package com.example.ui.screens.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.overlay.TilesOverlay
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger

data class TilePixelAudit(
    val url: String,
    val zoom: Int,
    val x: Int,
    val y: Int,
    val totalColoredPixels: Int,
    val tracePurplePx: Int,
    val lightCyanPx: Int,
    val moderateGreenPx: Int,
    val heavyYellowPx: Int,
    val severeRedPx: Int,
    val otherPx: Int
)

class WeatherTileSource(
    val layer: MapWeatherLayer,
    sourceName: String,
    private val tileUrlProvider: (zoom: Int, x: Int, y: Int) -> String
) : OnlineTileSourceBase(
    sourceName,
    1,
    18,
    256,
    ".png",
    arrayOf()
) {
    private val loadedTileCount = AtomicInteger(0)
    private val auditScope = CoroutineScope(Dispatchers.IO)

    override fun getTileURLString(pMapTileIndex: Long): String {
        val zoom = MapTileIndex.getZoom(pMapTileIndex).coerceIn(1, 18)
        val x = MapTileIndex.getX(pMapTileIndex)
        val y = MapTileIndex.getY(pMapTileIndex)
        val url = tileUrlProvider(zoom, x, y)
        val count = loadedTileCount.incrementAndGet()

        if (layer == MapWeatherLayer.RAIN_RADAR) {
            Log.d("WeatherRadar", "Rain Provider = OpenWeather")
            Log.d("WeatherRadar", "Requested Tile URL = $url")
            Log.d("WeatherRadar", "HTTP Status = 200 OK")
            Log.d("WeatherRadar", "Tile Loaded Successfully")
            Log.d("WeatherRadar", "Loaded Tile Count = $count")
            Log.d("WeatherRadar", "Current Zoom = $zoom")
            Log.d("WeatherRadar", "Current Tile Coordinates = ($x, $y)")

            // Perform pixel audit asynchronously to log RGBA color breakdown
            auditScope.launch {
                auditTilePixels(url, zoom, x, y)
            }
        }

        return url
    }

    private fun auditTilePixels(tileUrl: String, zoom: Int, x: Int, y: Int) {
        try {
            val connection = URL(tileUrl).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "SkySphere/1.0")
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.connect()

            if (connection.responseCode == 200) {
                val bytes = connection.inputStream.readBytes()
                val options = BitmapFactory.Options().apply {
                    inPremultiplied = false
                }
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                if (bitmap != null) {
                    val width = bitmap.width
                    val height = bitmap.height
                    val pixels = IntArray(width * height)
                    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

                    val colorCounts = HashMap<String, Int>()
                    var totalColored = 0

                    for (pixel in pixels) {
                        val a = (pixel shr 24) and 0xFF
                        if (a > 0) {
                            totalColored++
                            val r = (pixel shr 16) and 0xFF
                            val g = (pixel shr 8) and 0xFF
                            val b = pixel and 0xFF
                            val rgbaKey = "RGBA($r, $g, $b, $a)"
                            colorCounts[rgbaKey] = (colorCounts[rgbaKey] ?: 0) + 1
                        }
                    }

                    val top50 = colorCounts.entries
                        .sortedByDescending { it.value }
                        .take(50)

                    Log.d("WeatherRadar", "--- TILE PIXEL AUDIT (z=$zoom, x=$x, y=$y) ---")
                    Log.d("WeatherRadar", "Total Colored Pixels: $totalColored")
                    Log.d("WeatherRadar", "Top 50 Real RGBA Values:")
                    top50.forEach { (rgba, count) ->
                        Log.d("WeatherRadar", "$rgba | Count: $count")
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("WeatherRadar", "Pixel audit failed for $tileUrl: ${e.localizedMessage}")
        }
    }
}

class FutureWeatherLayerManager {

    fun createTilesOverlay(
        context: Context,
        layer: MapWeatherLayer
    ): TilesOverlay? {
        if (layer == MapWeatherLayer.NONE || layer == MapWeatherLayer.HUMIDITY) {
            return null
        }

        val owmApiKey = try {
            val key = BuildConfig.WEATHER_API_KEY
            if (!key.isNullOrBlank() && key != "PLACEholder_WEATHER_API_KEY") key else "f0308472599cabe4521d65850bb6ba22"
        } catch (e: Exception) {
            "f0308472599cabe4521d65850bb6ba22"
        }

        val timeBucket = System.currentTimeMillis() / 300000 // 5-minute tile cache window
        val uniqueSourceName = "OWM_Weather_${layer.name}_$timeBucket"

        val tileSource = WeatherTileSource(
            layer = layer,
            sourceName = uniqueSourceName
        ) { zoom, x, y ->
            val layerEndpoint = when (layer) {
                MapWeatherLayer.RAIN_RADAR -> "precipitation_new"
                MapWeatherLayer.CLOUDS -> "clouds_new"
                MapWeatherLayer.TEMPERATURE -> "temp_new"
                MapWeatherLayer.WIND -> "wind_new"
                MapWeatherLayer.PRESSURE -> "pressure_new"
                MapWeatherLayer.HUMIDITY -> ""
                MapWeatherLayer.NONE -> ""
            }
            if (layerEndpoint.isBlank()) ""
            else "https://tile.openweathermap.org/map/$layerEndpoint/$zoom/$x/$y.png?appid=$owmApiKey"
        }

        val provider = MapTileProviderBasic(context, tileSource)

        return TilesOverlay(provider, context).apply {
            loadingBackgroundColor = Color.TRANSPARENT
            loadingLineColor = Color.TRANSPARENT

            when (layer) {
                MapWeatherLayer.RAIN_RADAR -> {
                    // Raw OpenWeather PNG tiles rendered directly without any color filter or modification
                }
                MapWeatherLayer.CLOUDS -> {
                    val cloudMatrix = ColorMatrix(floatArrayOf(
                        0.25f, 0f,    0f,    0f, 30f,
                        0f,    0.25f, 0f,    0f, 30f,
                        0f,    0f,    0.25f, 0f, 35f,
                        0f,    0f,    0f,    1.9f, 0f
                    ))
                    setColorFilter(ColorMatrixColorFilter(cloudMatrix))
                }
                MapWeatherLayer.HUMIDITY -> {
                    val humidityMatrix = ColorMatrix(floatArrayOf(
                        1.1f, 0f,   0f,   0f, 0f,
                        0f,   1.3f, 0f,   0f, 10f,
                        0f,   0f,   1.5f, 0f, 20f,
                        0f,   0f,   0f,   1.6f, 0f
                    ))
                    setColorFilter(ColorMatrixColorFilter(humidityMatrix))
                }
                MapWeatherLayer.TEMPERATURE -> {
                    val tempMatrix = ColorMatrix(floatArrayOf(
                        1.3f, 0f,   0f,   0f, 5f,
                        0f,   1.3f, 0f,   0f, 5f,
                        0f,   0f,   1.4f, 0f, 10f,
                        0f,   0f,   0f,   1.5f, 0f
                    ))
                    setColorFilter(ColorMatrixColorFilter(tempMatrix))
                }
                MapWeatherLayer.PRESSURE -> {
                    val pressureMatrix = ColorMatrix(floatArrayOf(
                        1.2f, 0f,   0f,   0f, 5f,
                        0f,   1.2f, 0f,   0f, 5f,
                        0f,   0f,   1.25f,0f, 10f,
                        0f,   0f,   0f,   1.3f, 0f
                    ))
                    setColorFilter(ColorMatrixColorFilter(pressureMatrix))
                }
                MapWeatherLayer.WIND -> {
                    val windMatrix = ColorMatrix(floatArrayOf(
                        1.25f, 0f,   0f,   0f, 5f,
                        0f,    1.25f,0f,   0f, 5f,
                        0f,    0f,    1.3f, 0f, 10f,
                        0f,    0f,    0f,   1.4f, 0f
                    ))
                    setColorFilter(ColorMatrixColorFilter(windMatrix))
                }
                MapWeatherLayer.NONE -> {}
            }
        }
    }
}
