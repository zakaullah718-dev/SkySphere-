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

class OpenWeatherProvider(
    private val client: OkHttpClient,
    private val moshi: Moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
) : WeatherProvider {

    override suspend fun getForecast(query: String): Result<CityWeather> = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isBlank() || apiKey == "PLACEholder_WEATHER_API_KEY") {
            return@withContext Result.failure(IllegalStateException("OpenWeather API key is not configured."))
        }
        try {
            // Build OpenWeather query URL (e.g. by city name or lat,lon)
            val url = if (query.contains(",")) {
                val coords = query.split(",")
                "https://api.openweathermap.org/data/2.5/forecast?lat=${coords[0]}&lon=${coords[1]}&units=imperial&appid=$apiKey"
            } else {
                "https://api.openweathermap.org/data/2.5/forecast?q=${java.net.URLEncoder.encode(query, "UTF-8")}&units=imperial&appid=$apiKey"
            }

            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("OpenWeather server error: ${response.code}"))
                }
                val bodyString = response.body?.string() ?: return@withContext Result.failure(Exception("Empty body"))
                val adapter = moshi.adapter(OpenWeatherResponse::class.java)
                val resp = adapter.fromJson(bodyString) ?: return@withContext Result.failure(Exception("Parsing error"))

                Result.success(mapToCityWeather(resp))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun searchLocations(query: String): Result<List<CityWeather>> = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isBlank()) return@withContext Result.success(emptyList())
        try {
            // Geocoding API from OpenWeather
            val url = "https://api.openweathermap.org/geo/1.0/direct?q=${java.net.URLEncoder.encode(query, "UTF-8")}&limit=10&appid=$apiKey"
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("OpenWeather geocoding error: ${response.code}"))
                }
                val bodyString = response.body?.string() ?: return@withContext Result.failure(Exception("Empty body"))
                // Parse a list of geo results
                val listType = com.squareup.moshi.Types.newParameterizedType(List::class.java, OpenWeatherGeoResult::class.java)
                val adapter = moshi.adapter<List<OpenWeatherGeoResult>>(listType)
                val results = adapter.fromJson(bodyString) ?: emptyList()

                val mapped = results.map { geo ->
                    CityWeather(
                        cityName = geo.name,
                        country = geo.country,
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
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun getApiKey(): String {
        return try {
            // Fallback check for OpenWeather specific configuration or generic WEATHER_API_KEY
            com.example.BuildConfig.WEATHER_API_KEY
        } catch (e: Exception) {
            ""
        }
    }

    private fun mapToCityWeather(resp: OpenWeatherResponse): CityWeather {
        val city = resp.city
        val firstForecast = resp.list.firstOrNull() ?: throw Exception("No forecast entries found")
        val currentTemp = firstForecast.main.temp.toInt()
        val feelsLike = firstForecast.main.feels_like.toInt()
        val weatherIconCode = firstForecast.weather.firstOrNull()?.icon ?: ""
        val condition = mapOpenWeatherIconToCondition(weatherIconCode)

        // Sunrise/sunset formatting
        val tzOffsetSeconds = city.timezone ?: 0
        val sunriseDate = Date((city.sunrise ?: 0) * 1000L)
        val sunsetDate = Date((city.sunset ?: 0) * 1000L)
        val sdf = SimpleDateFormat("hh:mm a", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("GMT").apply {
                rawOffset = tzOffsetSeconds * 1000
            }
        }
        val sunriseStr = sdf.format(sunriseDate)
        val sunsetStr = sdf.format(sunsetDate)

        // Parse hourly forecast (take first 8 records -> 24 hours of 3-hour chunks)
        val hourlyForecast = resp.list.take(8).mapIndexed { idx, item ->
            val hourDate = Date(item.dt * 1000L)
            val formatHour = SimpleDateFormat("h:mm a", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("GMT").apply { rawOffset = tzOffsetSeconds * 1000 }
            }
            ForecastHour(
                time = if (idx == 0) "Now" else formatHour.format(hourDate),
                temperature = item.main.temp.toInt(),
                condition = mapOpenWeatherIconToCondition(item.weather.firstOrNull()?.icon ?: ""),
                precipitationChance = (item.pop * 100).toInt()
            )
        }

        // Parse daily forecast (aggregate 3-hour steps into days)
        val dailyGroups = resp.list.groupBy {
            val date = Date(it.dt * 1000L)
            val sdfDay = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("GMT").apply { rawOffset = tzOffsetSeconds * 1000 }
            }
            sdfDay.format(date)
        }

        val dailyForecast = dailyGroups.entries.take(5).mapIndexed { index, entry ->
            val dayCode = entry.key
            val dayItems = entry.value
            val maxTemp = dayItems.maxOf { it.main.temp_max }.toInt()
            val minTemp = dayItems.minOf { it.main.temp_min }.toInt()
            val dominantIcon = dayItems.groupBy { it.weather.firstOrNull()?.icon ?: "" }
                .maxByOrNull { it.value.size }?.key ?: ""
            val dayCondition = mapOpenWeatherIconToCondition(dominantIcon)
            val maxPop = (dayItems.maxOf { it.pop } * 100).toInt()

            val dayOfWeek = if (index == 0) {
                "Today"
            } else {
                try {
                    val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(dayCode)
                    val cal = Calendar.getInstance()
                    if (date != null) cal.time = date
                    val days = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
                    days[cal.get(Calendar.DAY_OF_WEEK) - 1]
                } catch (e: Exception) {
                    dayCode
                }
            }

            ForecastDay(
                dayName = dayOfWeek,
                condition = dayCondition,
                highTemp = maxTemp,
                lowTemp = minTemp,
                precipitationChance = maxPop
            )
        }

        val highTemp = dailyForecast.firstOrNull()?.highTemp ?: currentTemp
        val lowTemp = dailyForecast.firstOrNull()?.lowTemp ?: currentTemp

        val localTimeStr = try {
            val tz = TimeZone.getTimeZone("GMT").apply { rawOffset = tzOffsetSeconds * 1000 }
            val df = SimpleDateFormat("h:mm a", Locale.US)
            df.timeZone = tz
            df.format(Date())
        } catch (e: Exception) {
            null
        }

        return CityWeather(
            cityName = city.name,
            country = city.country ?: "Unknown",
            isFavorite = false,
            localTime = localTimeStr,
            weatherDetails = WeatherDetails(
                currentTemp = currentTemp,
                feelsLike = feelsLike,
                condition = condition,
                highTemp = highTemp,
                lowTemp = lowTemp,
                humidity = firstForecast.main.humidity,
                windSpeed = firstForecast.wind.speed * 1.60934, // convert mph to km/h
                uvIndex = 5, // OpenWeather 5-day forecast does not include UV in free 2.5 endpoint
                visibilityKm = firstForecast.visibility / 1000.0,
                pressureHpa = firstForecast.main.pressure,
                sunrise = sunriseStr,
                sunset = sunsetStr,
                airQuality = AirQuality(2, "Moderate", "Normal urban atmospheric particulate concentration.", "PM10"),
                hourlyForecast = hourlyForecast,
                dailyForecast = dailyForecast,
                aiSummary = "An elegant ${condition.displayName.lowercase()} day. Air currents flow at ${String.format(Locale.US, "%.1f", firstForecast.wind.speed * 1.60934)} km/h. Local barometric pressure is stable at ${firstForecast.main.pressure} hPa."
            )
        )
    }

    private fun mapOpenWeatherIconToCondition(icon: String): WeatherCondition {
        return when {
            icon.startsWith("01") -> WeatherCondition.SUNNY
            icon.startsWith("02") -> WeatherCondition.PARTLY_CLOUDY
            icon.startsWith("03") || icon.startsWith("04") || icon.startsWith("50") -> WeatherCondition.CLOUDY
            icon.startsWith("09") || icon.startsWith("10") -> WeatherCondition.RAINY
            icon.startsWith("11") -> WeatherCondition.STORM
            icon.startsWith("13") -> WeatherCondition.SNOWY
            else -> WeatherCondition.PARTLY_CLOUDY
        }
    }
}

// OpenWeather JSON structures
data class OpenWeatherResponse(
    val list: List<OpenWeatherForecastItem>,
    val city: OpenWeatherCity
)

data class OpenWeatherCity(
    val name: String,
    val country: String?,
    val sunrise: Long?,
    val sunset: Long?,
    val timezone: Int?
)

data class OpenWeatherForecastItem(
    val dt: Long,
    val main: OpenWeatherMain,
    val weather: List<OpenWeatherWeather>,
    val wind: OpenWeatherWind,
    val visibility: Int,
    val pop: Double
)

data class OpenWeatherMain(
    val temp: Double,
    val feels_like: Double,
    val temp_min: Double,
    val temp_max: Double,
    val pressure: Int,
    val humidity: Int
)

data class OpenWeatherWeather(
    val id: Int,
    val main: String,
    val description: String,
    val icon: String
)

data class OpenWeatherWind(
    val speed: Double
)

data class OpenWeatherGeoResult(
    val name: String,
    val lat: Double,
    val lon: Double,
    val country: String
)
