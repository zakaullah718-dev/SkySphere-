package com.example.ui.screens.map

import android.content.Context
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
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
    val minSupportedZoom: Int = 1,
    val maxSupportedZoom: Int = 18,
    private val client: OkHttpClient,
    private val tileUrlProvider: (zoom: Int, x: Int, y: Int) -> String
) : OnlineTileSourceBase(
    sourceName,
    minSupportedZoom,
    maxSupportedZoom,
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
                    if (response.isSuccessful) {
                        Log.d("WeatherRadar", "Tile loaded successfully (HTTP $code) for layer '${layer.displayName}'")
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

                    val radar = root.optJSONObject("radar")
                    val pastRadar = radar?.optJSONArray("past")
                    if (pastRadar != null && pastRadar.length() > 0) {
                        val latestItem = pastRadar.getJSONObject(pastRadar.length() - 1)
                        val path = latestItem.optString("path")
                        if (path.isNotBlank()) {
                            cachedRadarPath = path
                        }
                    }

                    Log.d("WeatherLayerManager", "RainViewer path updated -> Radar: '$cachedRadarPath'")
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

        val owmApiKey = try {
            val key = BuildConfig.WEATHER_API_KEY
            if (!key.isNullOrBlank() && key != "PLACEholder_WEATHER_API_KEY") key else "f0308472599cabe4521d65850bb6ba22"
        } catch (e: Exception) {
            "f0308472599cabe4521d65850bb6ba22"
        }

        val currentRadarPath = cachedRadarPath ?: "/v2/radar/4493c4cc5308"
        val uniqueSourceName = "OWM_Weather_${layer.name}_${System.currentTimeMillis()}"

        val minZoom = 1
        val maxZoom = if (layer == MapWeatherLayer.RAIN_RADAR) 12 else 18

        val tileSource = WeatherTileSource(
            layer = layer,
            sourceName = uniqueSourceName,
            minSupportedZoom = minZoom,
            maxSupportedZoom = maxZoom,
            client = client
        ) { zoom, x, y ->
            when (layer) {
                MapWeatherLayer.RAIN_RADAR -> {
                    // RainViewer color scheme 4 (Dark Sky precipitation palette: Light blue -> Blue -> Yellow -> Orange -> Red)
                    // Smooth 0 (sharp boundaries) and Snow 1 (cyan snow)
                    "https://tilecache.rainviewer.com$currentRadarPath/256/$zoom/$x/$y/4/0_1.png"
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
                    // OpenWeatherMap precipitation & moisture layer for global humidity + precipitation distribution
                    "https://tile.openweathermap.org/map/precipitation_new/$zoom/$x/$y.png?appid=$owmApiKey"
                }
                MapWeatherLayer.NONE -> ""
            }
        }

        val provider = MapTileProviderBasic(context, tileSource)

        return TilesOverlay(provider, context).apply {
            loadingBackgroundColor = Color.TRANSPARENT
            loadingLineColor = Color.TRANSPARENT

            // Apply targeted color filters for maximum contrast and layer-specific visibility
            when (layer) {
                MapWeatherLayer.CLOUDS -> {
                    // Low clouds -> light gray, High/Dense clouds -> dark/near-black translucent gray with high opacity
                    val cloudMatrix = ColorMatrix(floatArrayOf(
                        0.25f, 0f,    0f,    0f, 30f,
                        0f,    0.25f, 0f,    0f, 30f,
                        0f,    0f,    0.25f, 0f, 35f,
                        0f,    0f,    0f,    1.9f, 0f
                    ))
                    setColorFilter(ColorMatrixColorFilter(cloudMatrix))
                }
                MapWeatherLayer.RAIN_RADAR -> {
                    // Crisp precipitation radar palette with boosted saturation and contrast (light blue -> blue -> yellow -> orange -> red)
                    val rainMatrix = ColorMatrix(floatArrayOf(
                        1.25f, 0f,    0f,    0f, 0f,
                        0f,    1.25f, 0f,    0f, 0f,
                        0f,    0f,    1.35f, 0f, 0f,
                        0f,    0f,    0f,    1.4f, 0f
                    ))
                    setColorFilter(ColorMatrixColorFilter(rainMatrix))
                }
                MapWeatherLayer.HUMIDITY -> {
                    // Saturate moisture / humidity blue/teal channels and boost visibility
                    val humidityMatrix = ColorMatrix(floatArrayOf(
                        1.1f, 0f,   0f,   0f, 0f,
                        0f,   1.3f, 0f,   0f, 10f,
                        0f,   0f,   1.5f, 0f, 20f,
                        0f,   0f,   0f,   1.6f, 0f
                    ))
                    setColorFilter(ColorMatrixColorFilter(humidityMatrix))
                }
                MapWeatherLayer.TEMPERATURE -> {
                    // Rich temperature contrast: cold (blue) vs cool (cyan) vs warm (yellow) vs hot (orange/red)
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



