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
    private var lookaheadJob: Job? = null
    private var viewportJob: Job? = null

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

        preloadJob?.cancel()
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

        currentLat = lat
        currentLon = lon
        currentZoom = zoom

        viewportJob?.cancel()
        viewportJob = scope.launch(Dispatchers.IO) {
            delay(150)
            RadarPreloader.preloadFrames(
                layer = layer,
                frames = frames,
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
            runFrameLoop(onFrameChanged)
        }
    }

    private suspend fun isFrameCached(frame: TimeLapseFrame, zoom: Int): Boolean = withContext(Dispatchers.IO) {
        val layer = _state.value.activeLayer
        if (layer == MapWeatherLayer.NONE) return@withContext true
        val requiredKeys = RadarPreloader.getRequiredTileKeys(layer, listOf(frame), currentLat, currentLon, zoom)
        if (requiredKeys.isEmpty()) return@withContext true
        requiredKeys.all { key ->
            TileRamCache.contains(key) || DiskTileCache.contains(key)
        }
    }

    private suspend fun preloadFrameIfNeeded(frame: TimeLapseFrame, zoom: Int) {
        val layer = _state.value.activeLayer
        if (layer == MapWeatherLayer.NONE) return
        withContext(Dispatchers.IO) {
            RadarPreloader.preloadSingleFrame(layer, frame, currentLat, currentLon, zoom)
        }
    }

    private suspend fun runFrameLoop(onFrameChanged: (TimeLapseFrame) -> Unit) {
        while (_state.value.isPlaying) {
            val currentState = _state.value
            val frames = currentState.frames
            if (frames.isEmpty()) break
            
            val nextIndex = (currentState.currentFrameIndex + 1) % frames.size
            val nextFrame = frames[nextIndex]

            // Preload the frame AFTER next too (lookahead)
            val nextNextIndex = (nextIndex + 1) % frames.size
            val nextNextFrame = frames[nextNextIndex]

            // Start preloading future frames in background after canceling obsolete lookahead
            lookaheadJob?.cancel()
            lookaheadJob = scope.launch(Dispatchers.IO) {
                preloadFrameIfNeeded(nextFrame, currentZoom)
                preloadFrameIfNeeded(nextNextFrame, currentZoom)
            }
            
            val layer = currentState.activeLayer

            // Before calling onFrameChanged check if frame is cached to update buffering state
            val cached = isFrameCached(nextFrame, currentZoom)
            _state.update { it.copy(isBuffering = !cached) }

            // WAIT until tiles are preloaded for this frame
            val frameReady = waitForFramePreload(
                layer = layer,
                frame = nextFrame,
                zoom = currentZoom,
                timeoutMs = 5000L
            )
            
            if (!frameReady) {
                Log.w("RadarLoop", "Frame ${nextFrame.timestamp} not ready, skipping")
                continue
            }
            
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

    private suspend fun waitForFramePreload(
        layer: MapWeatherLayer,
        frame: TimeLapseFrame,
        zoom: Int,
        timeoutMs: Long = 5000L
    ): Boolean = withContext(Dispatchers.IO) {
        val requiredKeys = RadarPreloader.getRequiredTileKeys(layer, listOf(frame), currentLat, currentLon, zoom)
        if (requiredKeys.isEmpty()) return@withContext true

        var attempts = 0
        val maxAttempts = (timeoutMs / 200).toInt().coerceAtLeast(1)
        
        while (_state.value.isPlaying && attempts < maxAttempts) {
            val allCached = requiredKeys.all { key ->
                if (TileRamCache.contains(key)) {
                    true
                } else {
                    val diskBitmap = DiskTileCache.get(key)
                    if (diskBitmap != null) {
                        TileRamCache.put(key, diskBitmap)
                        true
                    } else {
                        false
                    }
                }
            }
            
            if (allCached) return@withContext true
            delay(200)
            attempts++
        }
        return@withContext false
    }

    fun pause() {
        playbackJob?.cancel()
        playbackJob = null
        lookaheadJob?.cancel()
        lookaheadJob = null
        viewportJob?.cancel()
        viewportJob = null
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
        RadarPreloader.stopAll("Controller destroyed")
        Log.d("RadarCache", "Controller paused and destroyed. RAM Cache preserved (${TileRamCache.size()} tiles).")
    }
}
