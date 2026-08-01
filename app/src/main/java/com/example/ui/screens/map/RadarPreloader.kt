package com.example.ui.screens.map

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
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

        val centerX = ((centerLon + 180.0) / 360.0 * numTilesDimension).toInt().coerceIn(0, numTilesDimension - 1)
        val rad = Math.toRadians(centerLat)
        val centerY = ((1.0 - ln(tan(rad) + 1.0 / cos(rad)) / Math.PI) / 2.0 * numTilesDimension).toInt().coerceIn(0, numTilesDimension - 1)

        val tileXs = (centerX - 2..centerX + 2).map { (it + numTilesDimension) % numTilesDimension }.distinct()
        val tileYs = (centerY - 2..centerY + 2).map { it.coerceIn(0, numTilesDimension - 1) }.distinct()

        val keys = mutableSetOf<String>()
        if (layer == MapWeatherLayer.RAIN_RADAR) {
            for (frame in frames) {
                val timestamp = frame.radarFrame?.time ?: frame.timestamp
                for (x in tileXs) {
                    for (y in tileYs) {
                        keys.add("RainViewer_Radar_${timestamp}_${pZoom}_${x}_${y}")
                    }
                }
            }
        } else {
            val layerEndpoint = when (layer) {
                MapWeatherLayer.CLOUDS -> "clouds_new"
                MapWeatherLayer.TEMPERATURE -> "temp_new"
                MapWeatherLayer.WIND -> "wind_new"
                MapWeatherLayer.HUMIDITY -> "humidity_new"
                MapWeatherLayer.PRESSURE -> "pressure_new"
                else -> return emptySet()
            }
            for (frame in frames) {
                for (x in tileXs) {
                    for (y in tileYs) {
                        keys.add("${layerEndpoint}_${pZoom}_${x}_${y}")
                    }
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
        if (layer == MapWeatherLayer.NONE || frames.isEmpty()) return@withContext

        val providerMaxZoom = if (layer == MapWeatherLayer.RAIN_RADAR) {
            FutureWeatherLayerManager.RAIN_RADAR_PROVIDER_MAX_ZOOM
        } else {
            FutureWeatherLayerManager.OWM_PROVIDER_MAX_ZOOM
        }

        val pZoom = mapZoom.coerceIn(FutureWeatherLayerManager.PROVIDER_MIN_ZOOM, providerMaxZoom)
        val numTilesDimension = 1 shl pZoom

        val centerX = ((centerLon + 180.0) / 360.0 * numTilesDimension).toInt().coerceIn(0, numTilesDimension - 1)
        val rad = Math.toRadians(centerLat)
        val centerY = ((1.0 - ln(tan(rad) + 1.0 / cos(rad)) / Math.PI) / 2.0 * numTilesDimension).toInt().coerceIn(0, numTilesDimension - 1)

        val tileXs = (centerX - 2..centerX + 2).map { (it + numTilesDimension) % numTilesDimension }.distinct()
        val tileYs = (centerY - 2..centerY + 2).map { it.coerceIn(0, numTilesDimension - 1) }.distinct()

        val tileCoords = mutableListOf<Pair<Int, Int>>()
        for (x in tileXs) {
            for (y in tileYs) {
                tileCoords.add(Pair(x, y))
            }
        }

        val totalTasks = frames.size * tileCoords.size
        if (totalTasks == 0) return@withContext

        var loadedCount = 0

        coroutineScope {
            val jobs = frames.map { frame ->
                async(Dispatchers.IO) {
                    for ((x, y) in tileCoords) {
                        try {
                            preloadSingleTile(layer, frame, pZoom, x, y)
                        } catch (e: Exception) {
                            Log.w("RadarPreloader", "Failed to preload tile: ${e.localizedMessage}")
                        }
                        synchronized(this@RadarPreloader) {
                            loadedCount++
                            onProgress(loadedCount, totalTasks)
                        }
                    }
                    onFrameReady?.invoke(frame.index)
                }
            }
            jobs.awaitAll()
        }

        // Clean up any stale RAM tiles not in current frame set
        val activeKeys = getRequiredTileKeys(layer, frames, centerLat, centerLon, mapZoom)
        TileRamCache.retainOnly(activeKeys)
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

        val centerX = ((centerLon + 180.0) / 360.0 * numTilesDimension).toInt().coerceIn(0, numTilesDimension - 1)
        val rad = Math.toRadians(centerLat)
        val centerY = ((1.0 - ln(tan(rad) + 1.0 / cos(rad)) / Math.PI) / 2.0 * numTilesDimension).toInt().coerceIn(0, numTilesDimension - 1)

        val tileXs = (centerX - 2..centerX + 2).map { (it + numTilesDimension) % numTilesDimension }.distinct()
        val tileYs = (centerY - 2..centerY + 2).map { it.coerceIn(0, numTilesDimension - 1) }.distinct()

        var netRequests = 0
        for (x in tileXs) {
            for (y in tileYs) {
                try {
                    val key = if (layer == MapWeatherLayer.RAIN_RADAR) {
                        val timestamp = frame.radarFrame?.time ?: frame.timestamp
                        "RainViewer_Radar_${timestamp}_${pZoom}_${x}_${y}"
                    } else {
                        val layerEndpoint = when (layer) {
                            MapWeatherLayer.CLOUDS -> "clouds_new"
                            MapWeatherLayer.TEMPERATURE -> "temp_new"
                            MapWeatherLayer.WIND -> "wind_new"
                            MapWeatherLayer.HUMIDITY -> "humidity_new"
                            MapWeatherLayer.PRESSURE -> "pressure_new"
                            else -> continue
                        }
                        "${layerEndpoint}_${pZoom}_${x}_${y}"
                    }

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
        if (layer == MapWeatherLayer.RAIN_RADAR) {
            val timestamp = frame.radarFrame?.time ?: frame.timestamp
            val cacheKey = "RainViewer_Radar_${timestamp}_${zoom}_${x}_${y}"

            if (TileRamCache.contains(cacheKey)) return

            val diskTile = DiskTileCache.get(cacheKey)
            if (diskTile != null) {
                RadarApiTracker.logDiskCacheHit(cacheKey)
                TileRamCache.put(cacheKey, diskTile)
                return
            }

            val tileUrl = frame.radarFrame?.buildTileUrl(zoom, x, y)
                ?: "https://tilecache.rainviewer.com/v2/radar/$timestamp/256/$zoom/$x/$y/4/1_1.png"

            val bitmap = downloadAndDecode(tileUrl) ?: return
            TileRamCache.put(cacheKey, bitmap)
            DiskTileCache.put(cacheKey, bitmap)

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
            var bitmap = downloadAndDecode(tileUrl) ?: return

            if (layer == MapWeatherLayer.CLOUDS) {
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
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    } else null
                } else null
            }
        } catch (e: Exception) {
            null
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
