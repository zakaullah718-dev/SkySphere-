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

/**
 * Component D: RadarPlaybackController
 * Responsible ONLY for displaying already available radar frames sequentially.
 * Strictly read-only: does NOT download tiles, rebuild viewport plans, clear caches,
 * restart preloads, or make network calls.
 */
class RadarPlaybackController(
    private val frameStore: RadarFrameStore
) {
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var playbackJob: Job? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    fun startPlayback(
        layer: MapWeatherLayer,
        getZoom: () -> Int,
        getTileBounds: () -> Pair<List<Int>, List<Int>>,
        delayMs: Long,
        playbackSpeed: Float,
        getCurrentIndex: () -> Int,
        onFrameChanged: (TimeLapseFrame) -> Unit,
        onIndexUpdated: (Int) -> Unit
    ) {
        val frames = frameStore.getFrames()
        if (frames.isEmpty()) return

        stopPlayback()
        _isPlaying.value = true
        _isBuffering.value = false

        Log.d("SKYSPHERE_RADAR", "[TL] RADAR_PLAYBACK_STARTED count=${frames.size}")

        playbackJob = scope.launch {
            var bufferWaitTicks = 0
            while (_isPlaying.value) {
                val allFrames = frameStore.getFrames()
                if (allFrames.isEmpty()) {
                    delay(200L)
                    continue
                }

                val currentIndex = getCurrentIndex()
                val nextIndex = (currentIndex + 1) % allFrames.size
                val nextFrame = allFrames[nextIndex]

                val currentZoom = getZoom()
                val pZoom = currentZoom.coerceIn(
                    FutureWeatherLayerManager.PROVIDER_MIN_ZOOM,
                    if (layer == MapWeatherLayer.RAIN_RADAR) FutureWeatherLayerManager.RAIN_RADAR_PROVIDER_MAX_ZOOM else FutureWeatherLayerManager.OWM_PROVIDER_MAX_ZOOM
                )

                val (tileXs, tileYs) = getTileBounds()
                val readiness = frameStore.checkFrameReadiness(layer, nextFrame, pZoom, tileXs, tileYs)

                if (readiness.isReady || readiness.readyCount > 0 || bufferWaitTicks >= 2) {
                    bufferWaitTicks = 0
                    _isBuffering.value = false
                    onIndexUpdated(nextIndex)
                    withContext(Dispatchers.Main) {
                        onFrameChanged(nextFrame)
                    }
                    Log.d("SKYSPHERE_RADAR", "[TL] RADAR_PLAYBACK_FRAME frame=${nextFrame.timestamp} index=$nextIndex")

                    val speed = playbackSpeed.coerceAtLeast(0.1f)
                    val actualDelay = (delayMs / speed).toLong()
                    delay(actualDelay)
                } else {
                    bufferWaitTicks++
                    Log.d("SKYSPHERE_RADAR", "[TL] RADAR_PLAYBACK_WAITING frame=${nextFrame.timestamp} ready=${readiness.readyCount}/${readiness.requiredCount} waitTicks=$bufferWaitTicks")
                    _isBuffering.value = true
                    delay(200L)
                }
            }
        }
    }

    fun stopPlayback() {
        if (_isPlaying.value) {
            Log.d("SKYSPHERE_RADAR", "[TL] RADAR_PLAYBACK_STOPPED")
        }
        playbackJob?.cancel()
        playbackJob = null
        _isPlaying.value = false
        _isBuffering.value = false
    }
}
