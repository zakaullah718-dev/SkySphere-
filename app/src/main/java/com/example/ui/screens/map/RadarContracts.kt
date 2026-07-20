package com.example.ui.screens.map

import android.content.Context
import android.view.View
import com.example.data.models.CityWeather
import com.example.data.models.WeatherDetails

/**
 * Interface contract representing the Weather Map engine.
 * Allows switching the map provider (e.g., MapLibre, Google Maps, Mapbox, OSM)
 * without rewriting the Compose UI.
 */
interface RadarMapProvider {
    /**
     * Initializes and returns the map view component.
     */
    fun createMapView(
        context: Context,
        onMapLoaded: () -> Unit,
        onCoordinatesSelected: (Double, Double) -> Unit,
        onApiKeyMissing: (String) -> Unit
    ): View

    /**
     * Centers the map view on a specific lat/lon coordinate.
     */
    fun setCenter(latitude: Double, longitude: Double, zoom: Float? = null)

    /**
     * Updates the active weather radar/satellite tile overlay on the map.
     */
    fun setWeatherLayer(layer: MapLayer)

    /**
     * Securely injects the environment API key to fetch premium layers.
     */
    fun setWeatherApiKey(apiKey: String)
}

/**
 * Interface contract representing the active weather radar/satellite tile provider.
 * Allows swapping the satellite tiles provider (e.g. OpenWeatherMap, RainViewer, Aeris)
 * without impacting the map engine.
 */
interface WeatherTileProvider {
    /**
     * Returns the name of the tile provider (for displaying credit/branding in the UI).
     */
    val providerName: String

    /**
     * Generates a fully qualified tile URL for standard coordinate layers.
     */
    fun getTileUrl(layer: MapLayer, apiKey: String): String
}
