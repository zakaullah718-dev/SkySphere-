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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class WeatherApiProvider(
    private val apiService: WeatherApiService
) : WeatherProvider {

    override suspend fun getForecast(query: String): Result<CityWeather> = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isBlank() || apiKey == "PLACEholder_WEATHER_API_KEY") {
            return@withContext Result.failure(IllegalStateException("WeatherAPI key is not configured."))
        }
        try {
            var apiQuery = query
            var customName: String? = null
            var customRegion: String? = null
            var customCountry: String? = null

            if (query.startsWith("COORDS:")) {
                try {
                    val parts = query.substring(7).split("|")
                    apiQuery = parts[0]
                    customName = parts.getOrNull(1)
                    customRegion = parts.getOrNull(2)
                    customCountry = parts.getOrNull(3)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            val response = apiService.getForecast(apiKey = apiKey, query = apiQuery, days = 7, aqi = "yes")
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    val cityWeather = mapResponseToCityWeather(body)
                    val finalWeather = if (customName != null) {
                        cityWeather.copy(
                            cityName = customName,
                            region = customRegion?.takeIf { it.isNotBlank() } ?: cityWeather.region,
                            country = customCountry ?: cityWeather.country
                        )
                    } else {
                        cityWeather
                    }
                    Result.success(finalWeather)
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
                        ),
                        region = loc.region,
                        latitude = loc.lat,
                        longitude = loc.lon
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

        val targetTz = location.tzId?.let {
            try { TimeZone.getTimeZone(it) } catch (e: Exception) { null }
        } ?: TimeZone.getDefault()

        val displayFormat = SimpleDateFormat("h:mm a", Locale.US).apply {
            timeZone = targetTz
        }

        val nowMillis = System.currentTimeMillis()
        val cal = Calendar.getInstance(targetTz).apply {
            timeInMillis = nowMillis
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val currentHourStartMillis = cal.timeInMillis

        val allHours = forecastDays.flatMap { it.hour ?: emptyList() }
        val astroFirst = forecastDays.firstOrNull()?.astro
        val sunriseStr = astroFirst?.sunrise ?: "06:00 AM"
        val sunsetStr = astroFirst?.sunset ?: "07:00 PM"
        val tzId = location.tzId

        data class WeatherApiHourCandidate(
            val epochMillis: Long,
            val tempF: Int,
            val condition: WeatherCondition,
            val precipChance: Int,
            val isNight: Boolean
        )
        val candidates = allHours.map { hourDto ->
            val epochMillis = hourDto.timeEpoch * 1000L
            val isNight = if (hourDto.isDay != null) {
                hourDto.isDay == 0
            } else {
                com.example.utils.WeatherTimeUtils.isNightForLocation(
                    timestampEpochMillis = epochMillis,
                    timeZoneId = tzId,
                    sunriseStr = sunriseStr,
                    sunsetStr = sunsetStr
                )
            }
            WeatherApiHourCandidate(
                epochMillis = epochMillis,
                tempF = hourDto.tempF.toInt(),
                condition = mapCodeToCondition(hourDto.condition.code),
                precipChance = hourDto.chanceOfRain ?: 0,
                isNight = isNight
            )
        }

        val futureCandidates = candidates.filter { it.epochMillis >= currentHourStartMillis }
        val selectedCandidates = if (futureCandidates.isNotEmpty()) futureCandidates.take(12) else candidates.take(12)

        val hourlyList = selectedCandidates.mapIndexed { index, candidate ->
            val timeLabel = if (index == 0) "Now" else displayFormat.format(Date(candidate.epochMillis))
            ForecastHour(
                time = timeLabel,
                temperature = candidate.tempF,
                condition = candidate.condition,
                precipitationChance = candidate.precipChance,
                timestampEpochMillis = candidate.epochMillis,
                isNight = candidate.isNight
            )
        }

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
            timeZoneId = tzId,
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
                windDirection = current.windDir ?: "N",
                timeZoneId = tzId
            ),
            region = location.region
        )
    }

    private fun mapCodeToCondition(code: Int): WeatherCondition {
        return when (code) {
            1000 -> WeatherCondition.SUNNY
            1003 -> WeatherCondition.PARTLY_CLOUDY
            1006, 1009 -> WeatherCondition.CLOUDY
            1030, 1135, 1147 -> WeatherCondition.FOGGY
            1063, 1150, 1153, 1180, 1183, 1186, 1189, 1192, 1195, 1240, 1243 -> WeatherCondition.RAINY
            1087, 1273, 1276, 1279, 1282 -> WeatherCondition.STORM
            1066, 1114, 1117, 1210, 1213, 1216, 1219, 1222, 1225, 1255, 1258 -> WeatherCondition.SNOWY
            else -> WeatherCondition.PARTLY_CLOUDY
        }
    }
}
