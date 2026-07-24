package com.example.ui.screens.map

enum class MapWeatherLayer(
    val displayName: String,
    val description: String
) {
    NONE("None", "Standard clean map"),
    RAIN_RADAR("Rain Radar", "Real-time precipitation radar"),
    CLOUDS("Cloud Coverage", "Satellite cloud density overlay"),
    TEMPERATURE("Temperature", "Thermal color gradient overlay"),
    WIND("Wind Speed", "Atmospheric wind streamlines"),
    PRESSURE("Air Pressure", "Isobaric surface pressure overlay"),
    HUMIDITY("Humidity & Rain", "Relative moisture & precipitation")
}
