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
    private val tileUrlProvider: (zoom: Int, x: Int, y: Int) -> String
) : OnlineTileSourceBase(
    sourceName,
    1,
    18,
    256,
    ".png",
    arrayOf()
) {
    override fun getTileURLString(pMapTileIndex: Long): String {
        val zoom = MapTileIndex.getZoom(pMapTileIndex)
        val x = MapTileIndex.getX(pMapTileIndex)
        val y = MapTileIndex.getY(pMapTileIndex)
        return tileUrlProvider(zoom, x, y)
    }
}

class FutureWeatherLayerManager {

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private var cachedRadarTimestamp: Long? = null

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
                    val radar = root.optJSONObject("radar")
                    val past = radar?.optJSONArray("past")
                    if (past != null && past.length() > 0) {
                        val latestItem = past.getJSONObject(past.length() - 1)
                        val timestamp = latestItem.optLong("time")
                        if (timestamp > 0) {
                            cachedRadarTimestamp = timestamp
                            return@withContext timestamp
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("WeatherLayerManager", "Error fetching RainViewer radar timestamp: ${e.localizedMessage}")
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

        val tileSource = WeatherTileSource(layer.name) { zoom, x, y ->
            when (layer) {
                MapWeatherLayer.RAIN_RADAR -> {
                    "https://tilecache.rainviewer.com/v2/radar/$ts/256/$zoom/$x/$y/2/1_1.png"
                }
                MapWeatherLayer.CLOUDS -> {
                    "https://tile.openweathermap.org/map/clouds_new/$zoom/$x/$y.png?appid=$owmApiKey"
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
