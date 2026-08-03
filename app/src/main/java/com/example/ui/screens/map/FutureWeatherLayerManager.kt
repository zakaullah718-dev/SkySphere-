package com.example.ui.screens.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.Log
import android.util.LruCache
import com.example.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.osmdroid.tileprovider.MapTileProviderArray
import org.osmdroid.tileprovider.MapTileProviderBase
import org.osmdroid.tileprovider.modules.IFilesystemCache
import org.osmdroid.tileprovider.modules.MapTileModuleProviderBase
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.TilesOverlay
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.FutureTask
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.tan

object RadarTileFetcher {
    private val semaphore = Semaphore(4) // Rate limit to max 4 concurrent network tile downloads
    private val inFlightTasks = ConcurrentHashMap<String, FutureTask<Bitmap?>>()

    fun fetchOrDeduplicateTile(
        cacheKey: String,
        fetcher: () -> Bitmap?
    ): Bitmap? {
        // 1. Check RAM Cache
        val ramCached = TileRamCache.get(cacheKey)
        if (ramCached != null) {
            RadarApiTracker.logRamCacheHit(cacheKey)
            return ramCached
        }

        // 2. Check Local Disk Cache
        val diskCached = DiskTileCache.get(cacheKey)
        if (diskCached != null) {
            RadarApiTracker.logDiskCacheHit(cacheKey)
            TileRamCache.put(cacheKey, diskCached)
            return diskCached
        }

        // 3. Deduplicate in-flight requests (prevent duplicate tile downloads)
        val newTask = FutureTask<Bitmap?> {
            semaphore.acquire()
            try {
                val bitmap = fetcher()
                if (bitmap != null) {
                    TileRamCache.put(cacheKey, bitmap)
                    DiskTileCache.put(cacheKey, bitmap)
                }
                bitmap
            } finally {
                semaphore.release()
            }
        }

        val existingTask = inFlightTasks.putIfAbsent(cacheKey, newTask)
        if (existingTask != null) {
            RadarApiTracker.logDuplicatePrevented(cacheKey)
            return try {
                existingTask.get(5, TimeUnit.SECONDS)
            } catch (e: Exception) {
                null
            }
        }

        return try {
            newTask.run()
            newTask.get(5, TimeUnit.SECONDS)
        } catch (e: Exception) {
            null
        } finally {
            inFlightTasks.remove(cacheKey)
        }
    }
}

fun org.osmdroid.views.overlay.OverlayManager.refresh() {
    // Extension function for OSMDroid OverlayManager refresh
}

class WeatherTilesOverlay(
    val pTileProvider: MapTileProviderBase,
    val pContext: Context,
    val moduleProvider: MapTileModuleProviderBase?
) : TilesOverlay(pTileProvider, pContext) {

    fun updateFrame(frame: TimeLapseFrame?, mapView: MapView? = null) {
        if (moduleProvider is RainRadarTileModuleProvider) {
            moduleProvider.customRadarFrame = frame?.radarFrame
        } else if (moduleProvider is OwmTileModuleProvider) {
            moduleProvider.currentFrame = frame
        }

        val overlayId = System.identityHashCode(this)
        val timestamp = frame?.radarFrame?.time ?: frame?.timestamp ?: 0L
        val sampleUrl = if (moduleProvider is RainRadarTileModuleProvider && frame?.radarFrame != null) {
            val pZoom = mapView?.zoomLevelDouble?.toInt()?.coerceIn(FutureWeatherLayerManager.PROVIDER_MIN_ZOOM, FutureWeatherLayerManager.RAIN_RADAR_PROVIDER_MAX_ZOOM) ?: 5
            frame.radarFrame.buildTileUrl(pZoom, 0, 0)
        } else if (moduleProvider is OwmTileModuleProvider) {
            val pZoom = mapView?.zoomLevelDouble?.toInt()?.coerceIn(FutureWeatherLayerManager.PROVIDER_MIN_ZOOM, FutureWeatherLayerManager.OWM_PROVIDER_MAX_ZOOM) ?: 5
            "https://tile.openweathermap.org/map/${moduleProvider.layerEndpoint}/$pZoom/0/0.png"
        } else "N/A"

        Log.d(
            "TimelapsePipeline",
            "FRAME_ADVANCE | FrameIndex: ${frame?.index} | Timestamp: $timestamp | TileURL: $sampleUrl | OverlayID: $overlayId | Layer: ${moduleProvider?.javaClass?.simpleName}"
        )

        val tileSource = when (moduleProvider) {
            is RainRadarTileModuleProvider -> moduleProvider.pTileSource
            is OwmTileModuleProvider -> moduleProvider.pTileSource
            else -> null
        }
        if (tileSource != null) {
            pTileProvider.setTileSource(tileSource)
        }

        mapView?.overlayManager?.refresh()
        mapView?.postInvalidate()

        // Pre-populate osmdroid's tileCache for visible tiles if already present in RAM/Disk cache
        if (frame != null && mapView != null) {
            try {
                val mapZoom = mapView.zoomLevelDouble.toInt()
                val center = mapView.mapCenter
                val centerLat = center.latitude
                val centerLon = center.longitude

                val providerMaxZoom = if (moduleProvider is RainRadarTileModuleProvider) {
                    FutureWeatherLayerManager.RAIN_RADAR_PROVIDER_MAX_ZOOM
                } else {
                    FutureWeatherLayerManager.OWM_PROVIDER_MAX_ZOOM
                }
                val pZoom = mapZoom.coerceIn(FutureWeatherLayerManager.PROVIDER_MIN_ZOOM, providerMaxZoom)
                val numTilesDimension = 1 shl pZoom

                val centerX = ((centerLon + 180.0) / 360.0 * numTilesDimension).toInt().coerceIn(0, numTilesDimension - 1)
                val clampedLat = centerLat.coerceIn(-85.05112878, 85.05112878)
                val rad = Math.toRadians(clampedLat)
                val centerY = ((1.0 - ln(tan(rad) + 1.0 / cos(rad)) / Math.PI) / 2.0 * numTilesDimension).toInt().coerceIn(0, numTilesDimension - 1)

                val tileXs = (centerX - 3..centerX + 3).map { (it + numTilesDimension) % numTilesDimension }.distinct()
                val tileYs = (centerY - 3..centerY + 3).map { it.coerceIn(0, numTilesDimension - 1) }.distinct()

                for (x in tileXs) {
                    for (y in tileYs) {
                        val cacheKey = if (moduleProvider is RainRadarTileModuleProvider) {
                            val time = frame.radarFrame?.time ?: frame.timestamp
                            "RainViewer_Radar_${time}_${pZoom}_${x}_${y}"
                        } else if (moduleProvider is OwmTileModuleProvider) {
                            "${moduleProvider.layerEndpoint}_${pZoom}_${x}_${y}"
                        } else null

                        if (cacheKey != null) {
                            val bitmap = TileRamCache.get(cacheKey) ?: DiskTileCache.get(cacheKey)
                            if (bitmap != null) {
                                val pMapTileIndex = MapTileIndex.getTileIndex(pZoom, x, y)
                                val drawable = BitmapDrawable(pContext.resources, bitmap)
                                pTileProvider.tileCache.putTile(pMapTileIndex, drawable)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("TimelapsePipeline", "Error pre-populating osmdroid tileCache: ${e.localizedMessage}")
            }
        }

        if (frame != null) {
            val layer = when (moduleProvider) {
                is RainRadarTileModuleProvider -> MapWeatherLayer.RAIN_RADAR
                is OwmTileModuleProvider -> {
                    when (moduleProvider.layerEndpoint) {
                        "clouds_new" -> MapWeatherLayer.CLOUDS
                        "temp_new" -> MapWeatherLayer.TEMPERATURE
                        "wind_new" -> MapWeatherLayer.WIND
                        "humidity_new" -> MapWeatherLayer.HUMIDITY
                        "pressure_new" -> MapWeatherLayer.PRESSURE
                        else -> MapWeatherLayer.NONE
                    }
                }
                else -> MapWeatherLayer.NONE
            }
            if (layer != MapWeatherLayer.NONE) {
                val centerLat = mapView?.mapCenter?.latitude ?: 37.7749
                val centerLon = mapView?.mapCenter?.longitude ?: -122.4194
                val mapZoom = mapView?.zoomLevelDouble?.toInt() ?: 5
                CoroutineScope(Dispatchers.IO).launch {
                    RadarPreloader.preloadFrames(
                        layer = layer,
                        frames = listOf(frame),
                        centerLat = centerLat,
                        centerLon = centerLon,
                        mapZoom = mapZoom
                    )
                }
            }
        }

        mapView?.postInvalidate()

        Log.d(
            "TimelapsePipeline",
            "MAP_INVALIDATE | FrameIndex: ${frame?.index} | Timestamp: $timestamp | OverlayID: $overlayId | Redraw event dispatched"
        )
        Log.d("RadarDebug", "REDRAW: PostInvalidate called for frame ${frame?.index} with URL sample: $sampleUrl")
    }

    fun setPlaybackActive(active: Boolean) {
        if (moduleProvider is RainRadarTileModuleProvider) {
            moduleProvider.isPlaybackActive = active
        } else if (moduleProvider is OwmTileModuleProvider) {
            moduleProvider.isPlaybackActive = active
        }
    }
}

class FutureWeatherLayerManager(
    private val radarRepository: FutureRadarRepository = FutureRadarRepository()
) {

    companion object {
        const val PROVIDER_MIN_ZOOM = 1
        const val RAIN_RADAR_PROVIDER_MAX_ZOOM = 12
        const val OWM_PROVIDER_MAX_ZOOM = 12
        const val PROVIDER_MAX_ZOOM = 12
        const val OVERLAY_MAX_ZOOM = 20

        val emptyTransparentTile: Drawable by lazy {
            val bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
            BitmapDrawable(null, bitmap)
        }
    }

    fun createTilesOverlay(
        context: Context,
        layer: MapWeatherLayer,
        radarTimestamp: Long = radarRepository.getFallbackTimestamp(),
        customRadarFrame: RadarFrame? = null
    ): WeatherTilesOverlay? {
        if (layer == MapWeatherLayer.NONE) {
            return null
        }

        DiskTileCache.init(context)
        Log.d("RadarCache", "TileProvider Recreation | Creating TileProvider & Module for Layer = $layer, Timestamp = ${customRadarFrame?.time ?: radarTimestamp}")

        if (layer == MapWeatherLayer.RAIN_RADAR) {
            val dummyTileSource = object : OnlineTileSourceBase(
                "RainViewer_Radar_${customRadarFrame?.time ?: radarTimestamp}",
                PROVIDER_MIN_ZOOM,
                OVERLAY_MAX_ZOOM,
                256,
                ".png",
                arrayOf("https://tilecache.rainviewer.com")
            ) {
                override fun getTileURLString(pMapTileIndex: Long): String = ""
            }

            val moduleProvider = RainRadarTileModuleProvider(dummyTileSource, null, radarRepository, customRadarFrame)
            val providerArray = MapTileProviderArray(dummyTileSource, null, arrayOf(moduleProvider))

            return WeatherTilesOverlay(providerArray, context, moduleProvider).apply {
                loadingBackgroundColor = Color.TRANSPARENT
                loadingLineColor = Color.TRANSPARENT
            }
        }

        val owmApiKey = try {
            val key = BuildConfig.WEATHER_API_KEY
            if (!key.isNullOrBlank() && key != "PLACEholder_WEATHER_API_KEY") key else "f0308472599cabe4521d65850bb6ba22"
        } catch (e: Exception) {
            "f0308472599cabe4521d65850bb6ba22"
        }

        val layerEndpoint = when (layer) {
            MapWeatherLayer.CLOUDS -> "clouds_new"
            MapWeatherLayer.TEMPERATURE -> "temp_new"
            MapWeatherLayer.WIND -> "wind_new"
            MapWeatherLayer.HUMIDITY -> "humidity_new"
            MapWeatherLayer.PRESSURE -> "pressure_new"
            else -> return null
        }

        val dummyTileSource = object : OnlineTileSourceBase(
            "OWM_${layer.name}",
            PROVIDER_MIN_ZOOM,
            OVERLAY_MAX_ZOOM,
            256,
            ".png",
            arrayOf("https://tile.openweathermap.org")
        ) {
            override fun getTileURLString(pMapTileIndex: Long): String = ""
        }

        val moduleProvider = OwmTileModuleProvider(
            pTileSource = dummyTileSource,
            pTileCache = null,
            layerEndpoint = layerEndpoint,
            owmApiKey = owmApiKey,
            isCloudsDark = (layer == MapWeatherLayer.CLOUDS)
        )
        val providerArray = MapTileProviderArray(dummyTileSource, null, arrayOf(moduleProvider))

        return WeatherTilesOverlay(providerArray, context, moduleProvider).apply {
            loadingBackgroundColor = Color.TRANSPARENT
            loadingLineColor = Color.TRANSPARENT
        }
    }
}

class RainRadarTileModuleProvider(
    val pTileSource: ITileSource,
    pTileCache: IFilesystemCache?,
    private val radarRepository: FutureRadarRepository,
    @Volatile var customRadarFrame: RadarFrame? = null,
    @Volatile var isPlaybackActive: Boolean = false,
    @Volatile var mapView: MapView? = null
) : MapTileModuleProviderBase(8, 200) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    private val parentBitmapCache = LruCache<String, Bitmap>(120)

    override fun getUsesDataConnection(): Boolean = true
    override fun getName(): String = "RainRadarTileDownloader"
    override fun getThreadGroupName(): String = "rainradar"
    override fun getMinimumZoomLevel(): Int = FutureWeatherLayerManager.PROVIDER_MIN_ZOOM
    override fun getMaximumZoomLevel(): Int = 20
    override fun setTileSource(pTileSource: ITileSource?) {}

    override fun getTileLoader(): TileLoader {
        return RainTileLoader()
    }

    private fun fetchProviderTileBitmap(zoom: Int, x: Int, y: Int): Pair<Bitmap?, String> {
        if (zoom < FutureWeatherLayerManager.PROVIDER_MIN_ZOOM || zoom > FutureWeatherLayerManager.RAIN_RADAR_PROVIDER_MAX_ZOOM) {
            return Pair(null, "Unsupported provider zoom level")
        }
        val clampedZoom = zoom.coerceIn(FutureWeatherLayerManager.PROVIDER_MIN_ZOOM, FutureWeatherLayerManager.RAIN_RADAR_PROVIDER_MAX_ZOOM)
        var frame = customRadarFrame ?: radarRepository.getLatestRadarFrameSync()
        val cacheKey = "RainViewer_Radar_${frame.time}_${clampedZoom}_${x}_${y}"

        val ramHit = TileRamCache.get(cacheKey)
        if (ramHit != null && !ramHit.isRecycled) {
            return Pair(ramHit, "RAM Cache Hit")
        }

        val diskHit = DiskTileCache.get(cacheKey)
        if (diskHit != null && !diskHit.isRecycled) {
            TileRamCache.put(cacheKey, diskHit)
            return Pair(diskHit, "Disk Cache Hit")
        }

        val bitmap = RadarTileFetcher.fetchOrDeduplicateTile(cacheKey) {
            var tileUrl = frame.buildTileUrl(clampedZoom, x, y)
            RadarApiTracker.logRainViewerRequest(tileUrl)
            var tileBytes: ByteArray? = null
            var httpCode = 0

            try {
                val req = Request.Builder()
                    .url(tileUrl)
                    .header("User-Agent", "SkySphereApp/1.0")
                    .build()
                client.newCall(req).execute().use { response ->
                    httpCode = response.code
                    if (httpCode == 410) {
                        radarRepository.invalidateCache()
                        frame = radarRepository.getLatestRadarFrameSync(forceRefresh = true)
                        tileUrl = frame.buildTileUrl(clampedZoom, x, y)
                        RadarApiTracker.logRainViewerRequest(tileUrl)
                        val retryReq = Request.Builder()
                            .url(tileUrl)
                            .header("User-Agent", "SkySphereApp/1.0")
                            .build()
                        client.newCall(retryReq).execute().use { retryResp ->
                            if (retryResp.isSuccessful) {
                                tileBytes = retryResp.body?.bytes()
                            }
                        }
                    } else if (response.isSuccessful) {
                        tileBytes = response.body?.bytes()
                    }
                }
            } catch (e: Exception) {
                Log.e("RainRadarTile", "Network error fetching provider tile [Z=$clampedZoom, X=$x, Y=$y]: ${e.localizedMessage}")
            }

            if (tileBytes != null && tileBytes!!.isNotEmpty()) {
                try {
                    BitmapFactory.decodeByteArray(tileBytes, 0, tileBytes!!.size)
                } catch (e: Exception) {
                    null
                }
            } else null
        }

        return Pair(bitmap, if (bitmap != null) "Loaded" else "Failed")
    }

    private inner class RainTileLoader : MapTileModuleProviderBase.TileLoader() {
        override fun loadTile(pMapTileIndex: Long): Drawable? {
            val rawZoom = MapTileIndex.getZoom(pMapTileIndex)
            if (rawZoom < FutureWeatherLayerManager.PROVIDER_MIN_ZOOM || rawZoom > 20) {
                return FutureWeatherLayerManager.emptyTransparentTile
            }

            val mapZoom = rawZoom.coerceIn(
                FutureWeatherLayerManager.PROVIDER_MIN_ZOOM,
                20
            )
            val tileX = MapTileIndex.getX(pMapTileIndex)
            val tileY = MapTileIndex.getY(pMapTileIndex)

            Log.d("RadarDraw", "Drawing tile at $tileX,$tileY (zoom=$mapZoom)")

            val providerMaxZoom = FutureWeatherLayerManager.RAIN_RADAR_PROVIDER_MAX_ZOOM

            val drawableResult: Drawable? = if (mapZoom <= providerMaxZoom) {
                val (bitmap, _) = fetchProviderTileBitmap(mapZoom, tileX, tileY)
                if (bitmap != null) {
                    Log.d("RadarDebug", "RENDER: loadTile returned bitmap with width=${bitmap.width} and pixel count=${bitmap.byteCount}")
                }
                if (bitmap != null) BitmapDrawable(null, bitmap) else FutureWeatherLayerManager.emptyTransparentTile
            } else {
                val parentZoom = providerMaxZoom
                val deltaZ = mapZoom - parentZoom
                val parentX = tileX shr deltaZ
                val parentY = tileY shr deltaZ

                val (parentBitmap, _) = fetchProviderTileBitmap(parentZoom, parentX, parentY)
                if (parentBitmap == null || parentBitmap.isRecycled) {
                    FutureWeatherLayerManager.emptyTransparentTile
                } else {
                    Log.d("RadarDebug", "RENDER: loadTile returned bitmap with width=${parentBitmap.width} and pixel count=${parentBitmap.byteCount}")

                    try {
                        val scale = 1 shl deltaZ
                        val subSize = (256 / scale).coerceAtLeast(1)
                        val offsetX = ((tileX and (scale - 1)) * 256 / scale).coerceIn(0, 255)
                        val offsetY = ((tileY and (scale - 1)) * 256 / scale).coerceIn(0, 255)

                        val cropWidth = minOf(subSize, parentBitmap.width - offsetX)
                        val cropHeight = minOf(subSize, parentBitmap.height - offsetY)

                        if (cropWidth <= 0 || cropHeight <= 0) {
                            FutureWeatherLayerManager.emptyTransparentTile
                        } else {
                            val cropped = Bitmap.createBitmap(parentBitmap, offsetX, offsetY, cropWidth, cropHeight)
                            val scaledBitmap = Bitmap.createScaledBitmap(cropped, 256, 256, true)
                            BitmapDrawable(null, scaledBitmap)
                        }
                    } catch (e: Exception) {
                        Log.e("RainRadarZoom", "Error cropping/scaling radar tile [MapZoom=$mapZoom, X=$tileX, Y=$tileY]: ${e.localizedMessage}")
                        FutureWeatherLayerManager.emptyTransparentTile
                    }
                }
            }

            mapView?.postInvalidate()
            return drawableResult
        }
    }
}

class OwmTileModuleProvider(
    val pTileSource: ITileSource,
    pTileCache: IFilesystemCache?,
    val layerEndpoint: String,
    private val owmApiKey: String,
    private val isCloudsDark: Boolean = false,
    @Volatile var isPlaybackActive: Boolean = false,
    @Volatile var currentFrame: TimeLapseFrame? = null
) : MapTileModuleProviderBase(8, 200) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    private val parentBitmapCache = LruCache<String, Bitmap>(80)

    override fun getUsesDataConnection(): Boolean = true
    override fun getName(): String = "OwmTileDownloader_$layerEndpoint"
    override fun getThreadGroupName(): String = "owmtile"
    override fun getMinimumZoomLevel(): Int = FutureWeatherLayerManager.PROVIDER_MIN_ZOOM
    override fun getMaximumZoomLevel(): Int = FutureWeatherLayerManager.OVERLAY_MAX_ZOOM
    override fun setTileSource(pTileSource: ITileSource?) {}

    override fun getTileLoader(): TileLoader {
        return OwmTileLoader()
    }

    private fun fetchProviderTileBitmap(zoom: Int, x: Int, y: Int): Bitmap? {
        val clampedZoom = zoom.coerceIn(FutureWeatherLayerManager.PROVIDER_MIN_ZOOM, FutureWeatherLayerManager.OWM_PROVIDER_MAX_ZOOM)
        val cacheKey = "${layerEndpoint}_${clampedZoom}_${x}_${y}"

        val ramHit = TileRamCache.get(cacheKey)
        if (ramHit != null) {
            return ramHit
        }

        val diskHit = DiskTileCache.get(cacheKey)
        if (diskHit != null) {
            TileRamCache.put(cacheKey, diskHit)
            return diskHit
        }

        return RadarTileFetcher.fetchOrDeduplicateTile(cacheKey) {
            val url = "https://tile.openweathermap.org/map/$layerEndpoint/$clampedZoom/$x/$y.png?appid=$owmApiKey"
            RadarApiTracker.logOpenWeatherRequest(url)
            var tileBytes: ByteArray? = null

            try {
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", "SkySphereApp/1.0")
                    .build()
                client.newCall(req).execute().use { response ->
                    if (response.isSuccessful) {
                        tileBytes = response.body?.bytes()
                    }
                }
            } catch (e: Exception) {
                Log.e("OwmTile", "Network error fetching OWM tile [$layerEndpoint Z=$clampedZoom X=$x Y=$y]: ${e.localizedMessage}")
            }

            if (tileBytes == null || tileBytes!!.isEmpty()) {
                null
            } else {
                try {
                    var bitmap = BitmapFactory.decodeByteArray(tileBytes, 0, tileBytes!!.size)
                    if (bitmap != null && isCloudsDark) {
                        bitmap = applyDarkCloudStyle(bitmap)
                    }
                    bitmap
                } catch (e: Exception) {
                    Log.e("OwmTile", "Decode error for OWM tile: ${e.localizedMessage}")
                    null
                }
            }
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

    private inner class OwmTileLoader : MapTileModuleProviderBase.TileLoader() {
        override fun loadTile(pMapTileIndex: Long): Drawable? {
            val mapZoom = MapTileIndex.getZoom(pMapTileIndex).coerceIn(
                FutureWeatherLayerManager.PROVIDER_MIN_ZOOM,
                FutureWeatherLayerManager.OVERLAY_MAX_ZOOM
            )
            val tileX = MapTileIndex.getX(pMapTileIndex)
            val tileY = MapTileIndex.getY(pMapTileIndex)

            val providerMaxZoom = FutureWeatherLayerManager.OWM_PROVIDER_MAX_ZOOM

            if (mapZoom <= providerMaxZoom) {
                val bitmap = fetchProviderTileBitmap(mapZoom, tileX, tileY)
                return if (bitmap != null) BitmapDrawable(null, bitmap) else FutureWeatherLayerManager.emptyTransparentTile
            } else {
                val deltaZ = mapZoom - providerMaxZoom
                val parentX = tileX shr deltaZ
                val parentY = tileY shr deltaZ

                val parentBitmap = fetchProviderTileBitmap(providerMaxZoom, parentX, parentY)
                if (parentBitmap == null || parentBitmap.isRecycled) {
                    return FutureWeatherLayerManager.emptyTransparentTile
                }

                return try {
                    val scale = 1 shl deltaZ
                    val subSize = (256 / scale).coerceAtLeast(1)
                    val offsetX = ((tileX and (scale - 1)) * 256 / scale).coerceIn(0, 255)
                    val offsetY = ((tileY and (scale - 1)) * 256 / scale).coerceIn(0, 255)

                    val cropWidth = minOf(subSize, parentBitmap.width - offsetX)
                    val cropHeight = minOf(subSize, parentBitmap.height - offsetY)

                    if (cropWidth <= 0 || cropHeight <= 0) return FutureWeatherLayerManager.emptyTransparentTile

                    val cropped = Bitmap.createBitmap(parentBitmap, offsetX, offsetY, cropWidth, cropHeight)
                    val scaledBitmap = Bitmap.createScaledBitmap(cropped, 256, 256, true)

                    BitmapDrawable(null, scaledBitmap)
                } catch (e: Exception) {
                    Log.e("OwmTileZoom", "Error cropping OWM tile: ${e.localizedMessage}")
                    FutureWeatherLayerManager.emptyTransparentTile
                }
            }
        }
    }
}

