package com.example.ui.screens.map

import android.content.Context
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

data class WeatherLayerRuntimeInfo(
    val layer: MapWeatherLayer = MapWeatherLayer.NONE,
    val providerName: String = "",
    val tileUrlSample: String = "",
    val httpStatus: String = "Initializing...",
    val isRainViewer: Boolean = false
)

class WeatherTileSource(
    val layer: MapWeatherLayer,
    sourceName: String,
    val minSupportedZoom: Int = 1,
    val maxSupportedZoom: Int = 18,
    private val client: OkHttpClient,
    private val onTileRequested: (url: String, httpStatus: String) -> Unit,
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
        val x = when {
            diff > 0 -> rawX shr diff
            diff < 0 -> rawX shl (-diff)
            else -> rawX
        }
        val y = when {
            diff > 0 -> rawY shr diff
            diff < 0 -> rawY shl (-diff)
            else -> rawY
        }

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
                    if (response.isSuccessful) {
                        onTileRequested(url, "OK")
                    } else {
                        Log.w("WeatherRadar", "Tile request returned HTTP ${response.code} (handled silently)")
                        onTileRequested(url, "HTTP ${response.code}")
                    }
                }
            } catch (e: Exception) {
                Log.w("WeatherRadar", "Tile request network exception: ${e.localizedMessage}")
                onTileRequested(url, "Network Exception")
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

    private val _runtimeInfo = MutableStateFlow(WeatherLayerRuntimeInfo())
    val runtimeInfo: StateFlow<WeatherLayerRuntimeInfo> = _runtimeInfo.asStateFlow()

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
                            Log.d("WeatherRadar", "RainViewer live radar path acquired: '$path'")
                        }
                    }
                    return@withContext true
                }
            } else {
                Log.e("WeatherRadar", "RainViewer API path request failed with HTTP ${response.code}")
            }
        } catch (e: Exception) {
            Log.e("WeatherRadar", "RainViewer API path request exception: ${e.localizedMessage}")
        }
        return@withContext false
    }

    fun createTilesOverlay(
        context: Context,
        layer: MapWeatherLayer,
        radarTimestamp: Long?
    ): TilesOverlay? {
        if (layer == MapWeatherLayer.NONE) {
            _runtimeInfo.value = WeatherLayerRuntimeInfo(layer = MapWeatherLayer.NONE)
            return null
        }

        val owmApiKey = try {
            val key = BuildConfig.WEATHER_API_KEY
            if (!key.isNullOrBlank() && key != "PLACEholder_WEATHER_API_KEY") key else "f0308472599cabe4521d65850bb6ba22"
        } catch (e: Exception) {
            "f0308472599cabe4521d65850bb6ba22"
        }

        val timeBucket = System.currentTimeMillis() / 300000 // 5-minute cache/refresh window
        val currentRadarPath = cachedRadarPath ?: "/v2/radar/79c9e619266e"
        
        val uniqueSourceName = if (layer == MapWeatherLayer.RAIN_RADAR) {
            "RainViewer_Radar_${currentRadarPath.replace("/", "_")}"
        } else {
            "OWM_Weather_${layer.name}_$timeBucket"
        }
        
        val minZoom = 1
        val maxZoom = if (layer == MapWeatherLayer.RAIN_RADAR) 12 else 18
        val providerName = if (layer == MapWeatherLayer.RAIN_RADAR) "RainViewer Real-Time Doppler Radar" else "OpenWeatherMap Overlay"

        _runtimeInfo.value = WeatherLayerRuntimeInfo(
            layer = layer,
            providerName = providerName,
            tileUrlSample = if (layer == MapWeatherLayer.RAIN_RADAR) "https://tilecache.rainviewer.com$currentRadarPath/256/{z}/{x}/{y}/2/0_1.png" else "https://tile.openweathermap.org/map/${layer.name}/...",
            httpStatus = "Requesting live tiles...",
            isRainViewer = (layer == MapWeatherLayer.RAIN_RADAR)
        )

        val tileSource = WeatherTileSource(
            layer = layer,
            sourceName = uniqueSourceName,
            minSupportedZoom = minZoom,
            maxSupportedZoom = maxZoom,
            client = client,
            onTileRequested = { url, httpStatus ->
                _runtimeInfo.value = _runtimeInfo.value.copy(
                    tileUrlSample = url,
                    httpStatus = httpStatus
                )
            }
        ) { zoom, x, y ->
            when (layer) {
                MapWeatherLayer.RAIN_RADAR -> {
                    "https://tilecache.rainviewer.com$currentRadarPath/256/$zoom/$x/$y/2/0_1.png"
                }
                MapWeatherLayer.CLOUDS -> {
                    "https://tile.openweathermap.org/map/clouds_new/$zoom/$x/$y.png?appid=$owmApiKey&_t=$timeBucket"
                }
                MapWeatherLayer.TEMPERATURE -> {
                    "https://tile.openweathermap.org/map/temp_new/$zoom/$x/$y.png?appid=$owmApiKey&_t=$timeBucket"
                }
                MapWeatherLayer.WIND -> {
                    "https://tile.openweathermap.org/map/wind_new/$zoom/$x/$y.png?appid=$owmApiKey&_t=$timeBucket"
                }
                MapWeatherLayer.PRESSURE -> {
                    "https://tile.openweathermap.org/map/pressure_new/$zoom/$x/$y.png?appid=$owmApiKey&_t=$timeBucket"
                }
                MapWeatherLayer.HUMIDITY -> {
                    "https://tile.openweathermap.org/map/precipitation_new/$zoom/$x/$y.png?appid=$owmApiKey&_t=$timeBucket"
                }
                MapWeatherLayer.NONE -> ""
            }
        }

        val provider = MapTileProviderBasic(context, tileSource)

        return TilesOverlay(provider, context).apply {
            loadingBackgroundColor = Color.TRANSPARENT
            loadingLineColor = Color.TRANSPARENT

            when (layer) {
                MapWeatherLayer.CLOUDS -> {
                    val cloudMatrix = ColorMatrix(floatArrayOf(
                        0.25f, 0f,    0f,    0f, 30f,
                        0f,    0.25f, 0f,    0f, 30f,
                        0f,    0f,    0.25f, 0f, 35f,
                        0f,    0f,    0f,    1.9f, 0f
                    ))
                    setColorFilter(ColorMatrixColorFilter(cloudMatrix))
                }
                MapWeatherLayer.RAIN_RADAR -> {
                    // Raw, unmodified Doppler NEXRAD radar colors directly from RainViewer tiles
                    setColorFilter(null)
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




