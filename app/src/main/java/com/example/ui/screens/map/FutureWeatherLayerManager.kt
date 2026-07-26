package com.example.ui.screens.map

import android.content.Context
import android.graphics.Color
import com.example.BuildConfig
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.overlay.TilesOverlay

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

        val owmApiKey = try {
            val key = BuildConfig.WEATHER_API_KEY
            if (!key.isNullOrBlank() && key != "PLACEholder_WEATHER_API_KEY") key else "f0308472599cabe4521d65850bb6ba22"
        } catch (e: Exception) {
            "f0308472599cabe4521d65850bb6ba22"
        }

        val timeBucket = System.currentTimeMillis() / 300000 // 5-minute tile cache window
        val sourceName = if (layer == MapWeatherLayer.RAIN_RADAR) "RADAR_${radarTimestamp}_$timeBucket" else "OWM_${layer.name}_$timeBucket"

        val layerEndpoint = when (layer) {
            MapWeatherLayer.RAIN_RADAR -> "precipitation_new"
            MapWeatherLayer.CLOUDS -> "clouds_new"
            MapWeatherLayer.TEMPERATURE -> "temp_new"
            MapWeatherLayer.WIND -> "wind_new"
            MapWeatherLayer.PRESSURE -> "pressure_new"
            MapWeatherLayer.NONE -> return null
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

                return if (layer == MapWeatherLayer.RAIN_RADAR) {
                    val ts = if (radarTimestamp > 0) radarTimestamp else radarRepository.getFallbackTimestamp()
                    "https://tilecache.rainviewer.com/v2/radar/$ts/256/$zoom/$x/$y/4/1_1.png"
                } else {
                    "https://tile.openweathermap.org/map/$layerEndpoint/$zoom/$x/$y.png?appid=$owmApiKey"
                }
            }
        }

        val provider = MapTileProviderBasic(context, tileSource)

        return TilesOverlay(provider, context).apply {
            loadingBackgroundColor = Color.TRANSPARENT
            loadingLineColor = Color.TRANSPARENT
        }
    }
}
