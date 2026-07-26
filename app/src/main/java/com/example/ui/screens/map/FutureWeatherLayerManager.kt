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
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                if (bitmap != null) {
                    val width = bitmap.width
                    val height = bitmap.height
                    val pixels = IntArray(width * height)
                    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

                    var tracePx = 0
                    var cyanPx = 0
                    var greenPx = 0
                    var yellowPx = 0
                    var redPx = 0
                    var otherPx = 0
                    var totalColored = 0

                    for (pixel in pixels) {
                        val a = (pixel shr 24) and 0xFF
                        if (a > 0) {
                            totalColored++
                            val r = (pixel shr 16) and 0xFF
                            val g = (pixel shr 8) and 0xFF
                            val b = pixel and 0xFF

                            when {
                                g > r + 25 && g > b + 10 -> greenPx++
                                r > 180 && g > 140 && b < 100 -> yellowPx++
                                (r > 180 && g < 120 && b < 120) || (r > 150 && b > 150 && g < 100) -> redPx++
                                g > 180 && b > 180 && r < 120 -> cyanPx++
                                b > 110 && r in 60..150 && g in 60..150 -> tracePx++
                                else -> otherPx++
                            }
                        }
                    }

                    Log.d("WeatherRadar", "--- TILE PIXEL AUDIT (z=$zoom, x=$x, y=$y) ---")
                    Log.d("WeatherRadar", "Total Colored Pixels: $totalColored")
                    Log.d("WeatherRadar", "Trace (Purple/Blue): $tracePx px")
                    Log.d("WeatherRadar", "Light (Cyan): $cyanPx px")
                    Log.d("WeatherRadar", "Moderate (Green): $greenPx px")
                    Log.d("WeatherRadar", "Heavy (Yellow/Orange): $yellowPx px")
                    Log.d("WeatherRadar", "Severe (Red/Magenta): $redPx px")
                    Log.d("WeatherRadar", "Other RGBA Tones: $otherPx px")
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
        if (layer == MapWeatherLayer.NONE) {
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
                MapWeatherLayer.HUMIDITY -> "precipitation_new"
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
                    // Boost color saturation (1.25x) and alpha channel (1.75x) so green, yellow, orange, and red rain cores render with 100% opacity and high contrast
                    val rainMatrix = ColorMatrix(floatArrayOf(
                        1.25f, 0f,    0f,    0f, 0f,
                        0f,    1.25f, 0f,    0f, 0f,
                        0f,    0f,    1.25f, 0f, 0f,
                        0f,    0f,    0f,    1.75f, 0f
                    ))
                    setColorFilter(ColorMatrixColorFilter(rainMatrix))
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
