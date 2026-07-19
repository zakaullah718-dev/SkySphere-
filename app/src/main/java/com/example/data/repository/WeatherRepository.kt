package com.example.data.repository

import android.content.Context
import androidx.room.Room
import com.example.BuildConfig
import com.example.data.api.OpenMeteoProvider
import com.example.data.api.OpenWeatherProvider
import com.example.data.api.WeatherApiProvider
import com.example.data.api.WeatherProvider
import com.example.data.db.AppDatabase
import com.example.data.db.CachedWeatherEntity
import com.example.data.db.RecentSearchEntity
import com.example.data.models.AirQuality
import com.example.data.models.CityWeather
import com.example.data.models.WeatherCondition
import com.example.data.models.WeatherDetails
import com.example.weather.data.api.WeatherApiService
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

class WeatherRepository(private val context: Context) {

    // Global settings flows (preserved from original)
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

    // Provider settings
    enum class ProviderType {
        WEATHER_API_COM,
        OPEN_METEO,
        OPEN_WEATHER
    }

    private val _selectedProvider = MutableStateFlow(ProviderType.OPEN_METEO)
    val selectedProvider = _selectedProvider.asStateFlow()

    fun setProvider(provider: ProviderType) {
        _selectedProvider.value = provider
        CoroutineScope(Dispatchers.IO).launch {
            forceRefreshActiveCity()
        }
    }

    // Initialize Database
    private val database = Room.databaseBuilder(
        context.applicationContext,
        AppDatabase::class.java,
        "skysphere_weather.db"
    ).fallbackToDestructiveMigration().build()

    private val weatherDao = database.weatherDao()
    private val recentSearchDao = database.recentSearchDao()

    // Setup network clients
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://api.weatherapi.com/v1/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    private val apiService = retrofit.create(WeatherApiService::class.java)

    // Instantiating the weather providers
    private val weatherApiProvider = WeatherApiProvider(apiService)
    private val openMeteoProvider = OpenMeteoProvider(okHttpClient, moshi)
    private val openWeatherProvider = OpenWeatherProvider(okHttpClient, moshi)

    private fun getProvider(): WeatherProvider {
        val apiKey = try {
            BuildConfig.WEATHER_API_KEY
        } catch (e: Exception) {
            ""
        }
        val isKeyConfigured = apiKey.isNotBlank() && apiKey != "PLACEholder_WEATHER_API_KEY"

        return when (_selectedProvider.value) {
            ProviderType.WEATHER_API_COM -> {
                if (isKeyConfigured) weatherApiProvider else openMeteoProvider
            }
            ProviderType.OPEN_WEATHER -> {
                if (isKeyConfigured) openWeatherProvider else openMeteoProvider
            }
            ProviderType.OPEN_METEO -> openMeteoProvider
        }
    }

    // Default loading placeholder city state
    private val defaultPlaceholder = CityWeather(
        cityName = "Loading...",
        country = "",
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
            aiSummary = "Fetching atmospheric details..."
        )
    )

    private val _cities = MutableStateFlow<List<CityWeather>>(emptyList())
    
    private val _selectedCity = MutableStateFlow<CityWeather>(defaultPlaceholder)
    val selectedCity = _selectedCity.asStateFlow()

    init {
        CoroutineScope(Dispatchers.IO).launch {
            // Observe Room's cached weather records and stream to UI components reactively
            weatherDao.getAllCachedWeatherFlow().collect { cachedList ->
                val mappedList = cachedList.mapNotNull { cached ->
                    try {
                        val details = moshi.adapter(WeatherDetails::class.java).fromJson(cached.weatherJson)
                        if (details != null) {
                            CityWeather(
                                cityName = cached.cityName,
                                country = cached.country,
                                isFavorite = cached.isFavorite,
                                weatherDetails = details,
                                localTime = null // recalculated dynamically on load if needed
                            )
                        } else null
                    } catch (e: Exception) {
                        null
                    }
                }

                if (mappedList.isEmpty()) {
                    seedDefaultCities()
                } else {
                    _cities.value = mappedList
                    if (_selectedCity.value.cityName == "Loading...") {
                        val firstFav = mappedList.find { it.isFavorite } ?: mappedList.first()
                        _selectedCity.value = firstFav
                    }
                }
            }
        }
    }

    private suspend fun seedDefaultCities() {
        val defaults = listOf("London", "New York", "Tokyo", "Paris")
        val seeded = mutableListOf<CityWeather>()
        for (cityName in defaults) {
            val res = getProvider().getForecast(cityName)
            res.onSuccess { weather ->
                val withFav = weather.copy(isFavorite = true)
                saveCityToCache(withFav)
                seeded.add(withFav)
            }.onFailure {
                it.printStackTrace()
            }
        }
        if (seeded.isNotEmpty()) {
            _cities.value = seeded
            _selectedCity.value = seeded.first()
        }
    }

    private suspend fun saveCityToCache(city: CityWeather) {
        val json = moshi.adapter(WeatherDetails::class.java).toJson(city.weatherDetails)
        val entity = CachedWeatherEntity(
            id = city.cityName.lowercase(),
            cityName = city.cityName,
            country = city.country,
            weatherJson = json,
            isFavorite = city.isFavorite,
            timestamp = System.currentTimeMillis()
        )
        weatherDao.insertCachedWeather(entity)
    }

    fun getCitiesFlow(): Flow<List<CityWeather>> = _cities.asStateFlow()

    fun getFavoritesFlow(): Flow<List<CityWeather>> = _cities.map { list ->
        list.filter { it.isFavorite }
    }

    // Expose Room-based Recent Searches Flow
    fun getRecentSearchesFlow(): Flow<List<String>> {
        return recentSearchDao.getRecentSearchesFlow().map { list ->
            list.map { it.query }
        }
    }

    suspend fun saveRecentSearch(query: String) {
        if (query.isNotBlank()) {
            recentSearchDao.insertRecentSearch(
                RecentSearchEntity(query.trim(), System.currentTimeMillis())
            )
        }
    }

    suspend fun deleteRecentSearch(query: String) {
        recentSearchDao.deleteRecentSearch(query)
    }

    suspend fun clearRecentSearches() {
        recentSearchDao.clearAll()
    }

    fun selectCity(cityName: String) {
        val existing = _cities.value.find { it.cityName.equals(cityName, ignoreCase = true) }
        if (existing != null && existing.weatherDetails.currentTemp != 0) {
            _selectedCity.value = existing
        }
        
        CoroutineScope(Dispatchers.IO).launch {
            val result = fetchWeatherFromApi(cityName, forceRefresh = false)
            result.onSuccess { fullCityWeather ->
                val withFav = fullCityWeather.copy(isFavorite = existing?.isFavorite ?: false)
                saveCityToCache(withFav)
                _selectedCity.value = withFav
            }
        }
    }

    fun selectLocationCoordinates(latitude: Double, longitude: Double) {
        val query = "$latitude,$longitude"
        CoroutineScope(Dispatchers.IO).launch {
            val result = fetchWeatherFromApi(query, forceRefresh = true)
            result.onSuccess { fullCityWeather ->
                val friendlyCity = fullCityWeather.copy(
                    cityName = if (fullCityWeather.cityName.isBlank()) "Current Location" else fullCityWeather.cityName
                )
                saveCityToCache(friendlyCity)
                _selectedCity.value = friendlyCity
            }
        }
    }

    suspend fun forceRefreshActiveCity() {
        val active = _selectedCity.value
        if (active.cityName == "Loading...") return
        val result = fetchWeatherFromApi(active.cityName, forceRefresh = true)
        result.onSuccess { fullCityWeather ->
            val withFav = fullCityWeather.copy(isFavorite = active.isFavorite)
            saveCityToCache(withFav)
            _selectedCity.value = withFav
        }
    }

    fun toggleFavorite(cityName: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val updated = _cities.value.map {
                if (it.cityName.equals(cityName, ignoreCase = true)) {
                    val newFavState = !it.isFavorite
                    weatherDao.updateFavorite(cityName.lowercase(), newFavState)
                    val updatedCity = it.copy(isFavorite = newFavState)
                    if (updatedCity.cityName == _selectedCity.value.cityName) {
                        _selectedCity.value = updatedCity
                    }
                    updatedCity
                } else {
                    it
                }
            }
            _cities.value = updated
        }
    }

    fun searchCities(query: String): List<CityWeather> {
        if (query.isBlank()) return _cities.value
        return _cities.value.filter {
            it.cityName.contains(query, ignoreCase = true) ||
            it.country.contains(query, ignoreCase = true)
        }
    }

    // Performance Caching Strategy: checks Room cache age (30 minutes expiry limit)
    suspend fun fetchWeatherFromApi(query: String, forceRefresh: Boolean = false): Result<CityWeather> {
        val cacheKey = query.lowercase()
        val cached = weatherDao.getCachedWeather(cacheKey)
        
        if (cached != null && !forceRefresh) {
            val cacheAge = System.currentTimeMillis() - cached.timestamp
            if (cacheAge < 1800000L) { // 30 minutes cache validation
                try {
                    val details = moshi.adapter(WeatherDetails::class.java).fromJson(cached.weatherJson)
                    if (details != null) {
                        return Result.success(
                            CityWeather(
                                cityName = cached.cityName,
                                country = cached.country,
                                isFavorite = cached.isFavorite,
                                weatherDetails = details
                            )
                        )
                    }
                } catch (e: Exception) {
                    // fallback to network fetch
                }
            }
        }

        // Fetch from the real remote api provider
        val result = getProvider().getForecast(query)
        result.onSuccess { freshWeather ->
            val finalWeather = if (cached != null) {
                freshWeather.copy(isFavorite = cached.isFavorite)
            } else {
                freshWeather
            }
            saveCityToCache(finalWeather)
        }
        return result
    }

    // Live geocoding search for worldwide location detection and autocomplete
    suspend fun searchLocationsAndFetch(query: String): List<CityWeather> {
        if (query.isBlank() || query.length < 2) return emptyList()
        val result = getProvider().searchLocations(query)
        return result.getOrDefault(emptyList())
    }
}
