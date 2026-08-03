package com.example.ui.screens.map

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
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

    private val emptyTransparentBitmap: Bitmap by lazy {
        Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
    }

    fun stopAll(reason: String = "Application destroyed or resetting") {
        scope.launch {
            pipelineMutex.withLock {
                val cancelCount = activeTileJobs.size
                activeTileJobs.values.forEach { it.cancel() }
                activeTileJobs.clear()
                currentActiveLayer = MapWeatherLayer.NONE
                currentRequiredKeys = emptySet()
                completedLogEmitted = false
                if (cancelCount > 0) {
                    Log.d("RadarPreloader", "PRELOAD CANCEL | Reason: $reason (Cancelled $cancelCount obsolete tile tasks)")
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

    fun getRequiredTileKeys(
        layer: MapWeatherLayer,
        frames: List<TimeLapseFrame>,
        centerLat: Double,
        centerLon: Double,
        mapZoom: Int
    ): Set<String> {
        if (layer == MapWeatherLayer.NONE || frames.isEmpty()) return emptySet()

        val providerMaxZoom = if (layer == MapWeatherLayer.RAIN_RADAR) {
            FutureWeatherLayerManager.RAIN_RADAR_PROVIDER_MAX_ZOOM
        } else {
            FutureWeatherLayerManager.OWM_PROVIDER_MAX_ZOOM
        }

        val pZoom = mapZoom.coerceIn(FutureWeatherLayerManager.PROVIDER_MIN_ZOOM, providerMaxZoom)
        val numTilesDimension = 1 shl pZoom

        val tileXs: List<Int>
        val tileYs: List<Int>
        if (pZoom <= 4) {
            tileXs = (0 until numTilesDimension).toList()
            tileYs = (0 until numTilesDimension).toList()
        } else {
            val centerX = ((centerLon + 180.0) / 360.0 * numTilesDimension).toInt().coerceIn(0, numTilesDimension - 1)
            val clampedLat = centerLat.coerceIn(-85.05112878, 85.05112878)
            val rad = Math.toRadians(clampedLat)
            val centerY = ((1.0 - ln(tan(rad) + 1.0 / cos(rad)) / Math.PI) / 2.0 * numTilesDimension).toInt().coerceIn(0, numTilesDimension - 1)

            val radius = 4
            tileXs = (centerX - radius..centerX + radius).map { (it + numTilesDimension) % numTilesDimension }.distinct()
            tileYs = (centerY - radius..centerY + radius).map { it.coerceIn(0, numTilesDimension - 1) }.distinct()
        }

        val keys = mutableSetOf<String>()
        for (frame in frames) {
            val timestamp = frame.radarFrame?.time ?: frame.timestamp
            for (x in tileXs) {
                for (y in tileYs) {
                    keys.add(buildTileKey(layer, timestamp, pZoom, x, y))
                }
            }
        }
        return keys
    }

    suspend fun preloadFrames(
        layer: MapWeatherLayer,
        frames: List<TimeLapseFrame>,
        centerLat: Double = 37.7749,
        centerLon: Double = -122.4194,
        mapZoom: Int = 5,
        onProgress: (loaded: Int, total: Int) -> Unit,
        onFrameReady: ((frameIndex: Int) -> Unit)? = null
    ) = withContext(Dispatchers.IO) {
        if (layer == MapWeatherLayer.NONE || frames.isEmpty()) {
            pipelineMutex.withLock {
                if (currentRequiredKeys.isNotEmpty() || activeTileJobs.isNotEmpty()) {
                    val cancelCount = activeTileJobs.size
                    activeTileJobs.values.forEach { it.cancel() }
                    activeTileJobs.clear()
                    Log.d("RadarPreloader", "PRELOAD CANCEL | Reason: Layer disabled or empty frames (Cancelled $cancelCount obsolete tile tasks)")
                }
                currentActiveLayer = MapWeatherLayer.NONE
                currentRequiredKeys = emptySet()
            }
            return@withContext
        }

        val newRequiredKeys = getRequiredTileKeys(layer, frames, centerLat, centerLon, mapZoom)
        if (newRequiredKeys.isEmpty()) return@withContext

        var missingKeysToStart = emptyList<String>()

        pipelineMutex.withLock {
            val oldRequiredKeys = currentRequiredKeys
            val obsoleteKeys = oldRequiredKeys - newRequiredKeys
            val addedKeys = newRequiredKeys - oldRequiredKeys
            val keptKeys = oldRequiredKeys intersect newRequiredKeys

            if (currentActiveLayer != layer) {
                val cancelCount = activeTileJobs.size
                activeTileJobs.values.forEach { it.cancel() }
                activeTileJobs.clear()
                if (cancelCount > 0) {
                    Log.d("RadarPreloader", "PRELOAD CANCEL | Reason: Active layer changed to $layer (Cancelled $cancelCount obsolete tile tasks)")
                }
                Log.d("RadarPreloader", "PRELOAD START | Layer: $layer | Zoom: $mapZoom | Required tiles: ${newRequiredKeys.size}")
                currentActiveLayer = layer
                completedLogEmitted = false
            } else {
                if (obsoleteKeys.isNotEmpty()) {
                    var cancelledCount = 0
                    obsoleteKeys.forEach { key ->
                        activeTileJobs.remove(key)?.let { job ->
                            job.cancel()
                            cancelledCount++
                        }
                    }
                    if (cancelledCount > 0) {
                        Log.d("RadarPreloader", "PRELOAD CANCEL | Reason: Viewport/Zoom change (Cancelled $cancelledCount obsolete tile tasks)")
                    }
                }

                if (addedKeys.isNotEmpty() || keptKeys.isNotEmpty()) {
                    Log.d("RadarPreloader", "PRELOAD RESUME | Reason: Viewport update to Zoom $mapZoom | Reusing ${keptKeys.size} valid tiles | Adding ${addedKeys.size} new tiles")
                }
            }

            currentRequiredKeys = newRequiredKeys

            missingKeysToStart = newRequiredKeys.filter { key ->
                !TileRamCache.contains(key) && !DiskTileCache.contains(key) && !activeTileJobs.containsKey(key)
            }
        }

        val providerMaxZoom = if (layer == MapWeatherLayer.RAIN_RADAR) {
            FutureWeatherLayerManager.RAIN_RADAR_PROVIDER_MAX_ZOOM
        } else {
            FutureWeatherLayerManager.OWM_PROVIDER_MAX_ZOOM
        }

        val pZoom = mapZoom.coerceIn(FutureWeatherLayerManager.PROVIDER_MIN_ZOOM, providerMaxZoom)
        val numTilesDimension = 1 shl pZoom

        val tileXs: List<Int>
        val tileYs: List<Int>
        if (pZoom <= 4) {
            tileXs = (0 until numTilesDimension).toList()
            tileYs = (0 until numTilesDimension).toList()
        } else {
            val centerX = ((centerLon + 180.0) / 360.0 * numTilesDimension).toInt().coerceIn(0, numTilesDimension - 1)
            val clampedLat = centerLat.coerceIn(-85.05112878, 85.05112878)
            val rad = Math.toRadians(clampedLat)
            val centerY = ((1.0 - ln(tan(rad) + 1.0 / cos(rad)) / Math.PI) / 2.0 * numTilesDimension).toInt().coerceIn(0, numTilesDimension - 1)

            val radius = 4
            tileXs = (centerX - radius..centerX + radius).map { (it + numTilesDimension) % numTilesDimension }.distinct()
            tileYs = (centerY - radius..centerY + radius).map { it.coerceIn(0, numTilesDimension - 1) }.distinct()
        }

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

        val snapshotKeys = newRequiredKeys
        val totalTasks = snapshotKeys.size
        val loadedCount = snapshotKeys.count { TileRamCache.contains(it) || DiskTileCache.contains(it) }

        onProgress(loadedCount, totalTasks)

        if (loadedCount == totalTasks) {
            pipelineMutex.withLock {
                if (!completedLogEmitted && currentActiveLayer == layer) {
                    completedLogEmitted = true
                    Log.d("RadarPreloader", "PRELOAD COMPLETE | Loaded $totalTasks/$totalTasks tiles (100%) for Zoom $pZoom")
                }
            }
            frames.forEach { onFrameReady?.invoke(it.index) }
            return@withContext
        }

        val launchedJobs = mutableListOf<Job>()
        missingKeysToStart.forEach { key ->
            val info = keyToInfoMap[key]
            if (info != null) {
                val (frame, x, y) = info
                val job = scope.launch(Dispatchers.IO) {
                    try {
                        preloadSingleTile(layer, frame, pZoom, x, y)
                    } catch (e: Exception) {
                        Log.w("RadarPreloader", "Failed tile download $key: ${e.localizedMessage}")
                    } finally {
                        activeTileJobs.remove(key)
                        val currRequired = currentRequiredKeys
                        val curLoaded = currRequired.count { TileRamCache.contains(it) || DiskTileCache.contains(it) }
                        val curTotal = currRequired.size
                        if (curTotal > 0) {
                            val pct = (curLoaded.toFloat() / curTotal.toFloat() * 100f).toInt()
                            Log.d("TimelapsePipeline", "PRELOAD_PROGRESS | Zoom: $pZoom | Required: $curTotal | Loaded: $curLoaded | Completion: $pct%")
                            onProgress(curLoaded, curTotal)

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
                        if (frameKeys.isNotEmpty() && frameKeys.all { TileRamCache.contains(it) || DiskTileCache.contains(it) }) {
                            onFrameReady?.invoke(frame.index)
                        }
                    }
                }
                activeTileJobs[key] = job
                launchedJobs.add(job)
            }
        }
        launchedJobs.joinAll()
    }

    fun checkFrameTileStats(
        layer: MapWeatherLayer,
        frame: TimeLapseFrame,
        centerLat: Double,
        centerLon: Double,
        mapZoom: Int,
        readyStartTimeMs: Long
    ): FrameTileStats {
        val requiredKeys = getRequiredTileKeys(layer, listOf(frame), centerLat, centerLon, mapZoom)
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
        mapZoom: Int
    ): FrameTileStats = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val providerMaxZoom = if (layer == MapWeatherLayer.RAIN_RADAR) {
            FutureWeatherLayerManager.RAIN_RADAR_PROVIDER_MAX_ZOOM
        } else {
            FutureWeatherLayerManager.OWM_PROVIDER_MAX_ZOOM
        }

        val pZoom = mapZoom.coerceIn(FutureWeatherLayerManager.PROVIDER_MIN_ZOOM, providerMaxZoom)
        val numTilesDimension = 1 shl pZoom

        val tileXs: List<Int>
        val tileYs: List<Int>
        if (pZoom <= 4) {
            tileXs = (0 until numTilesDimension).toList()
            tileYs = (0 until numTilesDimension).toList()
        } else {
            val centerX = ((centerLon + 180.0) / 360.0 * numTilesDimension).toInt().coerceIn(0, numTilesDimension - 1)
            val clampedLat = centerLat.coerceIn(-85.05112878, 85.05112878)
            val rad = Math.toRadians(clampedLat)
            val centerY = ((1.0 - ln(tan(rad) + 1.0 / cos(rad)) / Math.PI) / 2.0 * numTilesDimension).toInt().coerceIn(0, numTilesDimension - 1)

            val radius = 4
            tileXs = (centerX - radius..centerX + radius).map { (it + numTilesDimension) % numTilesDimension }.distinct()
            tileYs = (centerY - radius..centerY + radius).map { it.coerceIn(0, numTilesDimension - 1) }.distinct()
        }

        var netRequests = 0
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
                            preloadSingleTile(layer, frame, pZoom, x, y)
                        }
                    }
                } catch (e: Exception) {
                    Log.w("RadarPreloader", "Failed single tile fetch: ${e.localizedMessage}")
                }
            }
        }

        val stats = checkFrameTileStats(layer, frame, centerLat, centerLon, mapZoom, startTime)
        stats.copy(networkRequests = netRequests)
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

    private fun preloadSingleTile(
        layer: MapWeatherLayer,
        frame: TimeLapseFrame,
        zoom: Int,
        x: Int,
        y: Int
    ) {
        val timestamp = frame.radarFrame?.time ?: frame.timestamp
        val cacheKey = buildTileKey(layer, timestamp, zoom, x, y)

        if (layer == MapWeatherLayer.RAIN_RADAR) {
            if (TileRamCache.contains(cacheKey)) {
                Log.d("RadarCache", "RAM Cache Hit | Key: $cacheKey")
                return
            }

            val diskTile = DiskTileCache.get(cacheKey)
            if (diskTile != null) {
                RadarApiTracker.logDiskCacheHit(cacheKey)
                TileRamCache.put(cacheKey, diskTile)
                return
            }

            val tsInSec = if (timestamp > 10_000_000_000L) {
                timestamp / 1000L
            } else if (timestamp > 0L) {
                timestamp
            } else {
                (System.currentTimeMillis() - 600_000L) / 1000L
            }

            val tileUrl = frame.radarFrame?.buildTileUrl(zoom, x, y)
                ?: "https://tilecache.rainviewer.com/v2/radar/$tsInSec/256/$zoom/$x/$y.png"

            Log.d("RadarDebug", "ZOOM=$zoom | X=$x | Y=$y | TIMESTAMP=$tsInSec")
            Log.d("RadarDebug", "URL: $tileUrl")
            Log.d("RadarCache", "Cache Miss | Key: $cacheKey -> Downloading from network... URL: $tileUrl")
            val downloadedBitmap = downloadAndDecode(tileUrl) ?: emptyTransparentBitmap
            TileRamCache.put(cacheKey, downloadedBitmap)
            DiskTileCache.put(cacheKey, downloadedBitmap)

        } else {
            val layerEndpoint = when (layer) {
                MapWeatherLayer.CLOUDS -> "clouds_new"
                MapWeatherLayer.TEMPERATURE -> "temp_new"
                MapWeatherLayer.WIND -> "wind_new"
                MapWeatherLayer.HUMIDITY -> "humidity_new"
                MapWeatherLayer.PRESSURE -> "pressure_new"
                else -> return
            }

            val cacheKey = "${layerEndpoint}_${zoom}_${x}_${y}"
            if (TileRamCache.contains(cacheKey)) return

            val diskTile = DiskTileCache.get(cacheKey)
            if (diskTile != null) {
                RadarApiTracker.logDiskCacheHit(cacheKey)
                TileRamCache.put(cacheKey, diskTile)
                return
            }

            val tileUrl = "https://tile.openweathermap.org/map/$layerEndpoint/$zoom/$x/$y.png?appid=$owmApiKey"
            var bitmap = downloadAndDecode(tileUrl) ?: emptyTransparentBitmap

            if (layer == MapWeatherLayer.CLOUDS && bitmap != emptyTransparentBitmap) {
                bitmap = applyDarkCloudStyle(bitmap)
            }
            TileRamCache.put(cacheKey, bitmap)
            DiskTileCache.put(cacheKey, bitmap)
        }
    }

    private fun downloadAndDecode(url: String): Bitmap? {
        return try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "SkySphereApp/1.0")
                .build()
            client.newCall(req).execute().use { response ->
                if (response.isSuccessful) {
                    val bytes = response.body?.bytes()
                    if (bytes != null && bytes.isNotEmpty()) {
                        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        if (bmp != null) {
                            Log.d("RadarPreloader", "Successfully downloaded tile (${bytes.size} bytes): $url")
                            bmp
                        } else {
                            Log.w("RadarPreloader", "Failed to decode PNG bytes (${bytes.size} bytes): $url -> using transparent fallback")
                            emptyTransparentBitmap
                        }
                    } else {
                        Log.d("RadarPreloader", "Empty response body (0 bytes / 204 No Content): $url -> using transparent tile")
                        emptyTransparentBitmap
                    }
                } else if (response.code == 404 || response.code == 204 || response.code == 410) {
                    Log.d("RadarPreloader", "HTTP ${response.code} (No radar precipitation on tile): $url -> using transparent tile")
                    emptyTransparentBitmap
                } else {
                    Log.w("RadarPreloader", "HTTP ${response.code} ${response.message} for tile URL: $url -> using transparent fallback")
                    emptyTransparentBitmap
                }
            }
        } catch (e: Exception) {
            Log.e("RadarPreloader", "Exception downloading tile $url: ${e.localizedMessage} -> using transparent fallback")
            emptyTransparentBitmap
        }
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
