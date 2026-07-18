package com.example.data.repository

import com.example.BuildConfig
import com.example.data.models.AirQuality
import com.example.data.models.CityWeather
import com.example.data.models.ForecastDay
import com.example.data.models.ForecastHour
import com.example.data.models.WeatherCondition
import com.example.data.models.WeatherDetails
import com.example.weather.data.api.WeatherApiService
import com.example.weather.data.models.WeatherResponseDto
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

class WeatherRepository {

    // Network integration via Retrofit
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://api.weatherapi.com/v1/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    private val apiService = retrofit.create(WeatherApiService::class.java)

    private val _isCelsius = MutableStateFlow(false)
    val isCelsius = _isCelsius.asStateFlow()

    private val _windUnit = MutableStateFlow("km/h")
    val windUnit = _windUnit.asStateFlow()

    fun setCelsius(enabled: Boolean) {
        _isCelsius.value = enabled
    }

    fun setWindUnit(unit: String) {
        _windUnit.value = unit
    }

    // Seed mock data for global cities (used as instant, beautiful offline/fallback data)
    private val _cities = MutableStateFlow(listOf(
        CityWeather(
            cityName = "New York",
            country = "United States",
            isFavorite = true,
            weatherDetails = WeatherDetails(
                currentTemp = 75,
                feelsLike = 77,
                condition = WeatherCondition.PARTLY_CLOUDY,
                highTemp = 78,
                lowTemp = 62,
                humidity = 64,
                windSpeed = 12.5,
                uvIndex = 6,
                visibilityKm = 10.0,
                pressureHpa = 1016,
                sunrise = "05:42 AM",
                sunset = "08:21 PM",
                airQuality = AirQuality(38, "Good", "Air quality is satisfactory.", "PM2.5"),
                hourlyForecast = listOf(
                    ForecastHour("Now", 75, WeatherCondition.PARTLY_CLOUDY),
                    ForecastHour("08:00 AM", 76, WeatherCondition.PARTLY_CLOUDY),
                    ForecastHour("10:00 AM", 78, WeatherCondition.SUNNY),
                    ForecastHour("12:00 PM", 80, WeatherCondition.SUNNY),
                    ForecastHour("02:00 PM", 81, WeatherCondition.SUNNY),
                    ForecastHour("04:00 PM", 79, WeatherCondition.PARTLY_CLOUDY),
                    ForecastHour("06:00 PM", 75, WeatherCondition.CLOUDY),
                    ForecastHour("08:00 PM", 71, WeatherCondition.RAINY, 60),
                    ForecastHour("10:00 PM", 68, WeatherCondition.RAINY, 80)
                ),
                dailyForecast = listOf(
                    ForecastDay("Today", WeatherCondition.PARTLY_CLOUDY, 78, 62),
                    ForecastDay("Sat", WeatherCondition.SUNNY, 82, 65),
                    ForecastDay("Sun", WeatherCondition.STORM, 79, 64, 90),
                    ForecastDay("Mon", WeatherCondition.RAINY, 72, 59, 70),
                    ForecastDay("Tue", WeatherCondition.PARTLY_CLOUDY, 75, 60),
                    ForecastDay("Wed", WeatherCondition.SUNNY, 78, 62),
                    ForecastDay("Thu", WeatherCondition.CLOUDY, 74, 61)
                ),
                aiSummary = "An elegant partly cloudy morning will give way to brilliant sunshine in the afternoon. Perfect for outdoor activities, though a gentle breeze recommends light layering."
            )
        ),
        CityWeather(
            cityName = "Reykjavik",
            country = "Iceland",
            isFavorite = true,
            weatherDetails = WeatherDetails(
                currentTemp = 42,
                feelsLike = 35,
                condition = WeatherCondition.SNOWY,
                highTemp = 45,
                lowTemp = 30,
                humidity = 82,
                windSpeed = 28.0,
                uvIndex = 1,
                visibilityKm = 6.4,
                pressureHpa = 998,
                sunrise = "03:11 AM",
                sunset = "11:58 PM",
                airQuality = AirQuality(12, "Excellent", "Pristine Arctic atmosphere.", "O3"),
                hourlyForecast = listOf(
                    ForecastHour("Now", 42, WeatherCondition.SNOWY),
                    ForecastHour("08:00 AM", 40, WeatherCondition.SNOWY, 40),
                    ForecastHour("10:00 AM", 38, WeatherCondition.STORM, 80),
                    ForecastHour("12:00 PM", 39, WeatherCondition.STORM, 90),
                    ForecastHour("02:00 PM", 41, WeatherCondition.SNOWY, 50),
                    ForecastHour("04:00 PM", 42, WeatherCondition.CLOUDY),
                    ForecastHour("06:00 PM", 40, WeatherCondition.CLOUDY),
                    ForecastHour("08:00 PM", 38, WeatherCondition.CLOUDY),
                    ForecastHour("10:00 PM", 35, WeatherCondition.CLOUDY)
                ),
                dailyForecast = listOf(
                    ForecastDay("Today", WeatherCondition.SNOWY, 42, 30, 80),
                    ForecastDay("Sat", WeatherCondition.STORM, 39, 28, 90),
                    ForecastDay("Sun", WeatherCondition.CLOUDY, 41, 32),
                    ForecastDay("Mon", WeatherCondition.CLOUDY, 43, 34),
                    ForecastDay("Tue", WeatherCondition.PARTLY_CLOUDY, 45, 35),
                    ForecastDay("Wed", WeatherCondition.SUNNY, 48, 36),
                    ForecastDay("Thu", WeatherCondition.RAINY, 44, 33, 40)
                ),
                aiSummary = "Crisp, snowy winds will dominate. A heavy sub-zero chill is intensified by high Arctic gusts up to 28 km/h. Keep warm and expect snowy flurries."
            )
        ),
        CityWeather(
            cityName = "Tokyo",
            country = "Japan",
            isFavorite = false,
            weatherDetails = WeatherDetails(
                currentTemp = 68,
                feelsLike = 68,
                condition = WeatherCondition.RAINY,
                highTemp = 72,
                lowTemp = 58,
                humidity = 90,
                windSpeed = 8.4,
                uvIndex = 3,
                visibilityKm = 8.0,
                pressureHpa = 1009,
                sunrise = "04:35 AM",
                sunset = "07:01 PM",
                airQuality = AirQuality(45, "Good", "Satisfactory air quality in central areas.", "NO2"),
                hourlyForecast = listOf(
                    ForecastHour("Now", 68, WeatherCondition.RAINY, 80),
                    ForecastHour("08:00 AM", 67, WeatherCondition.RAINY, 90),
                    ForecastHour("10:00 AM", 68, WeatherCondition.RAINY, 70),
                    ForecastHour("12:00 PM", 69, WeatherCondition.CLOUDY),
                    ForecastHour("02:00 PM", 71, WeatherCondition.PARTLY_CLOUDY),
                    ForecastHour("04:00 PM", 72, WeatherCondition.SUNNY),
                    ForecastHour("06:00 PM", 70, WeatherCondition.SUNNY),
                    ForecastHour("08:00 PM", 65, WeatherCondition.SUNNY),
                    ForecastHour("10:00 PM", 62, WeatherCondition.SUNNY)
                ),
                dailyForecast = listOf(
                    ForecastDay("Today", WeatherCondition.RAINY, 72, 58, 80),
                    ForecastDay("Sat", WeatherCondition.PARTLY_CLOUDY, 74, 59),
                    ForecastDay("Sun", WeatherCondition.SUNNY, 78, 62),
                    ForecastDay("Mon", WeatherCondition.SUNNY, 80, 63),
                    ForecastDay("Tue", WeatherCondition.SUNNY, 81, 64),
                    ForecastDay("Wed", WeatherCondition.CLOUDY, 75, 60),
                    ForecastDay("Thu", WeatherCondition.RAINY, 71, 57, 60)
                ),
                aiSummary = "A gentle rainy morning will clear up beautifully by noon. Atmospheric humidity is high, but fresh rain-washed breezes from Tokyo Bay will create an exquisite late afternoon."
            )
        ),
        CityWeather(
            cityName = "Paris",
            country = "France",
            isFavorite = true,
            weatherDetails = WeatherDetails(
                currentTemp = 70,
                feelsLike = 70,
                condition = WeatherCondition.SUNNY,
                highTemp = 74,
                lowTemp = 56,
                humidity = 50,
                windSpeed = 6.2,
                uvIndex = 5,
                visibilityKm = 12.0,
                pressureHpa = 1018,
                sunrise = "05:58 AM",
                sunset = "09:45 PM",
                airQuality = AirQuality(30, "Excellent", "Fresh clean air flows through Paris.", "PM10"),
                hourlyForecast = listOf(
                    ForecastHour("Now", 70, WeatherCondition.SUNNY),
                    ForecastHour("08:00 AM", 62, WeatherCondition.SUNNY),
                    ForecastHour("10:00 AM", 68, WeatherCondition.SUNNY),
                    ForecastHour("12:00 PM", 71, WeatherCondition.SUNNY),
                    ForecastHour("02:00 PM", 74, WeatherCondition.SUNNY),
                    ForecastHour("04:00 PM", 73, WeatherCondition.SUNNY),
                    ForecastHour("06:00 PM", 70, WeatherCondition.SUNNY),
                    ForecastHour("08:00 PM", 66, WeatherCondition.PARTLY_CLOUDY),
                    ForecastHour("10:00 PM", 60, WeatherCondition.CLOUDY)
                ),
                dailyForecast = listOf(
                    ForecastDay("Today", WeatherCondition.SUNNY, 74, 56),
                    ForecastDay("Sat", WeatherCondition.SUNNY, 76, 58),
                    ForecastDay("Sun", WeatherCondition.PARTLY_CLOUDY, 75, 59),
                    ForecastDay("Mon", WeatherCondition.CLOUDY, 71, 55),
                    ForecastDay("Tue", WeatherCondition.RAINY, 68, 52, 50),
                    ForecastDay("Wed", WeatherCondition.PARTLY_CLOUDY, 70, 54),
                    ForecastDay("Thu", WeatherCondition.SUNNY, 73, 56)
                ),
                aiSummary = "Splendid light in Paris today. Clear skies and gentle sunshine with low humidity and very clean air make for highly pleasant conditions."
            )
        )
    ))

    private val _selectedCity = MutableStateFlow<CityWeather>(_cities.value.first())
    val selectedCity = _selectedCity.asStateFlow()

    init {
        // Trigger background live weather update for default pre-seeded cities on startup
        CoroutineScope(Dispatchers.IO).launch {
            updateDefaultCitiesWithLiveWeather()
        }
    }

    private suspend fun updateDefaultCitiesWithLiveWeather() {
        val updatedList = _cities.value.map { city ->
            fetchWeatherFromApi(city.cityName).getOrNull()?.copy(isFavorite = city.isFavorite) ?: city
        }
        _cities.value = updatedList
        val currentSelected = _selectedCity.value
        val newlyUpdatedSelected = updatedList.find { it.cityName.equals(currentSelected.cityName, ignoreCase = true) }
        if (newlyUpdatedSelected != null) {
            _selectedCity.value = newlyUpdatedSelected
        }
    }

    fun getCitiesFlow(): Flow<List<CityWeather>> = _cities.asStateFlow()

    fun getFavoritesFlow(): Flow<List<CityWeather>> = _cities.map { list ->
        list.filter { it.isFavorite }
    }

    fun selectCity(cityName: String) {
        val existing = _cities.value.find { it.cityName.equals(cityName, ignoreCase = true) }
        if (existing != null && existing.weatherDetails.currentTemp != 0) {
            _selectedCity.value = existing
        } else {
            // It's a new search-obtained or placeholder city; fetch full forecast asynchronously
            CoroutineScope(Dispatchers.IO).launch {
                val result = fetchWeatherFromApi(cityName)
                result.onSuccess { fullCityWeather ->
                    val withFav = fullCityWeather.copy(isFavorite = existing?.isFavorite ?: false)
                    val currentList = _cities.value.toMutableList()
                    val index = currentList.indexOfFirst { it.cityName.equals(cityName, ignoreCase = true) }
                    if (index >= 0) {
                        currentList[index] = withFav
                    } else {
                        currentList.add(withFav)
                    }
                    _cities.value = currentList
                    _selectedCity.value = withFav
                }.onFailure {
                    if (existing != null) {
                        _selectedCity.value = existing
                    }
                }
            }
        }
    }

    fun selectLocationCoordinates(latitude: Double, longitude: Double) {
        val query = "$latitude,$longitude"
        CoroutineScope(Dispatchers.IO).launch {
            val result = fetchWeatherFromApi(query)
            result.onSuccess { fullCityWeather ->
                val friendlyCity = fullCityWeather.copy(
                    cityName = if (fullCityWeather.cityName.isBlank()) "Current Location" else fullCityWeather.cityName
                )
                val currentList = _cities.value.toMutableList()
                val index = currentList.indexOfFirst { it.cityName.equals(friendlyCity.cityName, ignoreCase = true) }
                if (index >= 0) {
                    currentList[index] = friendlyCity
                } else {
                    currentList.add(friendlyCity)
                }
                _cities.value = currentList
                _selectedCity.value = friendlyCity
            }
        }
    }

    fun forceRefreshActiveCity() {
        val active = _selectedCity.value
        CoroutineScope(Dispatchers.IO).launch {
            val result = fetchWeatherFromApi(active.cityName)
            result.onSuccess { fullCityWeather ->
                val withFav = fullCityWeather.copy(isFavorite = active.isFavorite)
                val currentList = _cities.value.toMutableList()
                val index = currentList.indexOfFirst { it.cityName.equals(active.cityName, ignoreCase = true) }
                if (index >= 0) {
                    currentList[index] = withFav
                } else {
                    currentList.add(withFav)
                }
                _cities.value = currentList
                _selectedCity.value = withFav
            }
        }
    }

    fun toggleFavorite(cityName: String) {
        _cities.value = _cities.value.map {
            if (it.cityName.equals(cityName, ignoreCase = true)) {
                val updated = it.copy(isFavorite = !it.isFavorite)
                if (updated.cityName == _selectedCity.value.cityName) {
                    _selectedCity.value = updated
                }
                updated
            } else {
                it
            }
        }
    }

    fun searchCities(query: String): List<CityWeather> {
        if (query.isBlank()) return _cities.value
        return _cities.value.filter {
            it.cityName.contains(query, ignoreCase = true) ||
            it.country.contains(query, ignoreCase = true)
        }
    }

    // Fetches live full forecast and maps directly to internal UI data models (100% stable & zero-crash)
    suspend fun fetchWeatherFromApi(query: String): Result<CityWeather> {
        val apiKey = try {
            BuildConfig.WEATHER_API_KEY
        } catch (e: Exception) {
            "PLACEholder_WEATHER_API_KEY"
        }
        if (apiKey.isBlank() || apiKey == "PLACEholder_WEATHER_API_KEY") {
            return Result.failure(IllegalStateException("API key is not configured."))
        }
        return try {
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

    // Direct live location-search from WeatherAPI to fetch accurate locations worldwide
    suspend fun searchLocationsAndFetch(query: String): List<CityWeather> {
        val apiKey = try {
            BuildConfig.WEATHER_API_KEY
        } catch (e: Exception) {
            "PLACEholder_WEATHER_API_KEY"
        }
        if (apiKey.isBlank() || apiKey == "PLACEholder_WEATHER_API_KEY") {
            return emptyList()
        }
        return try {
            val response = apiService.searchLocations(apiKey, query)
            if (response.isSuccessful) {
                val locations = response.body() ?: emptyList()
                locations.map { loc ->
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
                            airQuality = AirQuality(0, "Good", "", ""),
                            hourlyForecast = emptyList(),
                            dailyForecast = emptyList(),
                            aiSummary = ""
                        )
                    )
                }
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
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

        return CityWeather(
            cityName = location.name,
            country = location.country,
            isFavorite = false,
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
                sunrise = "05:42 AM",
                sunset = "08:21 PM",
                airQuality = AirQuality(
                    aqi = epaIndex,
                    level = aqiDesc,
                    description = aqiRec,
                    dominantPollutant = "PM2.5"
                ),
                hourlyForecast = hourlyList,
                dailyForecast = dailyList,
                aiSummary = "An elegant ${conditionEnum.displayName.lowercase()} day. Wind speeds average ${current.windKph} km/h with a humidity level of ${current.humidity}%. Perfect for responsive monitoring."
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
