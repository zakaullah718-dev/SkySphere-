package com.example.weather.data.models

import com.squareup.moshi.Json

data class LocationDto(
    @Json(name = "name") val name: String,
    @Json(name = "region") val region: String?,
    @Json(name = "country") val country: String,
    @Json(name = "lat") val lat: Double,
    @Json(name = "lon") val lon: Double,
    @Json(name = "tz_id") val tzId: String?,
    @Json(name = "localtime") val localtime: String? = null
)

data class ConditionDto(
    @Json(name = "text") val text: String,
    @Json(name = "icon") val icon: String,
    @Json(name = "code") val code: Int
)

data class AirQualityDto(
    @Json(name = "co") val co: Double?,
    @Json(name = "no2") val no2: Double?,
    @Json(name = "o3") val o3: Double?,
    @Json(name = "pm2_5") val pm2_5: Double?,
    @Json(name = "pm10") val pm10: Double?,
    @Json(name = "us-epa-index") val epaIndex: Int?
)

data class CurrentDto(
    @Json(name = "temp_c") val tempC: Double,
    @Json(name = "temp_f") val tempF: Double,
    @Json(name = "is_day") val isDay: Int,
    @Json(name = "condition") val condition: ConditionDto,
    @Json(name = "wind_kph") val windKph: Double,
    @Json(name = "wind_dir") val windDir: String?,
    @Json(name = "pressure_mb") val pressureMb: Double,
    @Json(name = "humidity") val humidity: Int,
    @Json(name = "cloud") val cloud: Int,
    @Json(name = "feelslike_c") val feelslikeC: Double,
    @Json(name = "feelslike_f") val feelslikeF: Double,
    @Json(name = "vis_km") val visKm: Double,
    @Json(name = "uv") val uv: Double,
    @Json(name = "air_quality") val airQuality: AirQualityDto?
)

data class HourDto(
    @Json(name = "time_epoch") val timeEpoch: Long,
    @Json(name = "time") val time: String,
    @Json(name = "temp_c") val tempC: Double,
    @Json(name = "temp_f") val tempF: Double,
    @Json(name = "is_day") val isDay: Int,
    @Json(name = "condition") val condition: ConditionDto,
    @Json(name = "chance_of_rain") val chanceOfRain: Int?
)

data class DayDto(
    @Json(name = "maxtemp_c") val maxtempC: Double,
    @Json(name = "maxtemp_f") val maxtempF: Double,
    @Json(name = "mintemp_c") val mintempC: Double,
    @Json(name = "mintemp_f") val mintempF: Double,
    @Json(name = "daily_chance_of_rain") val dailyChanceOfRain: Int?,
    @Json(name = "condition") val condition: ConditionDto,
    @Json(name = "uv") val uv: Double?
)

data class AstroDto(
    @Json(name = "sunrise") val sunrise: String?,
    @Json(name = "sunset") val sunset: String?
)

data class ForecastDayDto(
    @Json(name = "date") val date: String,
    @Json(name = "day") val day: DayDto,
    @Json(name = "astro") val astro: AstroDto? = null,
    @Json(name = "hour") val hour: List<HourDto>?
)

data class ForecastContainerDto(
    @Json(name = "forecastday") val forecastday: List<ForecastDayDto>
)

data class WeatherResponseDto(
    @Json(name = "location") val location: LocationDto,
    @Json(name = "current") val current: CurrentDto,
    @Json(name = "forecast") val forecast: ForecastContainerDto?
)
