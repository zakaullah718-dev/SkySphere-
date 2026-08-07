package com.example.ui.screens.map

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.osmdroid.views.MapView
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.tan

data class FrameTileStats(
    val frameIndex: Int,
    val requiredTiles: Int,
    val loadedTiles: Int,
    val missingTiles: Int,
    val networkRequests: Int,
    val ramHits: Int,
    val diskHits: Int,
    val readyTimeMs: Long,
    val isReady: Boolean
)

object RadarPreloader {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val pipelineMutex = Mutex()

    private var currentActiveLayer: MapWeatherLayer = MapWeatherLayer.NONE
    private var currentRequiredKeys: Set<String> = emptySet()
    private val activeTileJobs = ConcurrentHashMap<String, Job>()
    private var completedLogEmitted = false

    @Volatile
    private var maxSessionProgressPct = 0

    @Volatile
    private var adaptiveRequestDelayMs: Long = 0L

    @Volatile
    var isDownloading = false

    private val client = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .build()

    private val owmApiKey: String by lazy {
        try {
            val key = BuildConfig.WEATHER_API_KEY
            if (!key.isNullOrBlank() && key != "PLACEholder_WEATHER_API_KEY") key else "f0308472599cabe4521d65850bb6ba22"
        } catch (e: Exception) {
            "f0308472599cabe4521d65850bb6ba22"
        }
    }

    val emptyTransparentBitmap: Bitmap by lazy {
        Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
    }

    fun stopAll(reason: String = "Application destroyed or resetting") {
        scope.launch {
            pipelineMutex.withLock {
                val cancelCount = activeTileJobs.size
                activeTileJobs.values.forEach {
                    it.cancel()
                    RadarDiag.logCancelledDownload()
                }
                activeTileJobs.clear()
                currentActiveLayer = MapWeatherLayer.NONE
                currentRequiredKeys = emptySet()
                completedLogEmitted = false
                maxSessionProgressPct = 0
                PlaybackProtectedCache.clear(reason)
                if (cancelCount > 0) {
                    Log.d("RadarPreloader", "PRELOAD CANCEL | Reason: $reason (Cancelled $cancelCount obsolete tile tasks)")
                }
            }
        }
    }

    fun pausePreparation(reason: String = "Playback paused") {
        synchronized(this) {
            activePreparationJob?.cancel()
            activePreparationJob = null
        }
        scope.launch {
            pipelineMutex.withLock {
                val cancelCount = activeTileJobs.size
                activeTileJobs.values.forEach {
                    it.cancel()
                    RadarDiag.logCancelledDownload()
                }
                activeTileJobs.clear()
                if (cancelCount > 0) {
                    Log.d("RadarPreloader", "PRELOAD PAUSE | Reason: $reason (Stopped $cancelCount active download tasks)")
                }
            }
        }
    }

    fun buildTileKey(layer: MapWeatherLayer, timestamp: Long, zoom: Int, x: Int, y: Int): String {
        return if (layer == MapWeatherLayer.RAIN_RADAR) {
            "RainViewer_Radar_${timestamp}_${zoom}_${x}_${y}"
        } else {
            val layerEndpoint = when (layer) {
                MapWeatherLayer.CLOUDS -> "clouds_new"
                MapWeatherLayer.TEMPERATURE -> "temp_new"
                MapWeatherLayer.WIND -> "wind_new"
                MapWeatherLayer.HUMIDITY -> "humidity_new"
                MapWeatherLayer.PRESSURE -> "pressure_new"
                else -> "unknown"
            }
            "${layerEndpoint}_${zoom}_${x}_${y}"
        }
    }

    fun evictOldFrameCache(currentFrameIndex: Int, frames: List<TimeLapseFrame>) {
        if (frames.size <= 2) return
        val targetIndex = (currentFrameIndex - 2 + frames.size) % frames.size
        val oldFrame = frames[targetIndex]
        val timestamp = oldFrame.radarFrame?.time ?: oldFrame.timestamp
        TileRamCache.evictFrameByTimestamp(timestamp)
    }

    fun computeViewportTileBounds(
        mapView: MapView?,
        centerLat: Double,
        centerLon: Double,
        pZoom: Int
    ): Pair<List<Int>, List<Int>> {
        val numTilesDimension = 1 shl pZoom

        if (pZoom <= 3) {
            return Pair((0 until numTilesDimension).toList(), (0 until numTilesDimension).toList())
        }

        if (mapView != null) {
            try {
                val bbox = mapView.boundingBox
                if (bbox != null) {
                    val minX = ((bbox.lonWest + 180.0) / 360.0 * numTilesDimension).toInt() - 1
                    val maxX = ((bbox.lonEast + 180.0) / 360.0 * numTilesDimension).toInt() + 1

                    val radNorth = Math.toRadians(bbox.latNorth.coerceIn(-85.05112878, 85.05112878))
                    val minY = ((1.0 - ln(tan(radNorth) + 1.0 / cos(radNorth)) / Math.PI) / 2.0 * numTilesDimension).toInt() - 1

                    val radSouth = Math.toRadians(bbox.latSouth.coerceIn(-85.05112878, 85.05112878))
                    val maxY = ((1.0 - ln(tan(radSouth) + 1.0 / cos(radSouth)) / Math.PI) / 2.0 * numTilesDimension).toInt() + 1

                    val xs = (minX..maxX).map { (it % numTilesDimension + numTilesDimension) % numTilesDimension }.distinct()
                    val ys = (minY..maxY).map { it.coerceIn(0, numTilesDimension - 1) }.distinct()

                    if (xs.isNotEmpty() && ys.isNotEmpty()) {
                        return Pair(xs, ys)
                    }
                }
            } catch (e: Exception) {
                Log.w("RadarPreloader", "Failed to compute bounding box from MapView: ${e.localizedMessage}")
            }
        }

        val centerX = ((centerLon + 180.0) / 360.0 * numTilesDimension).toInt().coerceIn(0, numTilesDimension - 1)
        val clampedLat = centerLat.coerceIn(-85.05112878, 85.05112878)
        val rad = Math.toRadians(clampedLat)
        val centerY = ((1.0 - ln(tan(rad) + 1.0 / cos(rad)) / Math.PI) / 2.0 * numTilesDimension).toInt().coerceIn(0, numTilesDimension - 1)

        val radius = 3
        val xs = (centerX - radius..centerX + radius).map { (it + numTilesDimension) % numTilesDimension }.distinct()
        val ys = (centerY - radius..centerY + radius).map { it.coerceIn(0, numTilesDimension - 1) }.distinct()
        return Pair(xs, ys)
    }

    fun getRequiredTileKeys(
        layer: MapWeatherLayer,
        frames: List<TimeLapseFrame>,
        centerLat: Double,
        centerLon: Double,
        mapZoom: Int,
        mapView: MapView? = null
    ): Set<String> {
        if (layer == MapWeatherLayer.NONE || frames.isEmpty()) return emptySet()

        val providerMaxZoom = if (layer == MapWeatherLayer.RAIN_RADAR) {
            FutureWeatherLayerManager.RAIN_RADAR_PROVIDER_MAX_ZOOM
        } else {
            FutureWeatherLayerManager.OWM_PROVIDER_MAX_ZOOM
        }

        val pZoom = mapZoom.coerceIn(FutureWeatherLayerManager.PROVIDER_MIN_ZOOM, providerMaxZoom)
        val (tileXs, tileYs) = computeViewportTileBounds(mapView, centerLat, centerLon, pZoom)

        val keys = mutableSetOf<String>()
        for (frame in frames) {
            val timestamp = frame.radarFrame?.time ?: frame.timestamp
            val plan = RadarTilePlanner.planFrame(layer, timestamp, pZoom, tileXs, tileYs)
            keys.addAll(plan.criticalKeys)
            keys.addAll(plan.backgroundKeys)
        }
        return keys
    }

    fun isCenterTile(x: Int, y: Int, tileXs: List<Int>, tileYs: List<Int>): Boolean {
        if (tileXs.isEmpty() || tileYs.isEmpty()) return false
        val isXCenter = if (tileXs.size <= 2) true else {
            val minXIdx = 1
            val maxXIdx = tileXs.size - 2
            val xIdx = tileXs.indexOf(x)
            xIdx in minXIdx..maxXIdx
        }
        val isYCenter = if (tileYs.size <= 2) true else {
            val minYIdx = 1
            val maxYIdx = tileYs.size - 2
            val yIdx = tileYs.indexOf(y)
            yIdx in minYIdx..maxYIdx
        }
        return isXCenter && isYCenter
    }

    private val activeGenerationId = java.util.concurrent.atomic.AtomicLong(1L)

    fun nextGenerationId(): Long = activeGenerationId.incrementAndGet()
    fun getCurrentGenerationId(): Long = activeGenerationId.get()

    private data class PreloadSessionParams(
        val layer: MapWeatherLayer,
        val frameTimestamps: List<Long>,
        val pZoom: Int
    )

    @Volatile private var activeSessionParams: PreloadSessionParams? = null

    private val preloaderSemaphore = Semaphore(5)

    suspend fun preloadFrames(
        layer: MapWeatherLayer,
        frames: List<TimeLapseFrame>,
        centerLat: Double = 37.7749,
        centerLon: Double = -122.4194,
        mapZoom: Int = 5,
        mapView: MapView? = null,
        generationId: Long = getCurrentGenerationId(),
        onProgress: (loaded: Int, total: Int) -> Unit = { _, _ -> },
        onFrameReady: ((frameIndex: Int) -> Unit)? = null
    ) = withContext(Dispatchers.IO) {
        try {
            if (layer == MapWeatherLayer.NONE || frames.isEmpty()) {
                pipelineMutex.withLock {
                    if (currentRequiredKeys.isNotEmpty() || activeTileJobs.isNotEmpty()) {
                        val cancelCount = activeTileJobs.size
                        activeTileJobs.values.forEach {
                            it.cancel()
                            RadarDiag.logCancelledDownload()
                        }
                        activeTileJobs.clear()
                        Log.d("RadarPreloader", "PRELOAD CANCEL | Reason: Layer disabled or empty frames (Cancelled $cancelCount obsolete tile tasks)")
                    }
                    currentActiveLayer = MapWeatherLayer.NONE
                    currentRequiredKeys = emptySet()
                    maxSessionProgressPct = 0
                }
                return@withContext
            }

            if (generationId != getCurrentGenerationId()) {
                Log.d("RadarPreloader", "PRELOAD ABORT | Obsolete generation $generationId (current=${getCurrentGenerationId()})")
                return@withContext
            }

            val newRequiredKeys = getRequiredTileKeys(layer, frames, centerLat, centerLon, mapZoom, mapView)
            if (newRequiredKeys.isEmpty()) return@withContext

            val providerMaxZoom = if (layer == MapWeatherLayer.RAIN_RADAR) {
                FutureWeatherLayerManager.RAIN_RADAR_PROVIDER_MAX_ZOOM
            } else {
                FutureWeatherLayerManager.OWM_PROVIDER_MAX_ZOOM
            }
            val pZoom = mapZoom.coerceIn(FutureWeatherLayerManager.PROVIDER_MIN_ZOOM, providerMaxZoom)
            val (tileXs, tileYs) = computeViewportTileBounds(mapView, centerLat, centerLon, pZoom)
            val activeFrames = frames.take(2)
            val criticalWindowKeys = RadarTilePlanner.planPlaybackWindowCriticalKeys(layer, activeFrames, pZoom, tileXs, tileYs)
            PlaybackProtectedCache.setProtectedKeys(criticalWindowKeys)

            val sessionId = "REQ_SESS_${System.currentTimeMillis()}_gen${generationId}_${(100..999).random()}"
            RadarDiag.logSessionStart(sessionId, layer.name, mapZoom, newRequiredKeys.size)

            var missingKeysToStart = emptyList<String>()

            pipelineMutex.withLock {
                val oldRequiredKeys = currentRequiredKeys
                val addedKeys = newRequiredKeys - oldRequiredKeys
                val keptKeys = oldRequiredKeys intersect newRequiredKeys

                if (currentActiveLayer != layer) {
                    val cancelCount = activeTileJobs.size
                    activeTileJobs.values.forEach {
                        it.cancel()
                        RadarDiag.logCancelledDownload()
                    }
                    activeTileJobs.clear()
                    maxSessionProgressPct = 0
                    RadarDiag.logQueueRestart()
                    if (cancelCount > 0) {
                        RadarDiag.logSessionCancel(sessionId, "Active layer changed to $layer", cancelCount)
                        Log.d("RadarPreloader", "PRELOAD CANCEL | Session $sessionId | Reason: Active layer changed to $layer (Cancelled $cancelCount obsolete tile tasks)")
                    }
                    Log.d("RadarPreloader", "PRELOAD START | Session $sessionId | Layer: $layer | Zoom: $mapZoom | Required tiles: ${newRequiredKeys.size}")
                    currentActiveLayer = layer
                    completedLogEmitted = false
                } else {
                    if (addedKeys.isNotEmpty()) {
                        RadarDiag.logQueueMerge(addedKeys.size)
                        Log.d("SKYSPHERE_TIMELAPSE", "VIEWPORT_CHANGE_REUSE_CACHE reusedTiles=${keptKeys.size} downloadedTiles=${addedKeys.size}")
                        Log.d("RadarPreloader", "PRELOAD INCREMENTAL MERGE | Session $sessionId | Reusing ${keptKeys.size} valid tiles | Merging ${addedKeys.size} newly required tiles into queue")
                    } else if (keptKeys.isNotEmpty()) {
                        Log.d("SKYSPHERE_TIMELAPSE", "VIEWPORT_CHANGE_REUSE_CACHE reusedTiles=${keptKeys.size} downloadedTiles=0")
                        Log.d("RadarPreloader", "PRELOAD REUSE | Session $sessionId | All ${keptKeys.size} required tiles already active/cached")
                    }
                }

                currentRequiredKeys = newRequiredKeys

                // Warm RAM Cache from Disk Cache for all required keys
                newRequiredKeys.forEach { key ->
                    if (!TileRamCache.contains(key) && DiskTileCache.contains(key)) {
                        val diskBmp = DiskTileCache.get(key)
                        if (diskBmp != null) {
                            TileRamCache.put(key, diskBmp)
                        }
                    }
                }

                missingKeysToStart = newRequiredKeys.filter { key ->
                    val isCached = TileRamCache.contains(key) || DiskTileCache.contains(key)
                    if (isCached) {
                        RadarDiag.logReusedTile()
                        false
                    } else {
                        !activeTileJobs.containsKey(key)
                    }
                }
            }

            val numTilesDimension = 1 shl pZoom
            val centerX = ((centerLon + 180.0) / 360.0 * numTilesDimension).toInt().coerceIn(0, numTilesDimension - 1)
            val clampedLat = centerLat.coerceIn(-85.05112878, 85.05112878)
            val rad = Math.toRadians(clampedLat)
            val centerY = ((1.0 - ln(tan(rad) + 1.0 / cos(rad)) / Math.PI) / 2.0 * numTilesDimension).toInt().coerceIn(0, numTilesDimension - 1)

            val keyToInfoMap = mutableMapOf<String, Triple<TimeLapseFrame, Int, Int>>()
            for (frame in frames) {
                val timestamp = frame.radarFrame?.time ?: frame.timestamp
                for (x in tileXs) {
                    for (y in tileYs) {
                        val key = buildTileKey(layer, timestamp, pZoom, x, y)
                        keyToInfoMap[key] = Triple(frame, x, y)
                    }
                }
            }

            // Priority Scheduling:
            // Priority 1: Current playback frame - Center tiles
            // Priority 2: Current playback frame - Edge tiles
            // Priority 3: Next animation frame - Center tiles
            // Priority 4: Next animation frame - Edge tiles
            // Priority 5: Previous animation frame - Center/Edge tiles
            // Priority 6: Future animation frames - Center/Edge tiles
            missingKeysToStart = missingKeysToStart.sortedBy { key ->
                val info = keyToInfoMap[key]
                if (info != null) {
                    val (frame, x, y) = info
                    val frameRank = frames.indexOf(frame).let { if (it >= 0) it else 99 }
                    val isCenter = isCenterTile(x, y, tileXs, tileYs)
                    val spatialDist = kotlin.math.abs(x - centerX) + kotlin.math.abs(y - centerY)
                    when (frameRank) {
                        0 -> if (isCenter) 10 + spatialDist else 20 + spatialDist
                        1 -> if (isCenter) 30 + spatialDist else 40 + spatialDist
                        2 -> if (isCenter) 50 + spatialDist else 60 + spatialDist
                        else -> 100 + frameRank * 10 + (if (isCenter) 0 else 5) + spatialDist
                    }
                } else 999999
            }

            val snapshotKeys = newRequiredKeys
            val totalTasks = snapshotKeys.size
            val loadedCount = snapshotKeys.count { TileRamCache.contains(it) || DiskTileCache.contains(it) }

            // Diagnostic logging for frame tile requirements
            frames.forEach { f ->
                val fTs = f.radarFrame?.time ?: f.timestamp
                val fKeys = snapshotKeys.filter { it.contains("_${fTs}_") }
                Log.d("SKYSPHERE_TIMELAPSE", "FRAME_REQUIRED timestamp=$fTs frame_index=${f.index} required_tiles=${fKeys.size}")
                fKeys.forEach { k ->
                    Log.d("SKYSPHERE_TIMELAPSE", "FRAME_TILE_REQUIRED key=$k timestamp=$fTs")
                }
            }

            if (frames.isNotEmpty()) {
                val frame0Timestamp = frames[0].radarFrame?.time ?: frames[0].timestamp
                val frame0Keys = snapshotKeys.filter { it.contains("_${frame0Timestamp}_") }
                if (frame0Keys.isNotEmpty()) {
                    val frame0Loaded = frame0Keys.count { TileRamCache.contains(it) || DiskTileCache.contains(it) }
                    val visReadinessPct = (frame0Loaded.toFloat() / frame0Keys.size.toFloat()) * 100f
                    RadarDiag.recordVisibleTileReadiness(visReadinessPct)
                }
            }
            if (totalTasks > 0) {
                val bgReadinessPct = (loadedCount.toFloat() / totalTasks.toFloat()) * 100f
                RadarDiag.recordBackgroundTileReadiness(bgReadinessPct)
            }

            val initialRawPct = if (totalTasks > 0) (loadedCount.toFloat() / totalTasks.toFloat() * 100f).toInt() else 100
            val initialMonotonicPct = maxOf(maxSessionProgressPct, initialRawPct)
            maxSessionProgressPct = initialMonotonicPct

            RadarDiag.recordPreloadCompletion(initialMonotonicPct.toFloat())
            Log.d("TimelapsePipeline", "PRELOAD_PROGRESS | Zoom: $pZoom | Required: $totalTasks | Loaded: $loadedCount | Completion: $initialMonotonicPct%")
            onProgress((totalTasks * initialMonotonicPct / 100), totalTasks)

            if (loadedCount == totalTasks) {
                pipelineMutex.withLock {
                    if (!completedLogEmitted && currentActiveLayer == layer) {
                        completedLogEmitted = true
                        Log.d("RadarPreloader", "PRELOAD COMPLETE | Loaded $totalTasks/$totalTasks tiles (100%) for Zoom $pZoom")
                    }
                }
                frames.forEach {
                    val fTs = it.radarFrame?.time ?: it.timestamp
                    val fKeys = snapshotKeys.filter { k -> k.contains("_${fTs}_") }
                    val fLoaded = fKeys.count { k -> TileRamCache.contains(k) || DiskTileCache.contains(k) }
                    Log.d("SKYSPHERE_TIMELAPSE", "FRAME_READY timestamp=$fTs frame_index=${it.index} loaded=$fLoaded/${fKeys.size}")
                    Log.d("SKYSPHERE_TIMELAPSE", "FRAME_READY\ntimestamp=$fTs\nframe_index=${it.index}\nloaded=$fLoaded/${fKeys.size}")
                    onFrameReady?.invoke(it.index)
                }
                return@withContext
            }

            val launchedJobs = mutableListOf<Job>()
            missingKeysToStart.forEach { key ->
                val info = keyToInfoMap[key]
                if (info != null) {
                    val (frame, x, y) = info
                    val job = scope.launch(Dispatchers.IO) {
                        if (generationId != getCurrentGenerationId()) return@launch
                        preloaderSemaphore.withPermit {
                            if (generationId != getCurrentGenerationId()) return@withPermit
                            try {
                                preloadSingleTile(layer, frame, pZoom, x, y)
                            } catch (e: Exception) {
                                Log.w("RadarPreloader", "Failed tile download $key: ${e.localizedMessage}")
                            } finally {
                                activeTileJobs.remove(key)
                                if (generationId != getCurrentGenerationId()) return@withPermit

                                val currRequired = currentRequiredKeys
                                val curLoaded = currRequired.count { TileRamCache.contains(it) || DiskTileCache.contains(it) }
                                val curTotal = currRequired.size
                                if (curTotal > 0) {
                                    val rawPct = (curLoaded.toFloat() / curTotal.toFloat() * 100f).toInt()
                                    val monotonicPct = maxOf(maxSessionProgressPct, rawPct)
                                    maxSessionProgressPct = monotonicPct

                                    RadarDiag.recordPreloadCompletion(monotonicPct.toFloat())
                                    Log.d("TimelapsePipeline", "PRELOAD_PROGRESS | Zoom: $pZoom | Required: $curTotal | Loaded: $curLoaded | Completion: $monotonicPct%")
                                    onProgress((curTotal * monotonicPct / 100), curTotal)

                                    if (curLoaded == curTotal) {
                                        pipelineMutex.withLock {
                                            if (!completedLogEmitted && currentActiveLayer == layer) {
                                                completedLogEmitted = true
                                                Log.d("RadarPreloader", "PRELOAD COMPLETE | Loaded $curTotal/$curTotal tiles (100%) for Zoom $pZoom")
                                            }
                                        }
                                    }
                                }

                                val frameTimestamp = frame.radarFrame?.time ?: frame.timestamp
                                val frameKeys = currRequired.filter { it.contains("_${frameTimestamp}_") }
                                val frameLoaded = frameKeys.count { TileRamCache.contains(it) || DiskTileCache.contains(it) }
                                val frameTotal = frameKeys.size
                                Log.d("SKYSPHERE_TIMELAPSE", "FRAME_COMPLETION timestamp=$frameTimestamp frame_index=${frame.index} loaded_tiles=$frameLoaded/total_tiles=$frameTotal")

                                if (frameTotal > 0 && frameLoaded == frameTotal) {
                                    Log.d("SKYSPHERE_TIMELAPSE", "FRAME_READY timestamp=$frameTimestamp frame_index=${frame.index} loaded=$frameLoaded/$frameTotal")
                                    Log.d("SKYSPHERE_TIMELAPSE", "FRAME_READY\ntimestamp=$frameTimestamp\nframe_index=${frame.index}\nloaded=$frameLoaded/$frameTotal")
                                    RadarDiag.logFrameReadyEvent(frame.index, frameTimestamp, frameKeys.size)
                                    onFrameReady?.invoke(frame.index)
                                }
                            }
                        }
                    }
                    activeTileJobs[key] = job
                    launchedJobs.add(job)
                }
            }
            launchedJobs.joinAll()
        } finally {
            isDownloading = false
        }
    }

    @Volatile private var activePreparationJob: Job? = null

    fun startBackgroundPreparation(
        layer: MapWeatherLayer,
        frames: List<TimeLapseFrame>,
        currentFrameIndex: Int = 0,
        centerLat: Double = 37.7749,
        centerLon: Double = -122.4194,
        mapZoom: Int = 5,
        mapView: MapView? = null,
        forceRestart: Boolean = false
    ): Job {
        synchronized(this) {
            val timestamps = frames.map { it.radarFrame?.time ?: it.timestamp }
            val providerMaxZoom = if (layer == MapWeatherLayer.RAIN_RADAR) {
                FutureWeatherLayerManager.RAIN_RADAR_PROVIDER_MAX_ZOOM
            } else {
                FutureWeatherLayerManager.OWM_PROVIDER_MAX_ZOOM
            }
            val pZoom = mapZoom.coerceIn(FutureWeatherLayerManager.PROVIDER_MIN_ZOOM, providerMaxZoom)
            val newParams = PreloadSessionParams(layer, timestamps, pZoom)
            val currentJob = activePreparationJob

            if (!forceRestart && currentJob != null && currentJob.isActive && activeSessionParams == newParams) {
                return currentJob
            }

            val targetGenId = if (activeSessionParams != newParams || forceRestart) {
                activeSessionParams = newParams
                nextGenerationId()
            } else {
                getCurrentGenerationId()
            }

            val job = scope.launch(Dispatchers.IO) {
                if (layer == MapWeatherLayer.NONE || frames.isEmpty()) return@launch

                val prioritizedFrames = mutableListOf<TimeLapseFrame>()
                if (frames.isNotEmpty()) {
                    val safeIndex = currentFrameIndex.coerceIn(0, frames.size - 1)
                    // Priority 1: Current frame
                    prioritizedFrames.add(frames[safeIndex])
                    // Priority 2: Next frame
                    val nextIdx = (safeIndex + 1) % frames.size
                    if (!prioritizedFrames.contains(frames[nextIdx])) prioritizedFrames.add(frames[nextIdx])
                    // Priority 3: Previous frame
                    val prevIdx = (safeIndex - 1 + frames.size) % frames.size
                    if (!prioritizedFrames.contains(frames[prevIdx])) prioritizedFrames.add(frames[prevIdx])
                    // Priority 4: Remaining frames
                    frames.forEach { f ->
                        if (!prioritizedFrames.contains(f)) prioritizedFrames.add(f)
                    }
                }

                preloadFrames(
                    layer = layer,
                    frames = prioritizedFrames,
                    centerLat = centerLat,
                    centerLon = centerLon,
                    mapZoom = mapZoom,
                    mapView = mapView,
                    generationId = targetGenId
                )
            }
            activePreparationJob = job
            return job
        }
    }

    fun checkFrameTileStats(
        layer: MapWeatherLayer,
        frame: TimeLapseFrame,
        centerLat: Double,
        centerLon: Double,
        mapZoom: Int,
        readyStartTimeMs: Long,
        mapView: MapView? = null
    ): FrameTileStats {
        val requiredKeys = getRequiredTileKeys(layer, listOf(frame), centerLat, centerLon, mapZoom, mapView)
        if (requiredKeys.isEmpty()) {
            return FrameTileStats(
                frameIndex = frame.index,
                requiredTiles = 0,
                loadedTiles = 0,
                missingTiles = 0,
                networkRequests = 0,
                ramHits = 0,
                diskHits = 0,
                readyTimeMs = 0L,
                isReady = true
            )
        }

        var ramHits = 0
        var diskHits = 0
        var missing = 0

        for (key in requiredKeys) {
            if (TileRamCache.contains(key)) {
                ramHits++
            } else {
                val diskTile = DiskTileCache.get(key)
                if (diskTile != null) {
                    diskHits++
                    TileRamCache.put(key, diskTile)
                } else {
                    missing++
                }
            }
        }

        val loaded = ramHits + diskHits
        val isReady = (missing == 0)
        val readyTimeMs = if (isReady) (System.currentTimeMillis() - readyStartTimeMs).coerceAtLeast(0L) else -1L

        return FrameTileStats(
            frameIndex = frame.index,
            requiredTiles = requiredKeys.size,
            loadedTiles = loaded,
            missingTiles = missing,
            networkRequests = 0,
            ramHits = ramHits,
            diskHits = diskHits,
            readyTimeMs = readyTimeMs,
            isReady = isReady
        )
    }

    suspend fun preloadSingleFrame(
        layer: MapWeatherLayer,
        frame: TimeLapseFrame,
        centerLat: Double,
        centerLon: Double,
        mapZoom: Int,
        mapView: MapView? = null
    ): FrameTileStats = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        try {
            val providerMaxZoom = if (layer == MapWeatherLayer.RAIN_RADAR) {
                FutureWeatherLayerManager.RAIN_RADAR_PROVIDER_MAX_ZOOM
            } else {
                FutureWeatherLayerManager.OWM_PROVIDER_MAX_ZOOM
            }

            val pZoom = mapZoom.coerceIn(FutureWeatherLayerManager.PROVIDER_MIN_ZOOM, providerMaxZoom)
            val (tileXs, tileYs) = computeViewportTileBounds(mapView, centerLat, centerLon, pZoom)

            var netRequests = 0
            val jobs = mutableListOf<Job>()
            for (x in tileXs) {
                for (y in tileYs) {
                    try {
                        val timestamp = frame.radarFrame?.time ?: frame.timestamp
                        val key = buildTileKey(layer, timestamp, pZoom, x, y)

                        if (!TileRamCache.contains(key)) {
                            val diskTile = DiskTileCache.get(key)
                            if (diskTile != null) {
                                TileRamCache.put(key, diskTile)
                            } else {
                                netRequests++
                                jobs.add(launch {
                                    preloadSingleTile(layer, frame, pZoom, x, y)
                                })
                            }
                        }
                    } catch (e: Exception) {
                        Log.w("RadarPreloader", "Failed single tile fetch: ${e.localizedMessage}")
                    }
                }
            }
            jobs.joinAll()

            val stats = checkFrameTileStats(layer, frame, centerLat, centerLon, mapZoom, startTime)
            stats.copy(networkRequests = netRequests)
        } catch (e: Exception) {
            checkFrameTileStats(layer, frame, centerLat, centerLon, mapZoom, startTime)
        }
    }

    fun isFrameCached(
        layer: MapWeatherLayer,
        frame: TimeLapseFrame,
        centerLat: Double,
        centerLon: Double,
        mapZoom: Int
    ): Boolean {
        if (layer == MapWeatherLayer.NONE) return true
        val requiredKeys = getRequiredTileKeys(layer, listOf(frame), centerLat, centerLon, mapZoom)
        if (requiredKeys.isEmpty()) return true
        return requiredKeys.all { key ->
            TileRamCache.contains(key) || DiskTileCache.contains(key)
        }
    }

    fun areAllFramesCached(
        layer: MapWeatherLayer,
        frames: List<TimeLapseFrame>,
        centerLat: Double,
        centerLon: Double,
        mapZoom: Int
    ): Boolean {
        if (layer == MapWeatherLayer.NONE || frames.isEmpty()) return true
        val requiredKeys = getRequiredTileKeys(layer, frames, centerLat, centerLon, mapZoom)
        if (requiredKeys.isEmpty()) return true
        return requiredKeys.all { key ->
            TileRamCache.contains(key) || DiskTileCache.contains(key)
        }
    }

    private val networkSemaphore = Semaphore(3)
    private val inFlightDownloads = ConcurrentHashMap<String, Deferred<Bitmap?>>()

    @Volatile
    var global429CooldownUntilMs: Long = 0L

    fun is429CooldownActive(): Boolean {
        return System.currentTimeMillis() < global429CooldownUntilMs
    }

    fun trigger429Cooldown(cooldownMs: Long = 15000L) {
        global429CooldownUntilMs = System.currentTimeMillis() + cooldownMs
        adaptiveRequestDelayMs = (adaptiveRequestDelayMs + 100L).coerceAtMost(500L)
        RadarDiag.log429Response()
        Log.w("RadarPreloader", "GLOBAL 429 RATE LIMIT COOLDOWN TRIGGERED | Pausing network calls for ${cooldownMs}ms | Adaptive delay=${adaptiveRequestDelayMs}ms")
    }

    suspend fun preloadSingleTile(
        layer: MapWeatherLayer,
        frame: TimeLapseFrame,
        zoom: Int,
        x: Int,
        y: Int
    ) = withContext(Dispatchers.IO) {
        val timestamp = frame.radarFrame?.time ?: frame.timestamp
        val cacheKey = buildTileKey(layer, timestamp, zoom, x, y)

        if (TileRamCache.contains(cacheKey)) {
            Log.d("SKYSPHERE_TIMELAPSE", "FRAME=$timestamp ZOOM=$zoom X=$x Y=$y RAM=HIT DISK=SKIP ACTION=PRELOADED")
            Log.d("SKYSPHERE_TIMELAPSE", "FRAME_TILE_CACHE_HIT_RAM key=$cacheKey timestamp=$timestamp")
            Log.d("SKYSPHERE_TIMELAPSE", "FRAME_TILE_COMPLETED key=$cacheKey timestamp=$timestamp")
            return@withContext
        }

        val diskTile = DiskTileCache.get(cacheKey)
        if (diskTile != null && !diskTile.isRecycled) {
            TileRamCache.put(cacheKey, diskTile)
            Log.d("SKYSPHERE_TIMELAPSE", "FRAME=$timestamp ZOOM=$zoom X=$x Y=$y RAM=MISS DISK=HIT ACTION=LOAD_TO_RAM")
            Log.d("SKYSPHERE_TIMELAPSE", "FRAME_TILE_CACHE_HIT_DISK key=$cacheKey timestamp=$timestamp")
            Log.d("SKYSPHERE_TIMELAPSE", "FRAME_TILE_COMPLETED key=$cacheKey timestamp=$timestamp")
            return@withContext
        }

        Log.d("SKYSPHERE_TIMELAPSE", "FRAME=$timestamp ZOOM=$zoom X=$x Y=$y RAM=MISS DISK=MISS ACTION=NETWORK_DOWNLOAD")
        Log.d("SKYSPHERE_TIMELAPSE", "FRAME_TILE_NETWORK key=$cacheKey timestamp=$timestamp")

        val fetchedBmp = RadarTileFetcher.fetchOrDeduplicateTile(cacheKey) {
            if (layer == MapWeatherLayer.RAIN_RADAR) {
                val tsInSec = if (timestamp > 10_000_000_000L) {
                    timestamp / 1000L
                } else if (timestamp > 0L) {
                    timestamp
                } else {
                    (System.currentTimeMillis() - 600_000L) / 1000L
                }

                val tileUrl = frame.radarFrame?.buildTileUrl(zoom, x, y)
                    ?: "https://tilecache.rainviewer.com/v2/radar/$tsInSec/256/$zoom/$x/$y/2/1_1.png"

                downloadAndDecodeSync(tileUrl, cacheKey)
            } else {
                val layerEndpoint = when (layer) {
                    MapWeatherLayer.CLOUDS -> "clouds_new"
                    MapWeatherLayer.TEMPERATURE -> "temp_new"
                    MapWeatherLayer.WIND -> "wind_new"
                    MapWeatherLayer.HUMIDITY -> "humidity_new"
                    MapWeatherLayer.PRESSURE -> "pressure_new"
                    else -> return@fetchOrDeduplicateTile null
                }

                val tileUrl = "https://tile.openweathermap.org/map/$layerEndpoint/$zoom/$x/$y.png?appid=$owmApiKey"
                var bitmap = downloadAndDecodeSync(tileUrl, cacheKey)

                if (bitmap != null && layer == MapWeatherLayer.CLOUDS && bitmap != emptyTransparentBitmap) {
                    bitmap = applyDarkCloudStyle(bitmap)
                }
                bitmap
            }
        }
        if (fetchedBmp != null) {
            Log.d("SKYSPHERE_TIMELAPSE", "FRAME_TILE_COMPLETED key=$cacheKey timestamp=$timestamp")
        }
    }

    private fun downloadAndDecodeSync(url: String, key: String = ""): Bitmap? {
        while (is429CooldownActive()) {
            val remaining = global429CooldownUntilMs - System.currentTimeMillis()
            if (remaining > 0) {
                RadarDiag.logSkippedDownload()
                try { Thread.sleep(remaining.coerceAtMost(2000L)) } catch (_: Exception) {}
            }
        }

        if (adaptiveRequestDelayMs > 0) {
            try { Thread.sleep(adaptiveRequestDelayMs) } catch (_: Exception) {}
        }

        RadarDiag.logTileDownloadStart(key, url)
        val startTime = System.currentTimeMillis()
        val maxAttempts = 2

        for (attempt in 1..maxAttempts) {
            while (is429CooldownActive()) {
                val remaining = global429CooldownUntilMs - System.currentTimeMillis()
                if (remaining > 0) {
                    RadarDiag.logSkippedDownload()
                    try { Thread.sleep(remaining.coerceAtMost(2000L)) } catch (_: Exception) {}
                }
            }

            try {
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", "SkySphereApp/1.0")
                    .build()

                val result = client.newCall(req).execute().use { response ->
                    val code = response.code
                    val durationMs = System.currentTimeMillis() - startTime
                    RadarDiag.recordDownloadDuration(durationMs)

                    when {
                        response.isSuccessful -> {
                            if (adaptiveRequestDelayMs > 0) {
                                adaptiveRequestDelayMs = (adaptiveRequestDelayMs - 10L).coerceAtLeast(0L)
                            }
                            val bytes = response.body?.bytes()
                            if (bytes != null && bytes.isNotEmpty()) {
                                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                if (bmp != null) {
                                    RadarDiag.logTileDownloadCompletion(key, "SUCCESS", bytes.size.toLong(), code, url)
                                    Log.d("SKYSPHERE_TIMELAPSE", "TILE_DECODE_SUCCESS key=$key")
                                    bmp
                                } else {
                                    RadarDiag.logTileDownloadCompletion(key, "DECODE_FAILED", bytes.size.toLong(), code, url)
                                    Log.d("SKYSPHERE_TIMELAPSE", "TILE_DECODE_FAILED key=$key")
                                    null
                                }
                            } else {
                                RadarDiag.logTileDownloadCompletion(key, "EMPTY_BODY_TRANSPARENT", 0L, code, url)
                                Log.d("SKYSPHERE_TIMELAPSE", "TILE_DECODE_SUCCESS key=$key status=EMPTY_BODY_TRANSPARENT")
                                emptyTransparentBitmap
                            }
                        }
                        code == 404 || code == 204 -> {
                            RadarDiag.logTileDownloadCompletion(key, "HTTP_${code}_TRANSPARENT", 0L, code, url)
                            Log.d("SKYSPHERE_TIMELAPSE", "TILE_DECODE_SUCCESS key=$key status=HTTP_${code}_TRANSPARENT")
                            emptyTransparentBitmap
                        }
                        code == 410 -> {
                            RadarDiag.logTileDownloadCompletion(key, "EXPIRED_410", 0L, code, url)
                            Log.d("SKYSPHERE_TIMELAPSE", "TILE_DECODE_FAILED key=$key status=EXPIRED_410")
                            null
                        }
                        code == 429 -> {
                            val retryAfterHeader = response.header("Retry-After")
                            val retryAfterMs = retryAfterHeader?.toLongOrNull()?.times(1000L) ?: 15000L
                            trigger429Cooldown(retryAfterMs)

                            RadarDiag.logTileDownloadCompletion(key, "RATE_LIMIT_429", 0L, code, url)
                            Log.w("RadarPreloader", "HTTP 429 Rate limited for $url! Set global cooldown for ${retryAfterMs}ms.")
                            Log.d("SKYSPHERE_TIMELAPSE", "TILE_DECODE_FAILED key=$key status=RATE_LIMIT_429")
                            null
                        }
                        else -> {
                            RadarDiag.logTileDownloadCompletion(key, "HTTP_ERROR_${code}", 0L, code, url)
                            Log.d("SKYSPHERE_TIMELAPSE", "TILE_DECODE_FAILED key=$key status=HTTP_ERROR_${code}")
                            null
                        }
                    }
                }

                if (result != null) {
                    return result
                }

                if (attempt < maxAttempts) {
                    try { Thread.sleep(1000L * attempt) } catch (_: Exception) {}
                }
            } catch (e: Exception) {
                RadarDiag.logTileDownloadCompletion(key, "EXCEPTION_${e.javaClass.simpleName}", 0L, 0, url)
                Log.d("SKYSPHERE_TIMELAPSE", "TILE_DECODE_FAILED key=$key status=EXCEPTION_${e.javaClass.simpleName}")
                if (attempt < maxAttempts) {
                    try { Thread.sleep(1000L * attempt) } catch (_: Exception) {}
                }
            }
        }
        RadarDiag.logTileDownloadCompletion(key, "FAILED_ALL_ATTEMPTS", 0L, 0, url)
        Log.d("SKYSPHERE_TIMELAPSE", "TILE_DECODE_FAILED key=$key status=FAILED_ALL_ATTEMPTS")
        return null
    }

    private fun applyDarkCloudStyle(originalBitmap: Bitmap): Bitmap {
        val width = originalBitmap.width
        val height = originalBitmap.height
        val pixels = IntArray(width * height)
        originalBitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        for (i in pixels.indices) {
            val p = pixels[i]
            val a = Color.alpha(p)
            if (a > 0) {
                val alphaBoost = (a * 2.2f).coerceAtMost(255f).toInt()
                val density = a / 255f

                val red = (55 - density * 35).toInt().coerceIn(15, 255)
                val green = (65 - density * 35).toInt().coerceIn(20, 255)
                val blue = (85 - density * 40).toInt().coerceIn(30, 255)

                pixels[i] = Color.argb(alphaBoost, red, green, blue)
            }
        }

        val darkCloudBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        darkCloudBitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return darkCloudBitmap
    }
}
