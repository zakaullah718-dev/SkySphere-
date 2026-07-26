package com.example.ui.screens.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.Log
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
                0,
                12,
                256,
                ".png",
                arrayOf("https://tilecache.rainviewer.com")
            ) {
                override fun getTileURLString(pMapTileIndex: Long): String {
                    val zoom = MapTileIndex.getZoom(pMapTileIndex).coerceIn(0, 12)
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
                1,
                18,
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
            1,
            18,
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

    override fun getUsesDataConnection(): Boolean = true
    override fun getName(): String = "RainRadarTileDownloader"
    override fun getThreadGroupName(): String = "rainradar"
    override fun getMinimumZoomLevel(): Int = 0
    override fun getMaximumZoomLevel(): Int = 12
    override fun setTileSource(pTileSource: ITileSource?) {}

    override fun getTileLoader(): TileLoader {
        return RainTileLoader()
    }

    private inner class RainTileLoader : MapTileModuleProviderBase.TileLoader() {
        override fun loadTile(pMapTileIndex: Long): Drawable? {
            val zoom = MapTileIndex.getZoom(pMapTileIndex).coerceIn(0, 12)
            val x = MapTileIndex.getX(pMapTileIndex)
            val y = MapTileIndex.getY(pMapTileIndex)

            var frame = radarRepository.getLatestRadarFrameSync()
            var tileUrl = frame.buildTileUrl(zoom, x, y)

            Log.d("RainRadarTile", "[Tile Load Request] Generated Tile URL [X=$x, Y=$y, Z=$zoom, Time=${frame.time}, Path=${frame.path}]: $tileUrl")

            var httpCode = 0
            var contentType = ""
            var tileBytes: ByteArray? = null

            try {
                val req = Request.Builder()
                    .url(tileUrl)
                    .header("User-Agent", "SkySphereApp/1.0")
                    .build()
                client.newCall(req).execute().use { response ->
                    httpCode = response.code
                    contentType = response.header("Content-Type", "") ?: ""

                    if (httpCode == 410) {
                        Log.w("RainRadarTile", "HTTP 410 Gone received for $tileUrl. Clearing cache and retrying with fresh frame...")
                        radarRepository.invalidateCache()
                        frame = radarRepository.getLatestRadarFrameSync(forceRefresh = true)
                        tileUrl = frame.buildTileUrl(zoom, x, y)
                        Log.d("RainRadarTile", "Retrying with updated Tile URL [X=$x, Y=$y, Z=$zoom, Time=${frame.time}, Path=${frame.path}]: $tileUrl")

                        val retryReq = Request.Builder()
                            .url(tileUrl)
                            .header("User-Agent", "SkySphereApp/1.0")
                            .build()
                        client.newCall(retryReq).execute().use { retryResp ->
                            httpCode = retryResp.code
                            contentType = retryResp.header("Content-Type", "") ?: ""
                            if (retryResp.isSuccessful) {
                                tileBytes = retryResp.body?.bytes()
                            }
                        }
                    } else if (response.isSuccessful) {
                        tileBytes = response.body?.bytes()
                    }
                }
            } catch (e: Exception) {
                Log.e("RainRadarTile", "Network error loading tile [X=$x, Y=$y, Z=$zoom]: ${e.localizedMessage}")
            }

            Log.d("RainRadarTile", "[HTTP Response] Code: $httpCode | Content-Type: $contentType | Tile X=$x, Y=$y, Z=$zoom")

            if (contentType.contains("text/html", ignoreCase = true) || (!contentType.contains("image", ignoreCase = true) && contentType.isNotBlank())) {
                Log.e("RainRadarTile", "Tile decode failed: Invalid Content-Type '$contentType' (HTML or non-image response) [X=$x, Y=$y, Z=$zoom]")
                return null
            }

            if (httpCode != 200 || tileBytes == null || tileBytes!!.isEmpty()) {
                Log.e("RainRadarTile", "Tile decode failed: HTTP status $httpCode or empty bytes [X=$x, Y=$y, Z=$zoom]")
                return null
            }

            return try {
                val bitmap = BitmapFactory.decodeByteArray(tileBytes, 0, tileBytes!!.size)
                if (bitmap != null) {
                    Log.d("RainRadarTile", "Tile decode success: ${bitmap.width}x${bitmap.height} bitmap [X=$x, Y=$y, Z=$zoom]")
                    BitmapDrawable(null, bitmap)
                } else {
                    Log.e("RainRadarTile", "Tile decode failed: BitmapFactory returned null [X=$x, Y=$y, Z=$zoom]")
                    null
                }
            } catch (e: Exception) {
                Log.e("RainRadarTile", "Tile decode failure exception: ${e.localizedMessage} [X=$x, Y=$y, Z=$zoom]")
                null
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
    override fun getMinimumZoomLevel(): Int = 1
    override fun getMaximumZoomLevel(): Int = 18
    override fun setTileSource(pTileSource: ITileSource?) {}

    override fun getTileLoader(): TileLoader {
        return CloudTileLoader()
    }

    private inner class CloudTileLoader : MapTileModuleProviderBase.TileLoader() {
        override fun loadTile(pMapTileIndex: Long): Drawable? {
            val zoom = MapTileIndex.getZoom(pMapTileIndex).coerceIn(1, 18)
            val x = MapTileIndex.getX(pMapTileIndex)
            val y = MapTileIndex.getY(pMapTileIndex)

            val url = "https://tile.openweathermap.org/map/clouds_new/$zoom/$x/$y.png?appid=$owmApiKey"

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
                Log.e("CloudTile", "Network error loading cloud tile [X=$x, Y=$y, Z=$zoom]: ${e.localizedMessage}")
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
                            // Original OWM clouds_new pixels are white/light-gray (RGB ~240-255).
                            // We boost opacity and map RGB to rich dark storm slate / charcoal gray for high contrast.
                            val alphaBoost = (a * 2.2f).coerceAtMost(255f).toInt()
                            val density = a / 255f

                            // Dark storm slate color mapping:
                            val red = (55 - density * 35).toInt().coerceIn(15, 255)
                            val green = (65 - density * 35).toInt().coerceIn(20, 255)
                            val blue = (85 - density * 40).toInt().coerceIn(30, 255)

                            pixels[i] = Color.argb(alphaBoost, red, green, blue)
                        }
                    }

                    val darkCloudBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    darkCloudBitmap.setPixels(pixels, 0, width, 0, 0, width, height)
                    Log.d("CloudTile", "Dark cloud tile generated ($width x $height, nonZero=$nonZero) [X=$x, Y=$y, Z=$zoom]")
                    BitmapDrawable(null, darkCloudBitmap)
                } else {
                    null
                }
            } catch (e: Exception) {
                Log.e("CloudTile", "Cloud tile decode exception [X=$x, Y=$y, Z=$zoom]: ${e.localizedMessage}")
                null
            }
        }
    }
}

