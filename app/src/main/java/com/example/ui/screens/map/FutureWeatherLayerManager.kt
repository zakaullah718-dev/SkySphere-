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
import org.osmdroid.tileprovider.MapTileProviderBasic
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
        const val PROVIDER_MIN_ZOOM = 0
        const val PROVIDER_MAX_ZOOM = 12
        const val OVERLAY_MAX_ZOOM = 20
    }

    fun createTilesOverlay(
        context: Context,
        layer: MapWeatherLayer,
        radarTimestamp: Long = radarRepository.getFallbackTimestamp()
    ): TilesOverlay? {
        if (layer == MapWeatherLayer.NONE) {
            return null
        }

        if (layer == MapWeatherLayer.RAIN_RADAR) {
            val dummyTileSource = object : OnlineTileSourceBase(
                "RainViewer_Radar",
                PROVIDER_MIN_ZOOM,
                OVERLAY_MAX_ZOOM,
                256,
                ".png",
                arrayOf("https://tilecache.rainviewer.com")
            ) {
                override fun getTileURLString(pMapTileIndex: Long): String {
                    val zoom = MapTileIndex.getZoom(pMapTileIndex).coerceIn(PROVIDER_MIN_ZOOM, PROVIDER_MAX_ZOOM)
                    val x = MapTileIndex.getX(pMapTileIndex)
                    val y = MapTileIndex.getY(pMapTileIndex)
                    val frame = radarRepository.getLatestRadarFrameSync()
                    return frame.buildTileUrl(zoom, x, y)
                }
            }

            val moduleProvider = RainRadarTileModuleProvider(dummyTileSource, null, radarRepository)
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

        if (layer == MapWeatherLayer.CLOUDS) {
            val dummyTileSource = object : OnlineTileSourceBase(
                "OWM_Clouds_Dark",
                0,
                OVERLAY_MAX_ZOOM,
                256,
                ".png",
                arrayOf("https://tile.openweathermap.org")
            ) {
                override fun getTileURLString(pMapTileIndex: Long): String = ""
            }

            val moduleProvider = CloudTileModuleProvider(dummyTileSource, null, owmApiKey)
            val providerArray = MapTileProviderArray(dummyTileSource, null, arrayOf(moduleProvider))

            return TilesOverlay(providerArray, context).apply {
                loadingBackgroundColor = Color.TRANSPARENT
                loadingLineColor = Color.TRANSPARENT
            }
        }

        val timeBucket = System.currentTimeMillis() / 300000 // 5-minute cache window
        val sourceName = "OWM_${layer.name}_$timeBucket"

        val layerEndpoint = when (layer) {
            MapWeatherLayer.CLOUDS -> "clouds_new"
            MapWeatherLayer.TEMPERATURE -> "temp_new"
            MapWeatherLayer.WIND -> "wind_new"
            MapWeatherLayer.PRESSURE -> "pressure_new"
            else -> return null
        }

        val tileSource = object : OnlineTileSourceBase(
            sourceName,
            0,
            OVERLAY_MAX_ZOOM,
            256,
            ".png",
            arrayOf()
        ) {
            override fun getTileURLString(pMapTileIndex: Long): String {
                val zoom = MapTileIndex.getZoom(pMapTileIndex).coerceIn(1, 18)
                val x = MapTileIndex.getX(pMapTileIndex)
                val y = MapTileIndex.getY(pMapTileIndex)
                return "https://tile.openweathermap.org/map/$layerEndpoint/$zoom/$x/$y.png?appid=$owmApiKey"
            }
        }

        val provider = MapTileProviderBasic(context, tileSource)

        return TilesOverlay(provider, context).apply {
            loadingBackgroundColor = Color.TRANSPARENT
            loadingLineColor = Color.TRANSPARENT
        }
    }
}

class RainRadarTileModuleProvider(
    pTileSource: ITileSource,
    pTileCache: IFilesystemCache?,
    private val radarRepository: FutureRadarRepository
) : MapTileModuleProviderBase(2, 40) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    // In-memory cache for provider max-zoom (Z=12) parent tiles to ensure smooth upscaling
    private val parentBitmapCache = LruCache<String, Bitmap>(80)

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
        val clampedZoom = zoom.coerceIn(FutureWeatherLayerManager.PROVIDER_MIN_ZOOM, FutureWeatherLayerManager.PROVIDER_MAX_ZOOM)
        var frame = radarRepository.getLatestRadarFrameSync()
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
            val mapZoom = MapTileIndex.getZoom(pMapTileIndex).coerceIn(
                FutureWeatherLayerManager.PROVIDER_MIN_ZOOM,
                FutureWeatherLayerManager.OVERLAY_MAX_ZOOM
            )
            val tileX = MapTileIndex.getX(pMapTileIndex)
            val tileY = MapTileIndex.getY(pMapTileIndex)

            val providerMaxZoom = FutureWeatherLayerManager.PROVIDER_MAX_ZOOM

            if (mapZoom <= providerMaxZoom) {
                // Direct request at provider supported zoom level (0..12)
                val (bitmap, status) = fetchProviderTileBitmap(mapZoom, tileX, tileY)
                Log.d(
                    "RainRadarZoom",
                    "MapZoom: $mapZoom | RequestedZoom: $mapZoom | ProviderMaxZoom: $providerMaxZoom | Tile: X=$tileX, Y=$tileY | Status: $status"
                )
                return if (bitmap != null) BitmapDrawable(null, bitmap) else null
            } else {
                // User zoomed beyond provider max zoom (12).
                // Clamp tile request to provider max zoom (12) and crop sub-quadrant.
                val deltaZ = mapZoom - providerMaxZoom
                val parentX = tileX shr deltaZ
                val parentY = tileY shr deltaZ

                val (parentBitmap, parentStatus) = fetchProviderTileBitmap(providerMaxZoom, parentX, parentY)
                if (parentBitmap == null || parentBitmap.isRecycled) {
                    Log.d(
                        "RainRadarZoom",
                        "MapZoom: $mapZoom | RequestedZoom: $providerMaxZoom (Clamped from $mapZoom) | ProviderMaxZoom: $providerMaxZoom | Tile: X=$tileX, Y=$tileY | Parent: X=$parentX, Y=$parentY | Status: Parent tile unavailable ($parentStatus)"
                    )
                    return null
                }

                return try {
                    val scale = 1 shl deltaZ
                    val subSize = (256 / scale).coerceAtLeast(1)
                    val offsetX = ((tileX and (scale - 1)) * 256 / scale).coerceIn(0, 255)
                    val offsetY = ((tileY and (scale - 1)) * 256 / scale).coerceIn(0, 255)

                    val cropWidth = minOf(subSize, parentBitmap.width - offsetX)
                    val cropHeight = minOf(subSize, parentBitmap.height - offsetY)

                    if (cropWidth <= 0 || cropHeight <= 0) return null

                    val cropped = Bitmap.createBitmap(parentBitmap, offsetX, offsetY, cropWidth, cropHeight)
                    val scaledBitmap = Bitmap.createScaledBitmap(cropped, 256, 256, true)
                    if (cropped != scaledBitmap && !cropped.isRecycled && cropped != parentBitmap) {
                        cropped.recycle()
                    }

                    Log.d(
                        "RainRadarZoom",
                        "MapZoom: $mapZoom | RequestedZoom: $providerMaxZoom (Clamped from $mapZoom) | ProviderMaxZoom: $providerMaxZoom | Tile: X=$tileX, Y=$tileY | Parent: X=$parentX, Y=$parentY | Status: Scaled sub-tile from Z=$providerMaxZoom parent ($parentStatus)"
                    )

                    BitmapDrawable(null, scaledBitmap)
                } catch (e: Exception) {
                    Log.e("RainRadarZoom", "Error cropping/scaling radar tile [MapZoom=$mapZoom, X=$tileX, Y=$tileY]: ${e.localizedMessage}")
                    null
                }
            }
        }
    }
}

class CloudTileModuleProvider(
    pTileSource: ITileSource,
    pTileCache: IFilesystemCache?,
    private val owmApiKey: String
) : MapTileModuleProviderBase(2, 40) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    override fun getUsesDataConnection(): Boolean = true
    override fun getName(): String = "CloudTileDownloader"
    override fun getThreadGroupName(): String = "cloudtile"
    override fun getMinimumZoomLevel(): Int = 0
    override fun getMaximumZoomLevel(): Int = FutureWeatherLayerManager.OVERLAY_MAX_ZOOM
    override fun setTileSource(pTileSource: ITileSource?) {}

    override fun getTileLoader(): TileLoader {
        return CloudTileLoader()
    }

    private inner class CloudTileLoader : MapTileModuleProviderBase.TileLoader() {
        override fun loadTile(pMapTileIndex: Long): Drawable? {
            val mapZoom = MapTileIndex.getZoom(pMapTileIndex).coerceIn(0, FutureWeatherLayerManager.OVERLAY_MAX_ZOOM)
            val tileX = MapTileIndex.getX(pMapTileIndex)
            val tileY = MapTileIndex.getY(pMapTileIndex)

            val reqZoom = mapZoom.coerceIn(1, 18)
            val url = "https://tile.openweathermap.org/map/clouds_new/$reqZoom/$tileX/$tileY.png?appid=$owmApiKey"

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
                Log.e("CloudTile", "Network error loading cloud tile [X=$tileX, Y=$tileY, Z=$mapZoom]: ${e.localizedMessage}")
            }

            if (tileBytes == null || tileBytes!!.isEmpty()) {
                return null
            }

            return try {
                val originalBitmap = BitmapFactory.decodeByteArray(tileBytes, 0, tileBytes!!.size)
                if (originalBitmap != null) {
                    val width = originalBitmap.width
                    val height = originalBitmap.height
                    val pixels = IntArray(width * height)
                    originalBitmap.getPixels(pixels, 0, width, 0, 0, width, height)

                    var nonZero = 0
                    for (i in pixels.indices) {
                        val p = pixels[i]
                        val a = Color.alpha(p)
                        if (a > 0) {
                            nonZero++
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
                    BitmapDrawable(null, darkCloudBitmap)
                } else {
                    null
                }
            } catch (e: Exception) {
                Log.e("CloudTile", "Cloud tile decode exception [X=$tileX, Y=$tileY, Z=$mapZoom]: ${e.localizedMessage}")
                null
            }
        }
    }
}
