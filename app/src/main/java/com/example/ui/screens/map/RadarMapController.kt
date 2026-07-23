package com.example.ui.screens.map

import android.content.Context
import android.util.Log
import com.example.data.models.RadarFrame
import com.example.data.models.RadarTimeline
import com.example.data.repository.RadarRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * Controller connecting MapScreen (Compose UI) to MapLibreWebViewProvider.
 * Handles radar playback animation, timeline indices, camera positioning,
 * weather layer switching, and offline state tracking.
 */
class RadarMapController(
    private val radarRepository: RadarRepository = RadarRepository()
) {
    private var provider: MapLibreWebViewProvider? = null

    // Timeline state
    private val _radarTimeline = MutableStateFlow<RadarTimeline?>(null)
    val radarTimeline: StateFlow<RadarTimeline?> = _radarTimeline.asStateFlow()

    private val _currentFrameIndex = MutableStateFlow(3) // Default to "Now" (index 3 out of 0..6)
    val currentFrameIndex: StateFlow<Int> = _currentFrameIndex.asStateFlow()

    private val _isPlaying = MutableStateFlow(true)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f) // 0.5x, 1.0x, 2.0x
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _radarOpacity = MutableStateFlow(0.85f)
    val radarOpacity: StateFlow<Float> = _radarOpacity.asStateFlow()

    private val _selectedLayer = MutableStateFlow(MapLayer.RAINFALL)
    val selectedLayer: StateFlow<MapLayer> = _selectedLayer.asStateFlow()

    private val _isOffline = MutableStateFlow(false)
    val isOffline: StateFlow<Boolean> = _isOffline.asStateFlow()

    private val _isMapLoaded = MutableStateFlow(false)
    val isMapLoaded: StateFlow<Boolean> = _isMapLoaded.asStateFlow()

    private var animationJob: Job? = null

    fun attachProvider(provider: MapLibreWebViewProvider) {
        this.provider = provider
        Log.d("RadarMapController", "MapLibreWebViewProvider attached to RadarMapController")
    }

    fun onMapReady(coroutineScope: CoroutineScope) {
        _isMapLoaded.value = true
        Log.d("RadarMapController", "Map loaded successfully. Fetching RainViewer timeline...")
        loadRadarTimeline(coroutineScope)
    }

    fun loadRadarTimeline(coroutineScope: CoroutineScope, forceRefresh: Boolean = false) {
        coroutineScope.launch(Dispatchers.IO) {
            val timeline = radarRepository.fetchRadarTimeline(forceRefresh = forceRefresh)
            if (timeline != null) {
                _isOffline.value = false
                _radarTimeline.value = timeline
                pushTimelineToWebView(timeline)
                startPlaybackLoop(coroutineScope)
            } else {
                _isOffline.value = true
                provider?.notifyRadarUnavailable("Radar timeline offline or unavailable")
            }
        }
    }

    private fun pushTimelineToWebView(timeline: RadarTimeline) {
        val framesJsonArray = JSONArray()
        timeline.compiled7TimelineFrames.forEach { frame ->
            val obj = JSONObject()
            obj.put("time", frame.time)
            obj.put("path", frame.path)
            obj.put("isForecast", frame.isForecast)
            obj.put("tileUrl", timeline.getTileUrl(frame))
            framesJsonArray.put(obj)
        }

        val payload = JSONObject()
        payload.put("host", timeline.host)
        payload.put("generatedAt", timeline.generatedAt)
        payload.put("frames", framesJsonArray)

        val jsonStr = payload.toString()
        Log.d("RadarMapController", "Pushing RainViewer timeline payload to WebView JS engine.")
        provider?.updateRadarTimeline(jsonStr)
    }

    fun selectTimelineIndex(index: Int) {
        val bounded = index.coerceIn(0, 6)
        _currentFrameIndex.value = bounded
        provider?.setTimelineIndex(bounded)
    }

    fun togglePlayback(coroutineScope: CoroutineScope) {
        val newPlayingState = !_isPlaying.value
        _isPlaying.value = newPlayingState
        if (newPlayingState) {
            startPlaybackLoop(coroutineScope)
        } else {
            animationJob?.cancel()
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        _playbackSpeed.value = speed
    }

    fun setRadarOpacity(opacity: Float) {
        val bounded = opacity.coerceIn(0.1f, 1.0f)
        _radarOpacity.value = bounded
        provider?.setRadarOpacity(bounded)
    }

    fun selectLayer(layer: MapLayer) {
        _selectedLayer.value = layer
        provider?.setWeatherLayer(layer)
    }

    fun setWeatherApiKey(key: String) {
        provider?.setWeatherApiKey(key)
    }

    fun setCenter(lat: Double, lon: Double, zoom: Float? = null) {
        provider?.setCenter(lat, lon, zoom)
    }

    fun zoomIn() {
        provider?.zoomIn()
    }

    fun zoomOut() {
        provider?.zoomOut()
    }

    fun resetNorth() {
        provider?.resetNorth()
    }

    fun startPlaybackLoop(coroutineScope: CoroutineScope) {
        animationJob?.cancel()
        if (!_isPlaying.value) return

        animationJob = coroutineScope.launch {
            while (_isPlaying.value) {
                val delayMs = (1200 / _playbackSpeed.value).toLong()
                delay(delayMs)
                val nextIndex = (_currentFrameIndex.value + 1) % 7
                _currentFrameIndex.value = nextIndex
                provider?.setTimelineIndex(nextIndex)
            }
        }
    }

    fun onNetworkStatusChanged(coroutineScope: CoroutineScope, isConnected: Boolean) {
        _isOffline.value = !isConnected
        if (isConnected && _radarTimeline.value == null) {
            loadRadarTimeline(coroutineScope, forceRefresh = true)
        }
    }

    fun release() {
        animationJob?.cancel()
        provider = null
    }
}
