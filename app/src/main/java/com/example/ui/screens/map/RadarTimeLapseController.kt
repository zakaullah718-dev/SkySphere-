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

    fun onLocationChanged(
        lat: Double,
        lon: Double,
        zoom: Int = currentZoom,
        onFrameChanged: ((TimeLapseFrame) -> Unit)? = null
    ) {
        val distSq = (lat - currentLat) * (lat - currentLat) + (lon - currentLon) * (lon - currentLon)
        if (distSq > 0.05) { // significant map shift
            currentLat = lat
            currentLon = lon
            currentZoom = zoom
            TileRamCache.clear()
            val layer = _state.value.activeLayer
            if (layer != MapWeatherLayer.NONE) {
                initializeForLayer(layer, lat, lon, zoom, onFrameChanged)
            }
        }
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
        if (!_state.value.isReadyToPlay) return
        pause()
        _state.update { it.copy(isPlaying = true) }

        playbackJob = scope.launch {
            val layer = _state.value.activeLayer
            val frames = _state.value.frames

            if (frames.isEmpty() || layer == MapWeatherLayer.NONE) {
                _state.update { it.copy(isPlaying = false) }
                return@launch
            }

            // Smooth synchronized frame loop reading exclusively from preloaded RAM cache
            while (_state.value.isPlaying) {
                val currentFrames = _state.value.frames
                if (currentFrames.isEmpty()) break

                val currentIndex = _state.value.currentFrameIndex
                val nextIndex = (currentIndex + 1) % currentFrames.size
                val nextFrame = currentFrames.getOrNull(nextIndex) ?: break

                _state.update { it.copy(currentFrameIndex = nextIndex) }
                onFrameChanged(nextFrame)

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
        TileRamCache.clear()
    }
}
