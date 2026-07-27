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
import okhttp3.OkHttpClient
import okhttp3.Request
import org.osmdroid.tileprovider.MapTileProviderArray
import org.osmdroid.tileprovider.modules.IFilesystemCache
import org.osmdroid.tileprovider.modules.MapTileModuleProviderBase
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.overlay.TilesOverlay
import java.util.concurrent.TimeUnit

class FutureWeatherLayerManager(
    private val radarRepository: FutureRadarRepository = FutureRadarRepository()
) {

    companion object {
        const val PROVIDER_MIN_ZOOM = 1
        const val RAIN_RADAR_PROVIDER_MAX_ZOOM = 7
        const val OWM_PROVIDER_MAX_ZOOM = 12
        const val PROVIDER_MAX_ZOOM = 7
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
    ): TilesOverlay? {
        if (layer == MapWeatherLayer.NONE) {
            return null
        }

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

            return TilesOverlay(providerArray, context).apply {
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

        return TilesOverlay(providerArray, context).apply {
            loadingBackgroundColor = Color.TRANSPARENT
            loadingLineColor = Color.TRANSPARENT
        }
    }
}

class RainRadarTileModuleProvider(
    pTileSource: ITileSource,
    pTileCache: IFilesystemCache?,
    private val radarRepository: FutureRadarRepository,
    @Volatile var customRadarFrame: RadarFrame? = null
) : MapTileModuleProviderBase(2, 40) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    private val parentBitmapCache = LruCache<String, Bitmap>(120)

    override fun getUsesDataConnection(): Boolean = true
    override fun getName(): String = "RainRadarTileDownloader"
    override fun getThreadGroupName(): String = "rainradar"
    override fun getMinimumZoomLevel(): Int = FutureWeatherLayerManager.PROVIDER_MIN_ZOOM
    override fun getMaximumZoomLevel(): Int = FutureWeatherLayerManager.OVERLAY_MAX_ZOOM
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
        val cacheKey = "${frame.time}_${clampedZoom}_${x}_${y}"

        val cached = parentBitmapCache.get(cacheKey)
        if (cached != null && !cached.isRecycled) {
            return Pair(cached, "Reused from in-memory cache")
        }

        var tileUrl = frame.buildTileUrl(clampedZoom, x, y)
        var tileBytes: ByteArray? = null
        var httpCode = 0
        var isFresh = false

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
                    val retryReq = Request.Builder()
                        .url(tileUrl)
                        .header("User-Agent", "SkySphereApp/1.0")
                        .build()
                    client.newCall(retryReq).execute().use { retryResp ->
                        if (retryResp.isSuccessful) {
                            tileBytes = retryResp.body?.bytes()
                            isFresh = true
                        }
                    }
                } else if (response.isSuccessful) {
                    tileBytes = response.body?.bytes()
                    isFresh = true
                }
            }
        } catch (e: Exception) {
            Log.e("RainRadarTile", "Network error fetching provider tile [Z=$clampedZoom, X=$x, Y=$y]: ${e.localizedMessage}")
        }

        if (tileBytes == null || tileBytes!!.isEmpty()) {
            return Pair(null, "Download failed (HTTP $httpCode)")
        }

        return try {
            val bitmap = BitmapFactory.decodeByteArray(tileBytes, 0, tileBytes!!.size)
            if (bitmap != null) {
                parentBitmapCache.put(cacheKey, bitmap)
                val sourceText = if (isFresh) "Freshly downloaded" else "Retrieved from cache"
                Pair(bitmap, sourceText)
            } else {
                Pair(null, "Decode failed")
            }
        } catch (e: Exception) {
            Pair(null, "Decode exception: ${e.localizedMessage}")
        }
    }

    private inner class RainTileLoader : MapTileModuleProviderBase.TileLoader() {
        override fun loadTile(pMapTileIndex: Long): Drawable? {
            val rawZoom = MapTileIndex.getZoom(pMapTileIndex)
            if (rawZoom < FutureWeatherLayerManager.PROVIDER_MIN_ZOOM || rawZoom > FutureWeatherLayerManager.OVERLAY_MAX_ZOOM) {
                return FutureWeatherLayerManager.emptyTransparentTile
            }

            val mapZoom = rawZoom.coerceIn(
                FutureWeatherLayerManager.PROVIDER_MIN_ZOOM,
                FutureWeatherLayerManager.OVERLAY_MAX_ZOOM
            )
            val tileX = MapTileIndex.getX(pMapTileIndex)
            val tileY = MapTileIndex.getY(pMapTileIndex)

            val providerMaxZoom = FutureWeatherLayerManager.RAIN_RADAR_PROVIDER_MAX_ZOOM

            if (mapZoom <= providerMaxZoom) {
                val (bitmap, status) = fetchProviderTileBitmap(mapZoom, tileX, tileY)
                return if (bitmap != null) BitmapDrawable(null, bitmap) else FutureWeatherLayerManager.emptyTransparentTile
            } else {
                val deltaZ = mapZoom - providerMaxZoom
                val parentX = tileX shr deltaZ
                val parentY = tileY shr deltaZ

                val (parentBitmap, _) = fetchProviderTileBitmap(providerMaxZoom, parentX, parentY)
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
                    if (cropped != scaledBitmap && !cropped.isRecycled && cropped != parentBitmap) {
                        cropped.recycle()
                    }

                    BitmapDrawable(null, scaledBitmap)
                } catch (e: Exception) {
                    Log.e("RainRadarZoom", "Error cropping/scaling radar tile [MapZoom=$mapZoom, X=$tileX, Y=$tileY]: ${e.localizedMessage}")
                    FutureWeatherLayerManager.emptyTransparentTile
                }
            }
        }
    }
}

class OwmTileModuleProvider(
    pTileSource: ITileSource,
    pTileCache: IFilesystemCache?,
    private val layerEndpoint: String,
    private val owmApiKey: String,
    private val isCloudsDark: Boolean = false
) : MapTileModuleProviderBase(2, 40) {

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

        val cached = parentBitmapCache.get(cacheKey)
        if (cached != null && !cached.isRecycled) {
            return cached
        }

        val url = "https://tile.openweathermap.org/map/$layerEndpoint/$clampedZoom/$x/$y.png?appid=$owmApiKey"
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
            return null
        }

        return try {
            var bitmap = BitmapFactory.decodeByteArray(tileBytes, 0, tileBytes!!.size)
            if (bitmap != null) {
                if (isCloudsDark) {
                    bitmap = applyDarkCloudStyle(bitmap)
                }
                parentBitmapCache.put(cacheKey, bitmap)
                bitmap
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("OwmTile", "Decode error for OWM tile: ${e.localizedMessage}")
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
                    if (cropped != scaledBitmap && !cropped.isRecycled && cropped != parentBitmap) {
                        cropped.recycle()
                    }

                    BitmapDrawable(null, scaledBitmap)
                } catch (e: Exception) {
                    Log.e("OwmTileZoom", "Error cropping OWM tile: ${e.localizedMessage}")
                    FutureWeatherLayerManager.emptyTransparentTile
                }
            }
        }
    }
}
