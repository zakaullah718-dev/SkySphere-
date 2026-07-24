package com.example.ui.screens.map

import android.content.Context
import android.util.Log
import com.example.BuildConfig
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

        val url = tileUrlProvider(zoom, x, y)
        if (url.isNotBlank()) {
            Log.d(
                "WeatherTileSource",
                "HTTP Request -> Layer: '${name()}', Zoom: $zoom, TileCoords: ($x, $y), URL: $url"
            )
        }
        return url
    }
}

class FutureWeatherLayerManager {

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private var cachedRadarPath: String? = null
    private var cachedSatellitePath: String? = null

    suspend fun fetchLatestRadarTimestamp(): Long? = withContext(Dispatchers.IO) {
        fetchLatestWeatherMapPaths()
        return@withContext System.currentTimeMillis() / 1000
    }

    suspend fun fetchLatestWeatherMapPaths(): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://api.rainviewer.com/public/weather-maps.json")
                .header("User-Agent", "SkySphere/1.0")
                .build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val jsonStr = response.body?.string()
                if (!jsonStr.isNullOrEmpty()) {
                    val root = JSONObject(jsonStr)

                    // Extract latest past radar path
                    val radar = root.optJSONObject("radar")
                    val pastRadar = radar?.optJSONArray("past")
                    if (pastRadar != null && pastRadar.length() > 0) {
                        val latestItem = pastRadar.getJSONObject(pastRadar.length() - 1)
                        val path = latestItem.optString("path")
                        if (path.isNotBlank()) {
                            cachedRadarPath = path
                        }
                    }

                    // Extract latest satellite path
                    val satellite = root.optJSONObject("satellite")
                    val infrared = satellite?.optJSONArray("infrared")
                    if (infrared != null && infrared.length() > 0) {
                        val latestSat = infrared.getJSONObject(infrared.length() - 1)
                        val path = latestSat.optString("path")
                        if (path.isNotBlank()) {
                            cachedSatellitePath = path
                        }
                    }

                    Log.d("WeatherLayerManager", "RainViewer paths updated -> Radar: '$cachedRadarPath', Satellite: '$cachedSatellitePath'")
                    return@withContext true
                }
            } else {
                Log.e("WeatherLayerManager", "Failed to fetch RainViewer map paths HTTP ${response.code}")
            }
        } catch (e: Exception) {
            Log.e("WeatherLayerManager", "Error fetching RainViewer map paths: ${e.localizedMessage}")
        }
        return@withContext false
    }

    fun createTilesOverlay(
        context: Context,
        layer: MapWeatherLayer,
        radarTimestamp: Long?
    ): TilesOverlay? {
        if (layer == MapWeatherLayer.NONE) return null

        val owmApiKey = try { BuildConfig.WEATHER_API_KEY } catch (e: Exception) { "" }
        val hasOwmKey = owmApiKey.isNotBlank() && owmApiKey != "PLACEholder_WEATHER_API_KEY"

        val currentRadarPath = cachedRadarPath ?: "/v2/radar/4493c4cc5308"

        val tileSource = WeatherTileSource(
            sourceName = layer.displayName,
            minZoom = 1,
            maxZoom = 12
        ) { zoom, x, y ->
            when (layer) {
                MapWeatherLayer.RAIN_RADAR -> {
                    "https://tilecache.rainviewer.com$currentRadarPath/256/$zoom/$x/$y/2/1_1.png"
                }
                MapWeatherLayer.CLOUDS -> {
                    if (cachedSatellitePath != null) {
                        "https://tilecache.rainviewer.com$cachedSatellitePath/256/$zoom/$x/$y/0/0_1.png"
                    } else {
                        "https://mesonet.agron.iastate.edu/cache/tile.py/1.0.0/goes-east-ir-4km-900913/$zoom/$x/$y.png"
                    }
                }
                MapWeatherLayer.TEMPERATURE -> {
                    if (hasOwmKey) {
                        "https://tile.openweathermap.org/map/temp_new/$zoom/$x/$y.png?appid=$owmApiKey"
                    } else {
                        "https://tilecache.rainviewer.com$currentRadarPath/256/$zoom/$x/$y/6/1_1.png"
                    }
                }
                MapWeatherLayer.WIND -> {
                    if (hasOwmKey) {
                        "https://tile.openweathermap.org/map/wind_new/$zoom/$x/$y.png?appid=$owmApiKey"
                    } else {
                        "https://tilecache.rainviewer.com$currentRadarPath/256/$zoom/$x/$y/3/1_1.png"
                    }
                }
                MapWeatherLayer.PRESSURE -> {
                    if (hasOwmKey) {
                        "https://tile.openweathermap.org/map/pressure_new/$zoom/$x/$y.png?appid=$owmApiKey"
                    } else {
                        "https://tilecache.rainviewer.com$currentRadarPath/256/$zoom/$x/$y/8/1_1.png"
                    }
                }
                MapWeatherLayer.HUMIDITY -> {
                    if (hasOwmKey) {
                        "https://tile.openweathermap.org/map/precipitation_new/$zoom/$x/$y.png?appid=$owmApiKey"
                    } else {
                        "https://mesonet.agron.iastate.edu/cache/tile.py/1.0.0/q2-n1p-900913/$zoom/$x/$y.png"
                    }
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

