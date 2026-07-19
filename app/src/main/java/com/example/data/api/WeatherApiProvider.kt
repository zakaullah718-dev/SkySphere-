package com.example.data.api

import com.example.BuildConfig
import com.example.data.models.AirQuality
import com.example.data.models.CityWeather
import com.example.data.models.ForecastDay
import com.example.data.models.ForecastHour
import com.example.data.models.WeatherCondition
import com.example.data.models.WeatherDetails
import com.example.weather.data.api.WeatherApiService
import com.example.weather.data.models.WeatherResponseDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WeatherApiProvider(
    private val apiService: WeatherApiService
) : WeatherProvider {

    override suspend fun getForecast(query: String): Result<CityWeather> = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isBlank() || apiKey == "PLACEholder_WEATHER_API_KEY") {
            return@withContext Result.failure(IllegalStateException("WeatherAPI key is not configured."))
        }
        try {
            val response = apiService.getForecast(apiKey = apiKey, query = query, days = 7, aqi = "yes")
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    Result.success(mapResponseToCityWeather(body))
                } else {
                    Result.failure(Exception("Response body was empty"))
                }
            } else {
                Result.failure(Exception("Error code: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun searchLocations(query: String): Result<List<CityWeather>> = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isBlank() || apiKey == "PLACEholder_WEATHER_API_KEY") {
            return@withContext Result.success(emptyList())
        }
        try {
            val response = apiService.searchLocations(apiKey, query)
            if (response.isSuccessful) {
                val locations = response.body() ?: emptyList()
                val mapped = locations.map { loc ->
                    CityWeather(
                        cityName = loc.name,
                        country = loc.country,
                        isFavorite = false,
                        weatherDetails = WeatherDetails(
                            currentTemp = 0,
                            feelsLike = 0,
                            condition = WeatherCondition.PARTLY_CLOUDY,
                            highTemp = 0,
                            lowTemp = 0,
                            humidity = 0,
                            windSpeed = 0.0,
                            uvIndex = 0,
                            visibilityKm = 0.0,
                            pressureHpa = 0,
                            sunrise = "06:00 AM",
                            sunset = "08:00 PM",
                            airQuality = AirQuality(1, "Good", "", ""),
                            hourlyForecast = emptyList(),
                            dailyForecast = emptyList(),
                            aiSummary = ""
                        )
                    )
                }
                Result.success(mapped)
            } else {
                Result.failure(Exception("Search request failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun getApiKey(): String {
        return try {
            BuildConfig.WEATHER_API_KEY
        } catch (e: Exception) {
            ""
        }
    }

    private fun mapResponseToCityWeather(dto: WeatherResponseDto): CityWeather {
        val location = dto.location
        val current = dto.current
        val forecastDays = dto.forecast?.forecastday ?: emptyList()
        val conditionEnum = mapCodeToCondition(current.condition.code)

        val hourlyList = forecastDays.firstOrNull()?.hour?.mapIndexed { index, hourDto ->
            val displayTime = try {
                val parts = hourDto.time.split(" ")
                if (parts.size >= 2) {
                    val timePart = parts[1]
                    val hr = timePart.split(":")[0].toInt()
                    when {
                        hr == 0 -> "12:00 AM"
                        hr < 12 -> "$hr:00 AM"
                        hr == 12 -> "12:00 PM"
                        else -> "${hr - 12}:00 PM"
                    }
                } else {
                    hourDto.time
                }
            } catch (e: Exception) {
                hourDto.time
            }

            ForecastHour(
                time = if (index == 0) "Now" else displayTime,
                temperature = hourDto.tempF.toInt(),
                condition = mapCodeToCondition(hourDto.condition.code),
                precipitationChance = hourDto.chanceOfRain ?: 0
            )
        } ?: emptyList()

        val dailyList = forecastDays.mapIndexed { index, fDay ->
            val dayName = if (index == 0) {
                "Today"
            } else {
                try {
                    val dateParts = fDay.date.split("-")
                    if (dateParts.size == 3) {
                        val calendar = java.util.Calendar.getInstance()
                        calendar.set(dateParts[0].toInt(), dateParts[1].toInt() - 1, dateParts[2].toInt())
                        val daysOfWeek = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
                        daysOfWeek[calendar.get(java.util.Calendar.DAY_OF_WEEK) - 1]
                    } else {
                        fDay.date
                    }
                } catch (e: Exception) {
                    fDay.date
                }
            }

            ForecastDay(
                dayName = dayName,
                condition = mapCodeToCondition(fDay.day.condition.code),
                highTemp = fDay.day.maxtempF.toInt(),
                lowTemp = fDay.day.mintempF.toInt(),
                precipitationChance = fDay.day.dailyChanceOfRain ?: 0
            )
        }

        val epaIndex = current.airQuality?.epaIndex ?: 1
        val (aqiDesc, aqiRec) = when (epaIndex) {
            1 -> "Good" to "Pristine atmosphere. Outdoor activities are safe for everyone."
            2 -> "Moderate" to "Acceptable air quality."
            3 -> "Unhealthy for Sensitive Groups" to "Sensitive groups should limit exertion."
            4 -> "Unhealthy" to "Everyone should limit heavy exertion."
            else -> "Very Unhealthy" to "Health warnings: limit outdoor exposure."
        }

        val formattedLocalTime = try {
            val raw = location.localtime
            if (!raw.isNullOrBlank()) {
                val parts = raw.split(" ")
                if (parts.size == 2) {
                    val timePart = parts[1]
                    val tParts = timePart.split(":")
                    if (tParts.size == 2) {
                        val hour = tParts[0].toInt()
                        val minStr = tParts[1]
                        val suffix = if (hour >= 12) "PM" else "AM"
                        val displayHour = when {
                            hour == 0 -> 12
                            hour > 12 -> hour - 12
                            else -> hour
                        }
                        "$displayHour:$minStr $suffix"
                    } else {
                        timePart
                    }
                } else {
                    raw
                }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }

        return CityWeather(
            cityName = location.name,
            country = location.country,
            isFavorite = false,
            localTime = formattedLocalTime,
            weatherDetails = WeatherDetails(
                currentTemp = current.tempF.toInt(),
                feelsLike = current.feelslikeF.toInt(),
                condition = conditionEnum,
                highTemp = forecastDays.firstOrNull()?.day?.maxtempF?.toInt() ?: current.tempF.toInt(),
                lowTemp = forecastDays.firstOrNull()?.day?.mintempF?.toInt() ?: current.tempF.toInt(),
                humidity = current.humidity,
                windSpeed = current.windKph,
                uvIndex = current.uv.toInt(),
                visibilityKm = current.visKm,
                pressureHpa = current.pressureMb.toInt(),
                sunrise = forecastDays.firstOrNull()?.astro?.sunrise ?: "05:42 AM",
                sunset = forecastDays.firstOrNull()?.astro?.sunset ?: "08:21 PM",
                airQuality = AirQuality(
                    aqi = epaIndex,
                    level = aqiDesc,
                    description = aqiRec,
                    dominantPollutant = "PM2.5"
                ),
                hourlyForecast = hourlyList,
                dailyForecast = dailyList,
                aiSummary = "An elegant ${conditionEnum.displayName.lowercase()} day. Wind speeds average ${current.windKph} km/h with a humidity level of ${current.humidity}%. Perfect for responsive monitoring.",
                cloudCoverage = current.cloud,
                windDirection = current.windDir ?: "N"
            )
        )
    }

    private fun mapCodeToCondition(code: Int): WeatherCondition {
        return when (code) {
            1000 -> WeatherCondition.SUNNY
            1003 -> WeatherCondition.PARTLY_CLOUDY
            1006, 1009, 1030, 1135, 1147 -> WeatherCondition.CLOUDY
            1063, 1150, 1153, 1180, 1183, 1186, 1189, 1192, 1195, 1240, 1243 -> WeatherCondition.RAINY
            1087, 1273, 1276, 1279, 1282 -> WeatherCondition.STORM
            1066, 1114, 1117, 1210, 1213, 1216, 1219, 1222, 1225, 1255, 1258 -> WeatherCondition.SNOWY
            else -> WeatherCondition.PARTLY_CLOUDY
        }
    }
}
