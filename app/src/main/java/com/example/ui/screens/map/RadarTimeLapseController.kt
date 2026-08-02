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
        pause()
        preloadJob?.cancel()

        if (layer == MapWeatherLayer.NONE) {
            _state.update {
                it.copy(
                    activeLayer = MapWeatherLayer.NONE,
                    isLoading = false,
                    isBuffering = false,
                    isReadyToPlay = false,
                    bufferProgress = 0f
                )
            }
            return
        }

        Log.d("RadarCache", "Cache Invalidation Check | Layer initialized/changed to $layer | Preserving valid cached tiles in RAM (${TileRamCache.size()} tiles) and Disk (${DiskTileCache.fileCount()} files)")

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

        preloadJob = scope.launch {
            val frames = fetchOrGenerateFrames(layer)
            val initialIndex = frames.indexOfLast { !it.isForecast }.coerceAtLeast(0)

            _state.update {
                it.copy(
                    frames = frames,
                    currentFrameIndex = initialIndex,
                    isLoading = true,
                    isBuffering = true,
                    bufferProgress = 0.05f
                )
            }

            val initialFrame = frames.getOrNull(initialIndex)
            if (initialFrame != null && onFrameChanged != null) {
                onFrameChanged(initialFrame)
            }

            // Preload current active layer frames completely into RAM
            val orderedFrames = if (initialFrame != null) {
                listOf(initialFrame) + frames.filter { it.index != initialIndex }
            } else frames

            RadarPreloader.preloadFrames(
                layer = layer,
                frames = orderedFrames,
                centerLat = lat,
                centerLon = lon,
                mapZoom = zoom,
                onProgress = { loaded, total ->
                    val progress = if (total > 0) loaded.toFloat() / total else 1.0f
                    _state.update { s -> s.copy(bufferProgress = progress) }
                },
                onFrameReady = { fIdx ->
                    _state.update { s ->
                        val updatedFrames = s.frames.map { f ->
                            if (f.index == fIdx) f.copy(isReady = true) else f
                        }
                        s.copy(frames = updatedFrames)
                    }
                }
            )

            _state.update { s ->
                val allReadyFrames = s.frames.map { f -> f.copy(isReady = true) }
                s.copy(
                    frames = allReadyFrames,
                    isLoading = false,
                    isBuffering = false,
                    isReadyToPlay = true,
                    bufferProgress = 1.0f
                )
            }
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

        val frames = _state.value.frames
        if (frames.isEmpty()) return

        val oldKeys = RadarPreloader.getRequiredTileKeys(layer, frames, currentLat, currentLon, currentZoom)
        val newKeys = RadarPreloader.getRequiredTileKeys(layer, frames, lat, lon, zoom)

        if (oldKeys == newKeys) {
            currentLat = lat
            currentLon = lon
            currentZoom = zoom
            Log.d("RadarCache", "VIEWPORT_CHANGED_NOOP | Zoom: $zoom ($lat, $lon) | Tile key set unchanged.")
            return // Required tile set has not changed for current viewport
        }

        val missingKeys = newKeys.filter { !TileRamCache.contains(it) && !DiskTileCache.contains(it) }
        val cachedCount = newKeys.size - missingKeys.size
        val wasPlaying = _state.value.isPlaying

        currentLat = lat
        currentLon = lon
        currentZoom = zoom

        if (missingKeys.isEmpty()) {
            Log.d(
                "RadarCache",
                "VIEWPORT_CHANGED_ALL_CACHED | Zoom: $zoom ($lat, $lon) | All ${newKeys.size} required tiles are already cached in RAM/Disk! Zero network downloads needed."
            )
            preloadJob?.cancel()
            _state.update {
                it.copy(
                    isBuffering = false,
                    isReadyToPlay = true,
                    bufferProgress = 1.0f
                )
            }
            // Ensure disk-cached tiles are promoted to RAM Cache in background
            preloadJob = scope.launch {
                RadarPreloader.preloadFrames(
                    layer = layer,
                    frames = frames,
                    centerLat = lat,
                    centerLon = lon,
                    mapZoom = zoom,
                    onProgress = { _, _ -> },
                    onFrameReady = { fIdx ->
                        _state.update { s ->
                            val updatedFrames = s.frames.map { f ->
                                if (f.index == fIdx) f.copy(isReady = true) else f
                            }
                            s.copy(frames = updatedFrames)
                        }
                    }
                )
                if (wasPlaying && !_state.value.isPlaying) {
                    onFrameChanged?.let { cb -> play(cb) }
                }
            }
            return
        }

        pause()
        preloadJob?.cancel()

        val initialProgress = if (newKeys.isNotEmpty()) cachedCount.toFloat() / newKeys.size.toFloat() else 0f
        Log.d(
            "RadarCache",
            "VIEWPORT_CHANGED_PRELOAD_MISSING | Zoom: $zoom ($lat, $lon) | Total required: ${newKeys.size} | Cached: $cachedCount | Downloading missing: ${missingKeys.size}"
        )

        _state.update {
            it.copy(
                isBuffering = true,
                isReadyToPlay = false,
                bufferProgress = initialProgress
            )
        }

        preloadJob = scope.launch {
            val currentFrame = _state.value.currentFrame
            val orderedFrames = if (currentFrame != null) {
                listOf(currentFrame) + frames.filter { it.index != currentFrame.index }
            } else frames

            RadarPreloader.preloadFrames(
                layer = layer,
                frames = orderedFrames,
                centerLat = lat,
                centerLon = lon,
                mapZoom = zoom,
                onProgress = { loaded, total ->
                    val progress = if (total > 0) loaded.toFloat() / total else 1.0f
                    _state.update { s -> s.copy(bufferProgress = progress) }
                },
                onFrameReady = { fIdx ->
                    _state.update { s ->
                        val updatedFrames = s.frames.map { f ->
                            if (f.index == fIdx) f.copy(isReady = true) else f
                        }
                        s.copy(frames = updatedFrames)
                    }
                }
            )

            val allCached = RadarPreloader.areAllFramesCached(layer, frames, lat, lon, zoom)
            Log.d(
                "RadarCache",
                "BUFFER_READY | Zoom: $zoom | RequiredTiles: ${newKeys.size} | AllCached: $allCached | Completion: 100%"
            )

            _state.update { s ->
                val allReadyFrames = s.frames.map { f -> f.copy(isReady = true) }
                s.copy(
                    frames = allReadyFrames,
                    isLoading = false,
                    isBuffering = false,
                    isReadyToPlay = true,
                    bufferProgress = 1.0f
                )
            }

            currentFrame?.let { onFrameChanged?.invoke(it) }
            if (wasPlaying) {
                onFrameChanged?.let { cb -> play(cb) }
            }
        }
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
        if (!_state.value.isReadyToPlay || _state.value.isBuffering) {
            Log.w("TimelapsePipeline", "PLAY_ATTEMPT_BLOCKED | Preload running or buffer not ready for zoom $currentZoom")
            return
        }
        pause()
        _state.update { it.copy(isPlaying = true) }

        val layer = _state.value.activeLayer
        val frames = _state.value.frames
        val reqKeys = RadarPreloader.getRequiredTileKeys(layer, frames, currentLat, currentLon, currentZoom)

        Log.d(
            "TimelapsePipeline",
            "PLAYBACK_STARTED | Zoom: $currentZoom | TotalFrames: ${frames.size} | RequiredTiles: ${reqKeys.size}"
        )

        playbackJob = scope.launch {
            val layer = _state.value.activeLayer
            val frames = _state.value.frames

            if (frames.isEmpty() || layer == MapWeatherLayer.NONE) {
                _state.update { it.copy(isPlaying = false) }
                return@launch
            }

            // Smooth synchronized frame loop reading exclusively from preloaded RAM/Disk cache
            while (_state.value.isPlaying) {
                val currentFrames = _state.value.frames
                if (currentFrames.isEmpty()) break

                val currentIndex = _state.value.currentFrameIndex
                val nextIndex = (currentIndex + 1) % currentFrames.size
                val candidateFrame = currentFrames.getOrNull(nextIndex) ?: break

                val startTimeMs = System.currentTimeMillis()
                val stats = RadarPreloader.checkFrameTileStats(
                    layer = layer,
                    frame = candidateFrame,
                    centerLat = currentLat,
                    centerLon = currentLon,
                    mapZoom = currentZoom,
                    readyStartTimeMs = startTimeMs
                )

                Log.d(
                    "TimelapsePlayback",
                    "Frame: ${candidateFrame.index} | Required: ${stats.requiredTiles} | Loaded: ${stats.loadedTiles} | Missing: ${stats.missingTiles} | NetReq: ${stats.networkRequests} | RAM Hits: ${stats.ramHits} | Disk Hits: ${stats.diskHits} | ReadyTime: ${stats.readyTimeMs}ms"
                )

                if (stats.isReady) {
                    Log.d(
                        "TimelapsePlayback",
                        "Advancing to frame ${candidateFrame.index}: 100% visible tiles ready (${stats.loadedTiles}/${stats.requiredTiles})."
                    )
                    _state.update { it.copy(currentFrameIndex = nextIndex) }
                    onFrameChanged(candidateFrame)
                } else {
                    Log.w(
                        "TimelapsePlayback",
                        "Holding back frame ${candidateFrame.index}. Reason: Missing ${stats.missingTiles} visible tiles (Loaded ${stats.loadedTiles}/${stats.requiredTiles}). Keeping previous frame $currentIndex."
                    )

                    // Attempt single frame preload in background if missing
                    val fetchedStats = withContext(Dispatchers.IO) {
                        RadarPreloader.preloadSingleFrame(layer, candidateFrame, currentLat, currentLon, currentZoom)
                    }

                    if (fetchedStats.isReady) {
                        Log.d(
                            "TimelapsePlayback",
                            "Frame ${candidateFrame.index} became ready after fetch (${fetchedStats.loadedTiles}/${fetchedStats.requiredTiles}). Advancing."
                        )
                        _state.update { it.copy(currentFrameIndex = nextIndex) }
                        onFrameChanged(candidateFrame)
                    } else {
                        Log.w(
                            "TimelapsePlayback",
                            "Provider timeout / missing tile for frame ${candidateFrame.index} (Missing ${fetchedStats.missingTiles}). Skipping frame ${candidateFrame.index} to maintain smooth animation."
                        )
                        val skipIndex = (nextIndex + 1) % currentFrames.size
                        val skipFrame = currentFrames.getOrNull(skipIndex)
                        if (skipFrame != null) {
                            _state.update { it.copy(currentFrameIndex = skipIndex) }
                            onFrameChanged(skipFrame)
                        }
                    }
                }

                val baseDelay = 750L
                val speed = _state.value.playbackSpeed.coerceAtLeast(0.1f)
                val delayTime = (baseDelay / speed).toLong()
                delay(delayTime)
            }
        }
    }

    fun pause() {
        playbackJob?.cancel()
        playbackJob = null
        _state.update { it.copy(isPlaying = false, isBuffering = false) }
    }

    fun nextFrame(onFrameChanged: (TimeLapseFrame) -> Unit) {
        pause()
        val frames = _state.value.frames
        if (frames.isEmpty()) return
        val nextIndex = (_state.value.currentFrameIndex + 1) % frames.size
        _state.update { it.copy(currentFrameIndex = nextIndex) }
        frames.getOrNull(nextIndex)?.let { onFrameChanged(it) }
    }

    fun previousFrame(onFrameChanged: (TimeLapseFrame) -> Unit) {
        pause()
        val frames = _state.value.frames
        if (frames.isEmpty()) return
        val prevIndex = if (_state.value.currentFrameIndex - 1 < 0) frames.size - 1 else _state.value.currentFrameIndex - 1
        _state.update { it.copy(currentFrameIndex = prevIndex) }
        frames.getOrNull(prevIndex)?.let { onFrameChanged(it) }
    }

    fun seekToFrame(index: Int, onFrameChanged: (TimeLapseFrame) -> Unit) {
        val frames = _state.value.frames
        if (frames.isEmpty()) return
        val clampedIndex = index.coerceIn(0, frames.size - 1)
        _state.update { it.copy(currentFrameIndex = clampedIndex) }
        frames.getOrNull(clampedIndex)?.let { onFrameChanged(it) }
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
        Log.d("RadarCache", "Controller paused and destroyed. RAM Cache preserved (${TileRamCache.size()} tiles).")
    }
}
