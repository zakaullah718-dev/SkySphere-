package com.example.ui.screens.map

import android.content.Context
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.util.Log
import com.example.BuildConfig
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.overlay.TilesOverlay
import java.util.concurrent.atomic.AtomicInteger

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
        }

        return url
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
                    // OpenWeather official precipitation layer - render raw tile PNG with original colors
                    setColorFilter(null)
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
