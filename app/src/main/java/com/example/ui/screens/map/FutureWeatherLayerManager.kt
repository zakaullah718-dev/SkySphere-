package com.example.ui.screens.map

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.overlay.TilesOverlay
import java.util.Collections
import java.util.concurrent.TimeUnit

class WeatherTileSource(
    val layer: MapWeatherLayer,
    sourceName: String,
    val minSupportedZoom: Int = 2,
    val maxSupportedZoom: Int = 12,
    private val client: OkHttpClient,
    private val tileUrlProvider: (zoom: Int, x: Int, y: Int) -> String
) : OnlineTileSourceBase(
    sourceName,
    1,
    22,
    256,
    ".png",
    arrayOf()
) {
    private val loggedUrls = Collections.synchronizedSet(HashSet<String>())

    override fun getTileURLString(pMapTileIndex: Long): String {
        val rawZoom = MapTileIndex.getZoom(pMapTileIndex)
        val rawX = MapTileIndex.getX(pMapTileIndex)
        val rawY = MapTileIndex.getY(pMapTileIndex)

        val zoom = rawZoom.coerceIn(minSupportedZoom, maxSupportedZoom)
        val diff = rawZoom - zoom
        val x = if (diff > 0) rawX shr diff else rawX shl (-diff)
        val y = if (diff > 0) rawY shr diff else rawY shl (-diff)

        val url = tileUrlProvider(zoom, x, y)
        if (url.isNotBlank()) {
            Log.d("WeatherRadar", "Selected Layer = ${layer.displayName}")
            Log.d("WeatherRadar", "Tile URL = $url")
            Log.d("WeatherRadar", "Tile request URL = $url")

            checkAndLogTileHttp(url)
        }
        return url
    }

    private fun checkAndLogTileHttp(url: String) {
        if (!loggedUrls.add(url)) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", "SkySphere/1.0")
                    .build()
                client.newCall(req).execute().use { response ->
                    val code = response.code
                    Log.d("WeatherRadar", "HTTP response code = $code")
                    if (response.isSuccessful) {
                        Log.d("WeatherRadar", "Tile loaded successfully for layer '${layer.displayName}'")
                    } else {
                        Log.e("WeatherRadar", "Tile failed (HTTP $code) for layer '${layer.displayName}'")
                    }
                }
            } catch (e: Exception) {
                Log.e("WeatherRadar", "Tile failed for '${layer.displayName}': ${e.localizedMessage}")
            }
        }
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

        // Unique source name per layer and instantiation to ensure dedicated tile cache & provider
        val uniqueSourceName = "OWM_Weather_${layer.name}_${System.currentTimeMillis()}"

        val tileSource = WeatherTileSource(
            layer = layer,
            sourceName = uniqueSourceName,
            minSupportedZoom = layer.minZoom.toInt(),
            maxSupportedZoom = layer.maxZoom.toInt(),
            client = client
        ) { zoom, x, y ->
            when (layer) {
                MapWeatherLayer.RAIN_RADAR -> {
                    "https://tilecache.rainviewer.com$currentRadarPath/256/$zoom/$x/$y/2/1_1.png"
                }
                MapWeatherLayer.CLOUDS -> {
                    if (hasOwmKey) {
                        "https://tile.openweathermap.org/map/clouds_new/$zoom/$x/$y.png?appid=$owmApiKey"
                    } else if (cachedSatellitePath != null) {
                        "https://tilecache.rainviewer.com$cachedSatellitePath/256/$zoom/$x/$y/0/0_1.png"
                    } else {
                        "https://mesonet.agron.iastate.edu/cache/tile.py/1.0.0/goes-east-ir-4km-900913/$zoom/$x/$y.png"
                    }
                }
                MapWeatherLayer.TEMPERATURE -> {
                    if (hasOwmKey) {
                        "https://tile.openweathermap.org/map/temp_new/$zoom/$x/$y.png?appid=$owmApiKey"
                    } else {
                        "https://mesonet.agron.iastate.edu/cache/tile.py/1.0.0/iatemp-900913/$zoom/$x/$y.png"
                    }
                }
                MapWeatherLayer.WIND -> {
                    if (hasOwmKey) {
                        "https://tile.openweathermap.org/map/wind_new/$zoom/$x/$y.png?appid=$owmApiKey"
                    } else {
                        "https://mesonet.agron.iastate.edu/cache/tile.py/1.0.0/iawind-900913/$zoom/$x/$y.png"
                    }
                }
                MapWeatherLayer.PRESSURE -> {
                    if (hasOwmKey) {
                        "https://tile.openweathermap.org/map/pressure_new/$zoom/$x/$y.png?appid=$owmApiKey"
                    } else {
                        "https://mesonet.agron.iastate.edu/cache/tile.py/1.0.0/isobar-900913/$zoom/$x/$y.png"
                    }
                }
                MapWeatherLayer.HUMIDITY -> {
                    // IEM moisture / precipitable water overlay yields valid 200 tiles for Humidity
                    "https://mesonet.agron.iastate.edu/cache/tile.py/1.0.0/q2-n1p-900913/$zoom/$x/$y.png"
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


