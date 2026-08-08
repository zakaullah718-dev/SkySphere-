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

    @Volatile var mapView: MapView? = null

    private val frameStore = RadarFrameStore()
    private val playbackController = RadarPlaybackController(frameStore)

    private val _state = MutableStateFlow(TimeLapseState())
    val state: StateFlow<TimeLapseState> = _state.asStateFlow()

    private val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())

    private var currentLat: Double = 37.7749
    private var currentLon: Double = -122.4194
    private var currentZoom: Int = 5

    private var lastViewportLat: Double = -999.0
    private var lastViewportLon: Double = -999.0
    private var lastViewportZoom: Int = -1
    private var lastTileXs: List<Int> = emptyList()
    private var lastTileYs: List<Int> = emptyList()

    fun initializeForLayer(
        layer: MapWeatherLayer,
        lat: Double = currentLat,
        lon: Double = currentLon,
        zoom: Int = currentZoom,
        onFrameChanged: ((TimeLapseFrame) -> Unit)? = null
    ) {
        if (layer == MapWeatherLayer.NONE) {
            pause()
            frameStore.cancelHistoryPreparation()
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

        scope.launch {
            val frames = fetchOrGenerateFrames(layer)
            frameStore.setFrames(layer, frames)

            val initialIndex = frames.indexOfLast { !it.isForecast }.coerceAtLeast(0)
            val initialFrame = frames.getOrNull(initialIndex)

            if (initialFrame != null) {
                Log.d("SKYSPHERE_RADAR", "[TL] RADAR_FRAME_SELECTED timestamp=${initialFrame.timestamp}")
            }

            _state.update {
                it.copy(
                    frames = frames,
                    currentFrameIndex = initialIndex,
                    isLoading = false,
                    isBuffering = false,
                    isReadyToPlay = true,
                    bufferProgress = 1.0f
                )
            }

            if (initialFrame != null && onFrameChanged != null) {
                onFrameChanged(initialFrame)
                Log.d("SKYSPHERE_RADAR", "[TL] RADAR_FRAME_DISPLAYED timestamp=${initialFrame.timestamp}")
            }

            // Calculate current tile bounds for initial readiness check
            val providerMaxZoom = if (layer == MapWeatherLayer.RAIN_RADAR) {
                FutureWeatherLayerManager.RAIN_RADAR_PROVIDER_MAX_ZOOM
            } else {
                FutureWeatherLayerManager.OWM_PROVIDER_MAX_ZOOM
            }
            val pZoom = zoom.coerceIn(FutureWeatherLayerManager.PROVIDER_MIN_ZOOM, providerMaxZoom)
            val (tileXs, tileYs) = RadarPreloader.computeViewportTileBounds(mapView, lat, lon, pZoom)

            if (initialFrame != null) {
                frameStore.checkFrameReadiness(layer, initialFrame, pZoom, tileXs, tileYs)
            }

            // Gradual background history preparation
            frameStore.prepareHistoricalFramesAsync(layer, initialIndex, pZoom, tileXs, tileYs, radarRepository)
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

        val providerMaxZoom = if (layer == MapWeatherLayer.RAIN_RADAR) {
            FutureWeatherLayerManager.RAIN_RADAR_PROVIDER_MAX_ZOOM
        } else {
            FutureWeatherLayerManager.OWM_PROVIDER_MAX_ZOOM
        }
        val pZoom = zoom.coerceIn(FutureWeatherLayerManager.PROVIDER_MIN_ZOOM, providerMaxZoom)
        val (tileXs, tileYs) = RadarPreloader.computeViewportTileBounds(mapView, lat, lon, pZoom)

        if (pZoom == lastViewportZoom && tileXs == lastTileXs && tileYs == lastTileYs && abs(lat - lastViewportLat) < 0.001 && abs(lon - lastViewportLon) < 0.001) {
            return
        }

        val oldZoom = currentZoom
        lastViewportLat = lat
        lastViewportLon = lon
        lastViewportZoom = pZoom
        lastTileXs = tileXs
        lastTileYs = tileYs

        currentLat = lat
        currentLon = lon
        currentZoom = zoom

        Log.d(
            "SKYSPHERE_RADAR",
            "[TL] VIEWPORT_CHANGE oldZoom=$oldZoom newZoom=$zoom tileCount=${tileXs.size * tileYs.size}"
        )

        val frames = _state.value.frames
        if (frames.isNotEmpty()) {
            RadarPreloader.startBackgroundPreparation(
                layer = layer,
                frames = frames,
                currentFrameIndex = _state.value.currentFrameIndex,
                centerLat = lat,
                centerLon = lon,
                mapZoom = zoom,
                mapView = mapView
            )
        }

        val currentFrame = _state.value.currentFrame
        if (currentFrame != null) {
            frameStore.checkFrameReadiness(layer, currentFrame, pZoom, tileXs, tileYs)
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
                val interval = 900L
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
            val list = mutableListOf<TimeLapseFrame>()
            val startSec = nowSec - (3 * 3600L)
            val interval = 900L
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
        if (_state.value.isPlaying) {
            pause()
        } else {
            play(onFrameChanged)
        }
    }

    fun play(onFrameChanged: (TimeLapseFrame) -> Unit) {
        val currentState = _state.value
        val frames = currentState.frames
        if (frames.isEmpty()) return
        pause()
        _state.update { it.copy(isPlaying = true, isReadyToPlay = true, isBuffering = false) }

        playbackController.startPlayback(
            layer = currentState.activeLayer,
            getZoom = { currentZoom },
            getTileBounds = {
                val providerMaxZoom = if (_state.value.activeLayer == MapWeatherLayer.RAIN_RADAR) {
                    FutureWeatherLayerManager.RAIN_RADAR_PROVIDER_MAX_ZOOM
                } else {
                    FutureWeatherLayerManager.OWM_PROVIDER_MAX_ZOOM
                }
                val pZoom = currentZoom.coerceIn(FutureWeatherLayerManager.PROVIDER_MIN_ZOOM, providerMaxZoom)
                RadarPreloader.computeViewportTileBounds(mapView, currentLat, currentLon, pZoom)
            },
            delayMs = currentState.delayMs,
            playbackSpeed = currentState.playbackSpeed,
            getCurrentIndex = { _state.value.currentFrameIndex },
            onFrameChanged = onFrameChanged,
            onIndexUpdated = { nextIndex ->
                _state.update { it.copy(currentFrameIndex = nextIndex, isBuffering = false) }
            }
        )
    }

    fun pause() {
        playbackController.stopPlayback()
        _state.update { it.copy(isPlaying = false, isBuffering = false) }
    }

    fun nextFrame(onFrameChanged: (TimeLapseFrame) -> Unit) {
        pause()
        val frames = _state.value.frames
        if (frames.isEmpty()) return
        val oldIndex = _state.value.currentFrameIndex
        val nextIndex = (oldIndex + 1) % frames.size
        _state.update { it.copy(currentFrameIndex = nextIndex) }
        frames.getOrNull(nextIndex)?.let { frame ->
            Log.d("SKYSPHERE_RADAR", "[TL] RADAR_FRAME_SELECTED timestamp=${frame.timestamp}")
            onFrameChanged(frame)
            Log.d("SKYSPHERE_RADAR", "[TL] RADAR_FRAME_DISPLAYED timestamp=${frame.timestamp}")
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
            Log.d("SKYSPHERE_RADAR", "[TL] RADAR_FRAME_SELECTED timestamp=${frame.timestamp}")
            onFrameChanged(frame)
            Log.d("SKYSPHERE_RADAR", "[TL] RADAR_FRAME_DISPLAYED timestamp=${frame.timestamp}")
        }
    }

    fun seekToFrame(index: Int, onFrameChanged: (TimeLapseFrame) -> Unit) {
        val frames = _state.value.frames
        if (frames.isEmpty()) return
        val clampedIndex = index.coerceIn(0, frames.size - 1)
        _state.update { it.copy(currentFrameIndex = clampedIndex) }
        frames.getOrNull(clampedIndex)?.let { frame ->
            Log.d("SKYSPHERE_RADAR", "[TL] RADAR_FRAME_SELECTED timestamp=${frame.timestamp}")
            onFrameChanged(frame)
            Log.d("SKYSPHERE_RADAR", "[TL] RADAR_FRAME_DISPLAYED timestamp=${frame.timestamp}")
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
        frameStore.cancelHistoryPreparation()
    }
}
