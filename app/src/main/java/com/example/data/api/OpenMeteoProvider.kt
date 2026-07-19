package com.example.data.api

import com.example.data.models.AirQuality
import com.example.data.models.CityWeather
import com.example.data.models.ForecastDay
import com.example.data.models.ForecastHour
import com.example.data.models.WeatherCondition
import com.example.data.models.WeatherDetails
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class OpenMeteoProvider(
    private val client: OkHttpClient,
    private val moshi: Moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
) : WeatherProvider {

    override suspend fun getForecast(query: String): Result<CityWeather> = withContext(Dispatchers.IO) {
        try {
            // Check if query is latitude/longitude coordinates (e.g., "51.50853,-0.12574")
            var lat: Double? = null
            var lon: Double? = null
            var name = query
            var country = "Unknown"

            val coords = query.split(",")
            if (coords.size == 2) {
                lat = coords[0].toDoubleOrNull()
                lon = coords[1].toDoubleOrNull()
            }

            if (lat == null || lon == null) {
                // If query is a city name, we geocode first to find the coordinates
                val geocodeResult = searchGeocoding(query)
                val firstResult = geocodeResult.getOrNull()?.firstOrNull()
                if (firstResult != null) {
                    lat = firstResult.latitude
                    lon = firstResult.longitude
                    name = firstResult.name
                    country = firstResult.country
                } else {
                    return@withContext Result.failure(Exception("City not found: $query"))
                }
            } else {
                // Reverse geocoding placeholder or simply fetch details for coordinates
                name = "Current Location"
                country = "GPS"
            }

            val url = "https://api.open-meteo.com/v1/forecast?" +
                    "latitude=$lat&longitude=$lon" +
                    "&current=temperature_2m,relative_humidity_2m,apparent_temperature,is_day,precipitation,weather_code,pressure_msl,wind_speed_10m,uv_index,visibility" +
                    "&hourly=temperature_2m,weather_code,precipitation_probability" +
                    "&daily=weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset,precipitation_probability_max" +
                    "&timezone=auto"

            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Open-Meteo server error: ${response.code}"))
                }
                val bodyString = response.body?.string() ?: return@withContext Result.failure(Exception("Empty body"))
                val adapter = moshi.adapter(OpenMeteoResponse::class.java)
                val openMeteoResponse = adapter.fromJson(bodyString) ?: return@withContext Result.failure(Exception("Parsing error"))

                Result.success(mapToCityWeather(name, country, openMeteoResponse))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun searchLocations(query: String): Result<List<CityWeather>> = withContext(Dispatchers.IO) {
        try {
            val geocodeResult = searchGeocoding(query)
            geocodeResult.map { results ->
                results.map { res ->
                    // Return a lightweight CityWeather with 0 temperature but location metadata
                    CityWeather(
                        cityName = res.name,
                        country = res.country,
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
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun searchGeocoding(query: String): Result<List<GeocodingResult>> = withContext(Dispatchers.IO) {
        try {
            val url = "https://geocoding-api.open-meteo.com/v1/search?name=${java.net.URLEncoder.encode(query, "UTF-8")}&count=10&language=en&format=json"
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Geocoding failed: ${response.code}"))
                }
                val bodyString = response.body?.string() ?: return@withContext Result.failure(Exception("Empty geocoding response"))
                val adapter = moshi.adapter(GeocodingResponse::class.java)
                val geoResponse = adapter.fromJson(bodyString)
                Result.success(geoResponse?.results ?: emptyList())
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun mapToCityWeather(name: String, country: String, resp: OpenMeteoResponse): CityWeather {
        val current = resp.current
        val tempF = (current.temperature_2m * 9 / 5 + 32).toInt()
        val feelsLikeF = (current.apparent_temperature * 9 / 5 + 32).toInt()
        val condition = mapWmoCodeToCondition(current.weather_code)

        // Sunrise/sunset formatting
        val rawSunrise = resp.daily.sunrise?.firstOrNull() ?: ""
        val rawSunset = resp.daily.sunset?.firstOrNull() ?: ""
        val sunriseStr = formatTimeString(rawSunrise) ?: "06:00 AM"
        val sunsetStr = formatTimeString(rawSunset) ?: "08:00 PM"

        // Map hourly
        val hourlyForecast = mutableListOf<ForecastHour>()
        val currentTimeMillis = System.currentTimeMillis()
        val hourLimit = 12
        var addedHours = 0
        resp.hourly?.let { hourly ->
            val size = hourly.time.size
            for (i in 0 until size) {
                if (addedHours >= hourLimit) break
                val timeStr = hourly.time[i]
                val hourTempF = (hourly.temperature_2m[i] * 9 / 5 + 32).toInt()
                val hourCondition = mapWmoCodeToCondition(hourly.weather_code[i])
                val precipChance = hourly.precipitation_probability.getOrNull(i) ?: 0

                val displayTime = formatTimeHourOnly(timeStr) ?: timeStr
                hourlyForecast.add(
                    ForecastHour(
                        time = if (addedHours == 0) "Now" else displayTime,
                        temperature = hourTempF,
                        condition = hourCondition,
                        precipitationChance = precipChance
                    )
                )
                addedHours++
            }
        }

        // Map daily
        val dailyForecast = mutableListOf<ForecastDay>()
        resp.daily.let { daily ->
            val size = daily.time.size
            for (i in 0 until size) {
                val dayStr = daily.time[i]
                val maxTempF = (daily.temperature_2m_max[i] * 9 / 5 + 32).toInt()
                val minTempF = (daily.temperature_2m_min[i] * 9 / 5 + 32).toInt()
                val dayCondition = mapWmoCodeToCondition(daily.weather_code[i])
                val precipChance = daily.precipitation_probability_max?.getOrNull(i) ?: 0

                val dayOfWeek = formatDayName(dayStr) ?: dayStr
                dailyForecast.add(
                    ForecastDay(
                        dayName = if (i == 0) "Today" else dayOfWeek,
                        condition = dayCondition,
                        highTemp = maxTempF,
                        lowTemp = minTempF,
                        precipitationChance = precipChance
                    )
                )
            }
        }

        val highTempF = (resp.daily.temperature_2m_max.firstOrNull() ?: current.temperature_2m) * 9 / 5 + 32
        val lowTempF = (resp.daily.temperature_2m_min.firstOrNull() ?: current.temperature_2m) * 9 / 5 + 32

        // Derive beautiful local time of the city using its timezone
        val localTimeStr = try {
            val tz = resp.timezone?.let { TimeZone.getTimeZone(it) } ?: TimeZone.getDefault()
            val df = SimpleDateFormat("h:mm a", Locale.US)
            df.timeZone = tz
            df.format(Date())
        } catch (e: Exception) {
            null
        }

        return CityWeather(
            cityName = name,
            country = country,
            isFavorite = false,
            localTime = localTimeStr,
            weatherDetails = WeatherDetails(
                currentTemp = tempF,
                feelsLike = feelsLikeF,
                condition = condition,
                highTemp = highTempF.toInt(),
                lowTemp = lowTempF.toInt(),
                humidity = current.relative_humidity_2m,
                windSpeed = current.wind_speed_10m,
                uvIndex = current.uv_index.toInt(),
                visibilityKm = current.visibility / 1000.0,
                pressureHpa = current.pressure_msl.toInt(),
                sunrise = sunriseStr,
                sunset = sunsetStr,
                airQuality = AirQuality(1, "Excellent", "Pristine standard green clean index.", "PM2.5"),
                hourlyForecast = hourlyForecast,
                dailyForecast = dailyForecast,
                aiSummary = "An elegant, stable ${condition.displayName.lowercase()} atmosphere. Local barometric trends indicate a level pressure of ${current.pressure_msl.toInt()} hPa, with wind currents at ${current.wind_speed_10m} km/h."
            )
        )
    }

    private fun mapWmoCodeToCondition(code: Int): WeatherCondition {
        return when (code) {
            0 -> WeatherCondition.SUNNY
            1, 2 -> WeatherCondition.PARTLY_CLOUDY
            3, 45, 48 -> WeatherCondition.CLOUDY
            51, 53, 55, 61, 63, 65, 80, 81, 82 -> WeatherCondition.RAINY
            71, 73, 75, 77, 85, 86 -> WeatherCondition.SNOWY
            95, 96, 99 -> WeatherCondition.STORM
            else -> WeatherCondition.PARTLY_CLOUDY
        }
    }

    private fun formatTimeString(isoString: String): String? {
        return try {
            val parts = isoString.split("T")
            if (parts.size == 2) {
                val timeParts = parts[1].split(":")
                val hour = timeParts[0].toInt()
                val minStr = timeParts[1]
                val suffix = if (hour >= 12) "PM" else "AM"
                val displayHour = when {
                    hour == 0 -> 12
                    hour > 12 -> hour - 12
                    else -> hour
                }
                "$displayHour:$minStr $suffix"
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun formatTimeHourOnly(isoString: String): String? {
        return try {
            val parts = isoString.split("T")
            if (parts.size == 2) {
                val timeParts = parts[1].split(":")
                val hour = timeParts[0].toInt()
                val suffix = if (hour >= 12) "PM" else "AM"
                val displayHour = when {
                    hour == 0 -> 12
                    hour > 12 -> hour - 12
                    else -> hour
                }
                "$displayHour:00 $suffix"
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun formatDayName(dateString: String): String? {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val date = sdf.parse(dateString) ?: return null
            val cal = Calendar.getInstance()
            cal.time = date
            val days = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
            days[cal.get(Calendar.DAY_OF_WEEK) - 1]
        } catch (e: Exception) {
            null
        }
    }
}

// Data models for parsing Open-Meteo responses
data class OpenMeteoResponse(
    val timezone: String?,
    val current: OpenMeteoCurrent,
    val hourly: OpenMeteoHourly?,
    val daily: OpenMeteoDaily
)

data class OpenMeteoCurrent(
    val temperature_2m: Double,
    val relative_humidity_2m: Int,
    val apparent_temperature: Double,
    val is_day: Int,
    val weather_code: Int,
    val wind_speed_10m: Double,
    val uv_index: Double,
    val visibility: Double,
    val pressure_msl: Double
)

data class OpenMeteoHourly(
    val time: List<String>,
    val temperature_2m: List<Double>,
    val weather_code: List<Int>,
    val precipitation_probability: List<Int>
)

data class OpenMeteoDaily(
    val time: List<String>,
    val weather_code: List<Int>,
    val temperature_2m_max: List<Double>,
    val temperature_2m_min: List<Double>,
    val sunrise: List<String>?,
    val sunset: List<String>?,
    val precipitation_probability_max: List<Int>?
)

data class GeocodingResponse(
    val results: List<GeocodingResult>?
)

data class GeocodingResult(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val country: String,
    val admin1: String?,
    val timezone: String?
)
