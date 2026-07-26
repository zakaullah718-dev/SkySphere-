package com.example.ui.screens.map

enum class MapWeatherLayer(
    val displayName: String,
    val description: String,
    val minZoom: Double = 1.0,
    val maxZoom: Double = 20.0
) {
    NONE("None", "Standard clean map", 1.0, 20.0),
    RAIN_RADAR("Rain Radar", "Real-time precipitation radar", 1.0, 20.0),
    CLOUDS("Cloud Coverage", "Satellite cloud density overlay", 1.0, 20.0),
    TEMPERATURE("Temperature", "Thermal color gradient overlay", 1.0, 20.0),
    WIND("Wind Speed", "Atmospheric wind streamlines", 1.0, 20.0),
    PRESSURE("Air Pressure", "Isobaric surface pressure overlay", 1.0, 20.0)
}
