package com.example.ui.screens.map

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.overlay.TilesOverlay
import java.util.concurrent.TimeUnit

class WeatherTileSource(
    sourceName: String,
    minZoom: Int = 1,
    maxZoom: Int = 12,
    private val tileUrlProvider: (zoom: Int, x: Int, y: Int) -> String
) : OnlineTileSourceBase(
    sourceName,
    minZoom,
    maxZoom,
    256,
    ".png",
    arrayOf()
) {
    private val maxAllowedZoom = maxZoom

    override fun getTileURLString(pMapTileIndex: Long): String {
        val rawZoom = MapTileIndex.getZoom(pMapTileIndex)
        val rawX = MapTileIndex.getX(pMapTileIndex)
        val rawY = MapTileIndex.getY(pMapTileIndex)

        val zoom: Int
        val x: Int
        val y: Int

        if (rawZoom > maxAllowedZoom) {
            zoom = maxAllowedZoom
            val diff = rawZoom - maxAllowedZoom
            x = rawX shr diff
            y = rawY shr diff
        } else {
            zoom = rawZoom
            x = rawX
            y = rawY
        }

        return tileUrlProvider(zoom, x, y)
    }
}

class FutureWeatherLayerManager {

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private var cachedRadarTimestamp: Long? = null
    private var cachedSatelliteTimestamp: Long? = null

    suspend fun fetchLatestRadarTimestamp(): Long? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://api.rainviewer.com/public/weather-maps.json")
                .build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val jsonStr = response.body?.string()
                if (!jsonStr.isNullOrEmpty()) {
                    val root = JSONObject(jsonStr)
                    
                    // Radar timestamp
                    val radar = root.optJSONObject("radar")
                    val pastRadar = radar?.optJSONArray("past")
                    if (pastRadar != null && pastRadar.length() > 0) {
                        val latestItem = pastRadar.getJSONObject(pastRadar.length() - 1)
                        val timestamp = latestItem.optLong("time")
                        if (timestamp > 0) {
                            cachedRadarTimestamp = timestamp
                        }
                    }

                    // Satellite timestamp for cloud coverage
                    val satellite = root.optJSONObject("satellite")
                    val infrared = satellite?.optJSONArray("infrared")
                    if (infrared != null && infrared.length() > 0) {
                        val latestSat = infrared.getJSONObject(infrared.length() - 1)
                        val satTime = latestSat.optLong("time")
                        if (satTime > 0) {
                            cachedSatelliteTimestamp = satTime
                        }
                    }

                    if (cachedRadarTimestamp != null) {
                        return@withContext cachedRadarTimestamp
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("WeatherLayerManager", "Error fetching RainViewer timestamps: ${e.localizedMessage}")
        }
        return@withContext cachedRadarTimestamp ?: (System.currentTimeMillis() / 1000 - 600)
    }

    fun createTilesOverlay(
        context: Context,
        layer: MapWeatherLayer,
        radarTimestamp: Long?
    ): TilesOverlay? {
        if (layer == MapWeatherLayer.NONE) return null

        val owmApiKey = "b1b15e88fa797225412429c1c50c122a"
        val ts = radarTimestamp ?: cachedRadarTimestamp ?: (System.currentTimeMillis() / 1000 - 600)
        val satTs = cachedSatelliteTimestamp ?: ts

        val maxZoomForLayer = when (layer) {
            MapWeatherLayer.RAIN_RADAR -> 12
            MapWeatherLayer.CLOUDS -> 12
            else -> 12
        }

        val tileSource = WeatherTileSource(
            sourceName = layer.name,
            minZoom = 1,
            maxZoom = maxZoomForLayer
        ) { zoom, x, y ->
            when (layer) {
                MapWeatherLayer.RAIN_RADAR -> {
                    "https://tilecache.rainviewer.com/v2/radar/$ts/256/$zoom/$x/$y/2/1_1.png"
                }
                MapWeatherLayer.CLOUDS -> {
                    // High performance satellite infrared cloud layer from RainViewer or OWM
                    "https://tilecache.rainviewer.com/v2/satellite/$satTs/256/$zoom/$x/$y/0/0_0.png"
                }
                MapWeatherLayer.TEMPERATURE -> {
                    "https://tile.openweathermap.org/map/temp_new/$zoom/$x/$y.png?appid=$owmApiKey"
                }
                MapWeatherLayer.WIND -> {
                    "https://tile.openweathermap.org/map/wind_new/$zoom/$x/$y.png?appid=$owmApiKey"
                }
                MapWeatherLayer.PRESSURE -> {
                    "https://tile.openweathermap.org/map/pressure_new/$zoom/$x/$y.png?appid=$owmApiKey"
                }
                MapWeatherLayer.HUMIDITY -> {
                    "https://tile.openweathermap.org/map/precipitation_new/$zoom/$x/$y.png?appid=$owmApiKey"
                }
                MapWeatherLayer.NONE -> ""
            }
        }

        val provider = MapTileProviderBasic(context, tileSource)
        return TilesOverlay(provider, context).apply {
            loadingBackgroundColor = android.graphics.Color.TRANSPARENT
            loadingLineColor = android.graphics.Color.TRANSPARENT
        }
    }
}
