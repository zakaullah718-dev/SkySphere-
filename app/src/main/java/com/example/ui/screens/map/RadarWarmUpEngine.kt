package com.example.ui.screens.map

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.osmdroid.views.MapView
import java.util.concurrent.ConcurrentHashMap

data class WarmUpState(
    val isRunning: Boolean = false,
    val activeLayer: MapWeatherLayer = MapWeatherLayer.RAIN_RADAR,
    val warmUpCompletionPct: Float = 0f,
    val totalFrames: Int = 0,
    val readyFrames: Int = 0,
    val missingFrames: Int = 0,
    val fullyCachedFrames: Int = 0,
    val partiallyCachedFrames: Int = 0,
    val preparedTilesCount: Int = 0,
    val missingTilesCount: Int = 0,
    val queueSize: Int = 0,
    val isTimelineSyncing: Boolean = false,
    val lastSyncTimeMs: Long = 0L
)

object RadarWarmUpEngine {
    private const val TAG = "RadarWarmUpEngine"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val radarRepository = FutureRadarRepository()
    private var maintenanceJob: Job? = null

    private val _state = MutableStateFlow(WarmUpState())
    val state: StateFlow<WarmUpState> = _state.asStateFlow()

    @Volatile private var currentLat: Double = 37.7749
    @Volatile private var currentLon: Double = -122.4194
    @Volatile private var currentZoom: Int = 5
    @Volatile private var lastLat: Double = 37.7749
    @Volatile private var lastLon: Double = -122.4194
    @Volatile private var lastZoom: Int = 5
    @Volatile private var zoomStreak: Int = 0
    @Volatile private var currentLayer: MapWeatherLayer = MapWeatherLayer.RAIN_RADAR
    @Volatile private var currentFrames: List<TimeLapseFrame> = emptyList()

    private val preparedFramesMap = ConcurrentHashMap<Long, Boolean>()
    private val cachedFrameTimestamps = ConcurrentHashMap.newKeySet<Long>()

    @Volatile private var lastTileXs: List<Int> = emptyList()
    @Volatile private var lastTileYs: List<Int> = emptyList()
    @Volatile private var lastPZoom: Int = -1
    private var viewportDebounceJob: Job? = null

    fun start(context: Context) {
        DiskTileCache.init(context)
        RadarDiag.startPeriodicDiagnostics()
        if (_state.value.isRunning) return

        Log.d(TAG, "Starting Persistent Radar Warm-Up Engine...")
        _state.update { it.copy(isRunning = true) }

        maintenanceJob = scope.launch {
            while (true) {
                try {
                    syncTimelineAndPrepare()
                } catch (e: Exception) {
                    Log.w(TAG, "Timeline sync error: ${e.localizedMessage}")
                }
                delay(180_000L) // Continuous 3-minute synchronization interval
            }
        }
    }

    fun updateViewport(
        lat: Double,
        lon: Double,
        zoom: Int,
        layer: MapWeatherLayer = currentLayer,
        mapView: MapView? = null
    ) {
        val layerChanged = (currentLayer != layer)
        val providerMaxZoom = if (layer == MapWeatherLayer.RAIN_RADAR) {
            FutureWeatherLayerManager.RAIN_RADAR_PROVIDER_MAX_ZOOM
        } else {
            FutureWeatherLayerManager.OWM_PROVIDER_MAX_ZOOM
        }
        val pZoom = zoom.coerceIn(FutureWeatherLayerManager.PROVIDER_MIN_ZOOM, providerMaxZoom)
        val (newTileXs, newTileYs) = RadarPreloader.computeViewportTileBounds(mapView, lat, lon, pZoom)

        // Prevent duplicate recalculations when camera movement does not change the visible tile grid
        if (!layerChanged && pZoom == lastPZoom && newTileXs == lastTileXs && newTileYs == lastTileYs && currentFrames.isNotEmpty()) {
            return
        }

        lastTileXs = newTileXs
        lastTileYs = newTileYs
        lastPZoom = pZoom

        val deltaLat = lat - currentLat
        val deltaLon = lon - currentLon
        val deltaZoom = zoom - currentZoom

        if (deltaZoom != 0) {
            zoomStreak = if ((deltaZoom > 0 && zoomStreak > 0) || (deltaZoom < 0 && zoomStreak < 0)) zoomStreak + deltaZoom else deltaZoom
        }

        lastLat = currentLat
        lastLon = currentLon
        lastZoom = currentZoom

        currentLat = lat
        currentLon = lon
        currentZoom = zoom
        currentLayer = layer

        viewportDebounceJob?.cancel()
        viewportDebounceJob = scope.launch {
            if (!layerChanged && currentFrames.isNotEmpty()) {
                delay(150L) // Debounce viewport updates during active camera panning
            }
            if (layerChanged || currentFrames.isEmpty()) {
                syncTimelineAndPrepare(mapView)
            } else {
                prepareVisibleTilesDelta(mapView, deltaLat, deltaLon)
            }
        }
    }

    suspend fun syncTimelineAndPrepare(mapView: MapView? = null) {
        _state.update { it.copy(isTimelineSyncing = true) }
        try {
            if (currentLayer == MapWeatherLayer.NONE) {
                _state.update { it.copy(isTimelineSyncing = false) }
                return
            }

            val frames = radarRepository.fetchTimelineFrames(currentLayer)

            currentFrames = frames
            val total = frames.size

            // Retain valid cached timeline entries and remove expired ones (ring buffer eviction)
            val activeTimestamps = frames.map { it.radarFrame?.time ?: it.timestamp }.toSet()
            val expiredTimestamps = cachedFrameTimestamps - activeTimestamps
            expiredTimestamps.forEach { ts ->
                TileRamCache.evictFrameByTimestamp(ts)
                DiskTileCache.evictFrameByTimestamp(ts)
                preparedFramesMap.remove(ts)
            }
            cachedFrameTimestamps.clear()
            cachedFrameTimestamps.addAll(activeTimestamps)
            preparedFramesMap.keys.retainAll(activeTimestamps)

            prepareVisibleTilesDelta(mapView, 0.0, 0.0)

            _state.update {
                it.copy(
                    activeLayer = currentLayer,
                    totalFrames = total,
                    lastSyncTimeMs = System.currentTimeMillis(),
                    isTimelineSyncing = false
                )
            }
        } catch (e: Exception) {
            _state.update { it.copy(isTimelineSyncing = false) }
            Log.e(TAG, "Error in syncTimelineAndPrepare: ${e.localizedMessage}")
        }
    }

    private suspend fun prepareVisibleTilesDelta(
        mapView: MapView? = null,
        deltaLat: Double = 0.0,
        deltaLon: Double = 0.0
    ) {
        if (currentFrames.isEmpty() || currentLayer == MapWeatherLayer.NONE) return

        val initialIndex = currentFrames.indexOfLast { !it.isForecast }.coerceAtLeast(0)
        val orderedFrames = mutableListOf<TimeLapseFrame>()

        // Priority ordering: Current -> Next 1 -> Next 2 -> Next 3 -> Prev -> Remaining
        if (initialIndex in currentFrames.indices) {
            orderedFrames.add(currentFrames[initialIndex])
        }

        for (step in 1..3) {
            val idx = (initialIndex + step) % currentFrames.size
            if (idx in currentFrames.indices && !orderedFrames.contains(currentFrames[idx])) {
                orderedFrames.add(currentFrames[idx])
            }
        }

        val prevIdx = (initialIndex - 1 + currentFrames.size) % currentFrames.size
        if (prevIdx in currentFrames.indices && !orderedFrames.contains(currentFrames[prevIdx])) {
            orderedFrames.add(currentFrames[prevIdx])
        }

        currentFrames.forEach { f ->
            if (!orderedFrames.contains(f)) orderedFrames.add(f)
        }

        // 1. Primary visible viewport preparation
        RadarPreloader.startBackgroundPreparation(
            layer = currentLayer,
            frames = orderedFrames,
            currentFrameIndex = initialIndex,
            centerLat = currentLat,
            centerLon = currentLon,
            mapZoom = currentZoom,
            mapView = mapView
        )

        // 2. Predictive Pan Direction Prefetching
        if (Math.abs(deltaLat) > 0.001 || Math.abs(deltaLon) > 0.001) {
            val predLat = (currentLat + deltaLat * 1.5).coerceIn(-85.0, 85.0)
            val predLon = (currentLon + deltaLon * 1.5).coerceIn(-180.0, 180.0)
            RadarPreloader.startBackgroundPreparation(
                layer = currentLayer,
                frames = orderedFrames.take(3),
                currentFrameIndex = 0,
                centerLat = predLat,
                centerLon = predLon,
                mapZoom = currentZoom,
                mapView = null
            )
        }

        // 3. Predictive Zoom Trajectory Prefetching
        if (Math.abs(zoomStreak) >= 1) {
            val predZoom = if (zoomStreak > 0) (currentZoom + 1).coerceAtMost(18) else (currentZoom - 1).coerceAtLeast(1)
            if (predZoom != currentZoom) {
                RadarPreloader.startBackgroundPreparation(
                    layer = currentLayer,
                    frames = orderedFrames.take(2),
                    currentFrameIndex = 0,
                    centerLat = currentLat,
                    centerLon = currentLon,
                    mapZoom = predZoom,
                    mapView = null
                )
            }
        }

        updateDiagnostics()
    }

    fun updateDiagnostics() {
        if (currentFrames.isEmpty()) return

        var readyCount = 0
        var totalTilesRequired = 0
        var totalTilesLoaded = 0

        currentFrames.forEach { frame ->
            val requiredKeys = RadarPreloader.getRequiredTileKeys(
                currentLayer, listOf(frame), currentLat, currentLon, currentZoom, null
            )
            val reqCount = requiredKeys.size
            totalTilesRequired += reqCount
            val loadedCount = requiredKeys.count { key ->
                TileRamCache.contains(key) || DiskTileCache.contains(key)
            }
            totalTilesLoaded += loadedCount
            val isReady = reqCount == 0 || (loadedCount.toFloat() / reqCount.toFloat()) >= 0.5f
            val timestamp = frame.radarFrame?.time ?: frame.timestamp
            preparedFramesMap[timestamp] = isReady
            if (isReady) readyCount++
        }

        val totalFramesCount = currentFrames.size
        val completionPct = if (totalTilesRequired > 0) {
            (totalTilesLoaded.toFloat() / totalTilesRequired.toFloat() * 100f).coerceIn(0f, 100f)
        } else 100f

        _state.update {
            it.copy(
                warmUpCompletionPct = completionPct,
                totalFrames = totalFramesCount,
                readyFrames = readyCount,
                missingFrames = totalFramesCount - readyCount,
                preparedTilesCount = totalTilesLoaded,
                missingTilesCount = (totalTilesRequired - totalTilesLoaded).coerceAtLeast(0),
                queueSize = RadarDiag.downloadQueueSize.get()
            )
        }
    }
}
