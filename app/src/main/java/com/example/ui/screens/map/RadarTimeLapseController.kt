package com.example.ui.screens.map

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.views.MapView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class RadarTimeLapseController(
    private val radarRepository: FutureRadarRepository = FutureRadarRepository()
) {
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var playbackJob: Job? = null
    private var preloadJob: Job? = null
    private var lookaheadJob: Job? = null
    private var viewportJob: Job? = null

    @Volatile var mapView: MapView? = null

    private val _state = MutableStateFlow(TimeLapseState())
    val state: StateFlow<TimeLapseState> = _state.asStateFlow()

    private val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())

    private var currentLat: Double = 37.7749
    private var currentLon: Double = -122.4194
    private var currentZoom: Int = 5

    fun initializeForLayer(
        layer: MapWeatherLayer,
        lat: Double = currentLat,
        lon: Double = currentLon,
        zoom: Int = currentZoom,
        onFrameChanged: ((TimeLapseFrame) -> Unit)? = null
    ) {
        val oldStateStr = "Layer:${_state.value.activeLayer}"
        if (layer == MapWeatherLayer.NONE) {
            pause()
            preloadJob?.cancel()
            preloadJob = null
            RadarPreloader.stopAll("Layer set to NONE")
            _state.update {
                it.copy(
                    activeLayer = MapWeatherLayer.NONE,
                    isLoading = false,
                    isBuffering = false,
                    isReadyToPlay = false,
                    bufferProgress = 0f
                )
            }
            RadarDiag.logPlaybackStateTransition(oldStateStr, "Layer:NONE")
            return
        }

        currentLat = lat
        currentLon = lon
        currentZoom = zoom

        _state.update {
            it.copy(
                activeLayer = layer,
                isLoading = true,
                isBuffering = true,
                isReadyToPlay = false,
                bufferProgress = 0f
            )
        }
        RadarDiag.logPlaybackStateTransition(oldStateStr, "Layer:$layer,Loading=true")

        if (preloadJob?.isActive == true) {
            RadarDiag.detectJobCancellation("preloadJob", RadarDiag.currentJobSequenceId, RadarDiag.currentFrameIndex, RadarDiag.currentJobSequenceId + 1, -1)
        }
        preloadJob?.cancel()
        val seqId = ++RadarDiag.currentJobSequenceId
        preloadJob = scope.launch {
            val frames = fetchOrGenerateFrames(layer)
            frames.forEachIndexed { i, f ->
                RadarDiag.logRadarTimestamp(i, f.timestamp)
            }

            val initialIndex = frames.indexOfLast { !it.isForecast }.coerceAtLeast(0)
            val initialFrame = frames.getOrNull(initialIndex)

            if (initialFrame != null) {
                RadarDiag.currentFrameIndex = initialIndex
                RadarDiag.currentFrameTimestamp = initialFrame.timestamp
                RadarDiag.logFrameIndexRequested(initialIndex, initialFrame.timestamp, initialFrame.displayLabel)
            }

            _state.update {
                it.copy(
                    frames = frames,
                    currentFrameIndex = initialIndex,
                    isLoading = false,
                    isBuffering = false,
                    isReadyToPlay = true,
                    bufferProgress = 0.2f
                )
            }

            if (initialFrame != null && onFrameChanged != null) {
                onFrameChanged(initialFrame)
            }

            val nextIndex = (initialIndex + 1) % frames.size
            val nextFrame = frames.getOrNull(nextIndex)

            // Quick-fetch initial frame + next frame tiles first for instant streaming playback
            if (initialFrame != null) {
                RadarPreloader.preloadSingleFrame(layer, initialFrame, lat, lon, zoom, mapView)
            }
            if (nextFrame != null && nextFrame.index != initialIndex) {
                RadarPreloader.preloadSingleFrame(layer, nextFrame, lat, lon, zoom, mapView)
            }

            _state.update { s ->
                s.copy(
                    isLoading = false,
                    isBuffering = false,
                    isReadyToPlay = true,
                    bufferProgress = 0.5f
                )
            }

            // Start background streaming preparation for remaining frames without blocking playback readiness
            RadarPreloader.startBackgroundPreparation(
                layer = layer,
                frames = frames,
                currentFrameIndex = initialIndex,
                centerLat = lat,
                centerLon = lon,
                mapZoom = zoom,
                mapView = mapView
            )
        }
    }

    fun onViewportChanged(
        lat: Double,
        lon: Double,
        zoom: Int,
        onFrameChanged: ((TimeLapseFrame) -> Unit)? = null
    ) {
        val layer = _state.value.activeLayer
        if (layer == MapWeatherLayer.NONE) return

        currentLat = lat
        currentLon = lon
        currentZoom = zoom

        RadarWarmUpEngine.updateViewport(lat, lon, zoom, layer, mapView)
    }

    fun onLocationChanged(
        lat: Double,
        lon: Double,
        zoom: Int = currentZoom,
        onFrameChanged: ((TimeLapseFrame) -> Unit)? = null
    ) {
        onViewportChanged(lat, lon, zoom, onFrameChanged)
    }

    private suspend fun fetchOrGenerateFrames(layer: MapWeatherLayer): List<TimeLapseFrame> = withContext(Dispatchers.IO) {
        val nowSec = System.currentTimeMillis() / 1000L

        if (layer == MapWeatherLayer.RAIN_RADAR) {
            val radarFrames = try {
                radarRepository.getAllRadarPastFrames()
            } catch (e: Exception) {
                emptyList()
            }

            if (radarFrames.isNotEmpty()) {
                radarFrames.mapIndexed { idx, rf ->
                    createFrame(idx, rf.time, rf, nowSec)
                }
            } else {
                val list = mutableListOf<TimeLapseFrame>()
                val startSec = nowSec - (3 * 3600L)
                val interval = 900L // 15 mins
                var idx = 0
                for (ts in startSec..nowSec step interval) {
                    list.add(createFrame(idx++, ts, RadarFrame(time = ts), nowSec))
                }
                if (list.none { it.isNow }) {
                    list.add(createFrame(idx, nowSec, RadarFrame(time = nowSec), nowSec))
                }
                list
            }
        } else {
            // Generates 12 timeline frames for standard weather layers (Clouds, Temp, Wind, Humidity, Pressure)
            val list = mutableListOf<TimeLapseFrame>()
            val startSec = nowSec - (3 * 3600L) // Past 3 hours
            val interval = 900L // 15-minute steps
            var idx = 0
            for (ts in startSec..nowSec step interval) {
                list.add(createFrame(idx++, ts, RadarFrame(time = ts), nowSec))
            }
            if (list.none { it.isNow }) {
                list.add(createFrame(idx, nowSec, RadarFrame(time = nowSec), nowSec))
            }
            list
        }
    }

    private fun createFrame(index: Int, timestamp: Long, radarFrame: RadarFrame?, nowSec: Long): TimeLapseFrame {
        val diffSec = timestamp - nowSec
        val isNow = abs(diffSec) < 300L
        val isForecast = diffSec > 300L

        val displayLabel = when {
            isNow -> "NOW (LIVE)"
            diffSec < 0 -> {
                val totalMins = abs(diffSec) / 60
                val hrs = totalMins / 60
                val mins = totalMins % 60
                when {
                    totalMins < 60 -> if (totalMins <= 1) "1 minute ago" else "$totalMins minutes ago"
                    mins == 0L -> if (hrs == 1L) "1 hour ago" else "$hrs hours ago"
                    else -> "${hrs}h ${mins}m ago"
                }
            }
            else -> {
                val totalMins = diffSec / 60
                val hrs = totalMins / 60
                val mins = totalMins % 60
                when {
                    totalMins < 60 -> if (totalMins <= 1) "In 1 minute" else "In $totalMins minutes"
                    mins == 0L -> if (hrs == 1L) "In 1 hour" else "In $hrs hours"
                    else -> "In ${hrs}h ${mins}m"
                }
            }
        }

        val clockStr = timeFormat.format(Date(timestamp * 1000L))

        return TimeLapseFrame(
            index = index,
            timestamp = timestamp,
            displayLabel = displayLabel,
            relativeLabel = displayLabel,
            formattedClock = clockStr,
            radarFrame = radarFrame,
            isNow = isNow,
            isForecast = isForecast
        )
    }

    fun togglePlayPause(onFrameChanged: (TimeLapseFrame) -> Unit) {
        val currentlyPlaying = _state.value.isPlaying
        if (currentlyPlaying) {
            pause()
        } else {
            play(onFrameChanged)
        }
    }

    fun play(onFrameChanged: (TimeLapseFrame) -> Unit) {
        val frames = _state.value.frames
        if (frames.isEmpty()) return
        pause()
        _state.update { it.copy(isPlaying = true, isReadyToPlay = true, isBuffering = false) }
        RadarDiag.isPlaybackActive = true
        RadarDiag.logPlaybackStateTransition("Paused", "Playing (Read-Only)")

        Log.d(
            "TimelapsePipeline",
            "READ_ONLY_PLAYBACK_STARTED | Zoom: $currentZoom | TotalFrames: ${frames.size}"
        )

        RadarPreloader.startBackgroundPreparation(
            layer = _state.value.activeLayer,
            frames = frames,
            currentFrameIndex = _state.value.currentFrameIndex,
            centerLat = currentLat,
            centerLon = currentLon,
            mapZoom = currentZoom,
            mapView = mapView
        )

        playbackJob = scope.launch {
            runFrameLoop(onFrameChanged)
        }
    }

    private data class FrameReadinessInfo(
        val isReady: Boolean,
        val loadedCount: Int,
        val requiredCount: Int,
        val missingCount: Int,
        val missingCenterCount: Int,
        val inFlightCount: Int,
        val failedCount: Int
    )

    private fun extractXFromKey(key: String): Int {
        val parts = key.split("_")
        return parts.getOrNull(parts.size - 2)?.toIntOrNull() ?: 0
    }

    private fun extractYFromKey(key: String): Int {
        val parts = key.split("_")
        return parts.lastOrNull()?.toIntOrNull() ?: 0
    }

    private fun checkFrameReadiness(
        layer: MapWeatherLayer,
        frame: TimeLapseFrame,
        zoom: Int
    ): FrameReadinessInfo {
        val requiredKeys = RadarPreloader.getRequiredTileKeys(layer, listOf(frame), currentLat, currentLon, zoom, mapView)
        val requiredCount = requiredKeys.size
        if (requiredCount == 0) {
            return FrameReadinessInfo(
                isReady = true,
                loadedCount = 0,
                requiredCount = 0,
                missingCount = 0,
                missingCenterCount = 0,
                inFlightCount = 0,
                failedCount = 0
            )
        }

        val providerMaxZoom = if (layer == MapWeatherLayer.RAIN_RADAR) {
            FutureWeatherLayerManager.RAIN_RADAR_PROVIDER_MAX_ZOOM
        } else {
            FutureWeatherLayerManager.OWM_PROVIDER_MAX_ZOOM
        }
        val pZoom = zoom.coerceIn(FutureWeatherLayerManager.PROVIDER_MIN_ZOOM, providerMaxZoom)
        val (tileXs, tileYs) = RadarPreloader.computeViewportTileBounds(mapView, currentLat, currentLon, pZoom)

        var loadedCount = 0
        var inFlightCount = 0
        var missingCount = 0
        var missingCenterCount = 0

        requiredKeys.forEach { key ->
            if (TileRamCache.contains(key)) {
                loadedCount++
            } else if (DiskTileCache.contains(key)) {
                val diskTile = DiskTileCache.get(key)
                if (diskTile != null && !diskTile.isRecycled) {
                    TileRamCache.put(key, diskTile)
                    Log.d("SKYSPHERE_TIMELAPSE", "DISK=HIT RAM_PROMOTE=SUCCESS KEY=$key")
                    loadedCount++
                } else {
                    missingCount++
                    val x = extractXFromKey(key)
                    val y = extractYFromKey(key)
                    if (RadarPreloader.isCenterTile(x, y, tileXs, tileYs)) {
                        missingCenterCount++
                    }
                }
            } else {
                missingCount++
                val x = extractXFromKey(key)
                val y = extractYFromKey(key)
                if (RadarPreloader.isCenterTile(x, y, tileXs, tileYs)) {
                    missingCenterCount++
                }
                if (RadarTileFetcher.isInFlight(key)) {
                    inFlightCount++
                }
            }
        }

        val readinessPct = if (requiredCount > 0) loadedCount.toFloat() / requiredCount.toFloat() else 1.0f
        RadarDiag.recordFrameReadiness(readinessPct * 100f)

        val isFullReady = (requiredCount > 0 && loadedCount == requiredCount)
        val isPartialReady = (readinessPct >= 0.90f && missingCenterCount == 0)
        val isReady = isFullReady || isPartialReady

        return FrameReadinessInfo(
            isReady = isReady,
            loadedCount = loadedCount,
            requiredCount = requiredCount,
            missingCount = missingCount,
            missingCenterCount = missingCenterCount,
            inFlightCount = inFlightCount,
            failedCount = 0
        )
    }

    private suspend fun runFrameLoop(onFrameChanged: (TimeLapseFrame) -> Unit) {
        var isLoopFirstRun = true

        while (_state.value.isPlaying) {
            val currentState = _state.value
            val frames = currentState.frames
            if (frames.isEmpty()) break

            val currentIndex = currentState.currentFrameIndex
            val currentFrame = frames[currentIndex]
            val nextIndex = (currentIndex + 1) % frames.size
            val nextFrame = frames[nextIndex]

            // Protect RAM cache for playback window: previous + current + next + lookahead
            val prevIndex = (currentIndex - 1 + frames.size) % frames.size
            val lookaheadIndex = (nextIndex + 1) % frames.size
            val activeWindowFrames = listOfNotNull(
                frames.getOrNull(prevIndex),
                currentFrame,
                nextFrame,
                frames.getOrNull(lookaheadIndex)
            )
            val windowKeys = RadarPreloader.getRequiredTileKeys(
                currentState.activeLayer,
                activeWindowFrames,
                currentLat,
                currentLon,
                currentZoom,
                mapView
            )
            PlaybackProtectedCache.setProtectedKeys(windowKeys)

            if (isLoopFirstRun) {
                isLoopFirstRun = false
                val currReadiness = checkFrameReadiness(currentState.activeLayer, currentFrame, currentZoom)
                val nextReadiness = checkFrameReadiness(currentState.activeLayer, nextFrame, currentZoom)

                val currStatus = if (currReadiness.isReady) "READY" else "LOADING"
                val nextStatus = if (nextReadiness.isReady) "READY" else "LOADING"

                Log.d("SKYSPHERE_TIMELAPSE", "PLAYBACK_BUFFER_STATUS current=$currStatus next=$nextStatus future=QUEUED")
                Log.d("SKYSPHERE_TIMELAPSE", "PLAYBACK_START_READY frame=$currentIndex loaded=${currReadiness.loadedCount}/${currReadiness.requiredCount}")
            }

            val readiness = checkFrameReadiness(
                layer = currentState.activeLayer,
                frame = nextFrame,
                zoom = currentZoom
            )

            if (!readiness.isReady) {
                _state.update { it.copy(isBuffering = true) }

                val nextReadiness = checkFrameReadiness(currentState.activeLayer, nextFrame, currentZoom)
                val currReadiness = checkFrameReadiness(currentState.activeLayer, currentFrame, currentZoom)
                val currStatus = if (currReadiness.isReady) "READY" else "LOADING"
                val nextStatus = if (nextReadiness.isReady) "READY" else "LOADING"
                Log.d("SKYSPHERE_TIMELAPSE", "PLAYBACK_BUFFER_STATUS current=$currStatus next=$nextStatus future=QUEUED")

                Log.d(
                    "SKYSPHERE_TIMELAPSE",
                    "FRAME_WAIT timestamp=${nextFrame.timestamp} frameIndex=$nextIndex required=${readiness.requiredCount} loaded=${readiness.loadedCount} missing=${readiness.missingCount} inFlight=${readiness.inFlightCount} failed=${readiness.failedCount}"
                )

                // Ensure tile preparation is active for this frame
                RadarPreloader.startBackgroundPreparation(
                    layer = currentState.activeLayer,
                    frames = frames,
                    currentFrameIndex = nextIndex,
                    centerLat = currentLat,
                    centerLon = currentLon,
                    mapZoom = currentZoom,
                    mapView = mapView
                )

                delay(100L)
                continue
            }

            // Frame is READY (100% or partial ready >= 90% with 0 missing center tiles)
            if (readiness.missingCount > 0 && readiness.missingCenterCount == 0) {
                Log.d(
                    "SKYSPHERE_TIMELAPSE",
                    "FRAME_PARTIAL_READY timestamp=${nextFrame.timestamp} index=$nextIndex loaded=${readiness.loadedCount}/${readiness.requiredCount} visibleMissing=${readiness.missingCenterCount}"
                )
            }

            Log.d(
                "SKYSPHERE_TIMELAPSE",
                "FRAME_READY timestamp=${nextFrame.timestamp} frameIndex=$nextIndex loaded=${readiness.loadedCount}/${readiness.requiredCount}"
            )

            RadarDiag.logPlaybackFrameSwitch(currentIndex, nextIndex, nextFrame.timestamp)
            RadarDiag.currentFrameIndex = nextIndex
            RadarDiag.currentFrameTimestamp = nextFrame.timestamp

            _state.update {
                it.copy(
                    isBuffering = false,
                    currentFrameIndex = nextIndex
                )
            }
            onFrameChanged(nextFrame)

            val delayMs = currentState.delayMs
            delay(delayMs)
        }
    }

    fun pause() {
        val wasPlaying = _state.value.isPlaying
        playbackJob?.cancel()
        playbackJob = null
        lookaheadJob?.cancel()
        lookaheadJob = null
        viewportJob?.cancel()
        viewportJob = null
        _state.update { it.copy(isPlaying = false, isBuffering = false) }
        RadarDiag.isPlaybackActive = false
        RadarPreloader.pausePreparation("Playback paused")
        if (wasPlaying) {
            RadarDiag.logPlaybackStateTransition("Playing", "Paused")
        }
    }

    fun nextFrame(onFrameChanged: (TimeLapseFrame) -> Unit) {
        pause()
        val frames = _state.value.frames
        if (frames.isEmpty()) return
        val oldIndex = _state.value.currentFrameIndex
        val nextIndex = (oldIndex + 1) % frames.size
        _state.update { it.copy(currentFrameIndex = nextIndex) }
        frames.getOrNull(nextIndex)?.let { frame ->
            RadarDiag.logFrameIndexRequested(nextIndex, frame.timestamp, frame.displayLabel)
            RadarDiag.logPlaybackFrameSwitch(oldIndex, nextIndex, frame.timestamp)
            RadarDiag.currentFrameIndex = nextIndex
            RadarDiag.currentFrameTimestamp = frame.timestamp

            val requiredKeys = RadarPreloader.getRequiredTileKeys(_state.value.activeLayer, listOf(frame), currentLat, currentLon, currentZoom, mapView)
            val ramHits = requiredKeys.count { TileRamCache.contains(it) }
            val diskHits = requiredKeys.count { !TileRamCache.contains(it) && DiskTileCache.contains(it) }
            val loadedTiles = ramHits + diskHits
            val requiredCount = requiredKeys.size

            RadarDiag.printFrameSummary(
                frameIndex = nextIndex,
                timestamp = frame.timestamp,
                requiredTiles = requiredCount,
                loadedTiles = loadedTiles,
                failedTiles = if (loadedTiles < requiredCount) requiredCount - loadedTiles else 0,
                ramHits = ramHits,
                diskHits = diskHits,
                netMisses = requiredCount - loadedTiles,
                isReady = frame.isReady,
                isRendered = true,
                playbackAdvanced = false
            )

            onFrameChanged(frame)
        }
    }

    fun previousFrame(onFrameChanged: (TimeLapseFrame) -> Unit) {
        pause()
        val frames = _state.value.frames
        if (frames.isEmpty()) return
        val oldIndex = _state.value.currentFrameIndex
        val prevIndex = if (oldIndex - 1 < 0) frames.size - 1 else oldIndex - 1
        _state.update { it.copy(currentFrameIndex = prevIndex) }
        frames.getOrNull(prevIndex)?.let { frame ->
            RadarDiag.logFrameIndexRequested(prevIndex, frame.timestamp, frame.displayLabel)
            RadarDiag.logPlaybackFrameSwitch(oldIndex, prevIndex, frame.timestamp)
            RadarDiag.currentFrameIndex = prevIndex
            RadarDiag.currentFrameTimestamp = frame.timestamp

            val requiredKeys = RadarPreloader.getRequiredTileKeys(_state.value.activeLayer, listOf(frame), currentLat, currentLon, currentZoom, mapView)
            val ramHits = requiredKeys.count { TileRamCache.contains(it) }
            val diskHits = requiredKeys.count { !TileRamCache.contains(it) && DiskTileCache.contains(it) }
            val loadedTiles = ramHits + diskHits
            val requiredCount = requiredKeys.size

            RadarDiag.printFrameSummary(
                frameIndex = prevIndex,
                timestamp = frame.timestamp,
                requiredTiles = requiredCount,
                loadedTiles = loadedTiles,
                failedTiles = if (loadedTiles < requiredCount) requiredCount - loadedTiles else 0,
                ramHits = ramHits,
                diskHits = diskHits,
                netMisses = requiredCount - loadedTiles,
                isReady = frame.isReady,
                isRendered = true,
                playbackAdvanced = false
            )

            onFrameChanged(frame)
        }
    }

    fun seekToFrame(index: Int, onFrameChanged: (TimeLapseFrame) -> Unit) {
        val frames = _state.value.frames
        if (frames.isEmpty()) return
        val oldIndex = _state.value.currentFrameIndex
        val clampedIndex = index.coerceIn(0, frames.size - 1)
        _state.update { it.copy(currentFrameIndex = clampedIndex) }
        frames.getOrNull(clampedIndex)?.let { frame ->
            RadarDiag.logFrameIndexRequested(clampedIndex, frame.timestamp, frame.displayLabel)
            RadarDiag.logPlaybackFrameSwitch(oldIndex, clampedIndex, frame.timestamp)
            RadarDiag.currentFrameIndex = clampedIndex
            RadarDiag.currentFrameTimestamp = frame.timestamp

            val requiredKeys = RadarPreloader.getRequiredTileKeys(_state.value.activeLayer, listOf(frame), currentLat, currentLon, currentZoom, mapView)
            val ramHits = requiredKeys.count { TileRamCache.contains(it) }
            val diskHits = requiredKeys.count { !TileRamCache.contains(it) && DiskTileCache.contains(it) }
            val loadedTiles = ramHits + diskHits
            val requiredCount = requiredKeys.size

            RadarDiag.printFrameSummary(
                frameIndex = clampedIndex,
                timestamp = frame.timestamp,
                requiredTiles = requiredCount,
                loadedTiles = loadedTiles,
                failedTiles = if (loadedTiles < requiredCount) requiredCount - loadedTiles else 0,
                ramHits = ramHits,
                diskHits = diskHits,
                netMisses = requiredCount - loadedTiles,
                isReady = frame.isReady,
                isRendered = true,
                playbackAdvanced = false
            )

            onFrameChanged(frame)
        }
    }

    fun cycleSpeed() {
        val nextSpeed = when (_state.value.playbackSpeed) {
            0.25f -> 0.5f
            0.5f -> 1.0f
            1.0f -> 1.5f
            1.5f -> 2.0f
            else -> 0.25f
        }
        _state.update { it.copy(playbackSpeed = nextSpeed) }
    }

    fun destroy() {
        pause()
        preloadJob?.cancel()
        preloadJob = null
        RadarPreloader.stopAll("Controller destroyed")
        Log.d("RadarCache", "Controller paused and destroyed. RAM Cache preserved (${TileRamCache.size()} tiles).")
    }
}
