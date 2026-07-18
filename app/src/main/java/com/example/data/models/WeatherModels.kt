package com.example.data.models

import androidx.compose.ui.graphics.Color

enum class WeatherCondition(
    val displayName: String,
    val iconName: String, // Material Icon identifier
    val description: String,
    val startColor: Color,
    val endColor: Color
) {
    SUNNY("Sunny", "WbSunny", "Clear sky conditions", Color(0xFFFFB300), Color(0xFFFF7043)),
    PARTLY_CLOUDY("Partly Cloudy", "CloudQueue", "Few passing clouds", Color(0xFF4FA7FF), Color(0xFF1E88E5)),
    CLOUDY("Cloudy", "Cloud", "Overcast sky", Color(0xFF78909C), Color(0xFF455A64)),
    RAINY("Rainy", "WaterDrop", "Showers and drizzling", Color(0xFF4FC3F7), Color(0xFF0288D1)),
    STORM("Storm", "Thunderstorm", "Heavy storm and thunder", Color(0xFF9575CD), Color(0xFF5E35B1)),
    SNOWY("Snowy", "AcUnit", "Light snow and ice", Color(0xFF80DEEA), Color(0xFF00ACC1))
}

data class ForecastHour(
    val time: String,
    val temperature: Int,
    val condition: WeatherCondition,
    val precipitationChance: Int = 0
)

data class ForecastDay(
    val dayName: String,
    val condition: WeatherCondition,
    val highTemp: Int,
    val lowTemp: Int,
    val precipitationChance: Int = 0
)

data class AirQuality(
    val aqi: Int,
    val level: String,
    val description: String,
    val dominantPollutant: String
)

data class WeatherDetails(
    val currentTemp: Int,
    val feelsLike: Int,
    val condition: WeatherCondition,
    val highTemp: Int,
    val lowTemp: Int,
    val humidity: Int, // percentage
    val windSpeed: Double, // km/h
    val uvIndex: Int,
    val visibilityKm: Double,
    val pressureHpa: Int,
    val sunrise: String,
    val sunset: String,
    val airQuality: AirQuality,
    val hourlyForecast: List<ForecastHour>,
    val dailyForecast: List<ForecastDay>,
    val aiSummary: String // Elegant AI summary feature (ready for Milestone 2 API)
)

data class CityWeather(
    val cityName: String,
    val country: String,
    val isFavorite: Boolean = false,
    val weatherDetails: WeatherDetails
)
