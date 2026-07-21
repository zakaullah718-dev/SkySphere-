package com.example.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

class WeatherRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("skysphere_prefs", Context.MODE_PRIVATE)

    // Global settings flows (preserved from original)
    private val _isCelsius = MutableStateFlow(true)
    val isCelsius = _isCelsius.asStateFlow()

    private val _isUpdating = MutableStateFlow(false)
    val isUpdating = _isUpdating.asStateFlow()

    private val _windUnit = MutableStateFlow("km/h")
    val windUnit = _windUnit.asStateFlow()

    private fun isFahrenheitCountry(country: String): Boolean {
        if (country.isBlank()) return false
        val c = country.trim().lowercase()
        val fahrenheitCountries = listOf(
            "united states", "usa", "us", "united states of america",
            "bahamas", "belize", "cayman islands", "palau",
            "micronesia", "federated states of micronesia",
            "marshall islands", "guam", "puerto rico", "virgin islands", "american samoa"
        )
        return fahrenheitCountries.any { c == it || c.contains(it) || it.contains(c) }
    }

    private fun updateUnitForCountryIfNeeded(country: String) {
        val userManualSet = prefs.getBoolean("user_manual_unit_set", false)
        if (!userManualSet) {
            val autoCelsius = !isFahrenheitCountry(country)
            _isCelsius.value = autoCelsius
            prefs.edit().putBoolean("is_celsius", autoCelsius).apply()
        }
    }

    fun setCelsius(enabled: Boolean) {
        _isCelsius.value = enabled
        prefs.edit()
            .putBoolean("user_manual_unit_set", true)
            .putBoolean("is_celsius", enabled)
            .apply()
    }

    fun setWindUnit(unit: String) {
        _windUnit.value = unit
    }

    private val _isGpsActive = MutableStateFlow(false)
    val isGpsActive = _isGpsActive.asStateFlow()

    private val _repositoryError = MutableStateFlow<String?>(null)
    val repositoryError = _repositoryError.asStateFlow()

    fun clearRepositoryError() {
        _repositoryError.value = null
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
                                localTime = null, // recalculated dynamically on load if needed
                                region = cached.region
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
                        updateUnitForCountryIfNeeded(firstFav.country)
                    }
                }
            }
        }
        initAutoRefresh()
    }

    private fun initAutoRefresh() {
        registerNetworkCallback()
        startPeriodic30MinRefresh()
    }

    private fun registerNetworkCallback() {
        try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (connectivityManager != null) {
                val request = NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()
                connectivityManager.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
                    private var wasDisconnected = false

                    override fun onAvailable(network: Network) {
                        if (wasDisconnected) {
                            wasDisconnected = false
                            CoroutineScope(Dispatchers.IO).launch {
                                forceRefreshActiveCity()
                            }
                        }
                    }

                    override fun onLost(network: Network) {
                        wasDisconnected = true
                    }
                })
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startPeriodic30MinRefresh() {
        CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                delay(30 * 60 * 1000L)
                forceRefreshActiveCity()
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
        val uniqueId = if (!city.region.isNullOrBlank()) {
            "${city.cityName.lowercase()},${city.region.lowercase()},${city.country.lowercase()}"
        } else {
            "${city.cityName.lowercase()},${city.country.lowercase()}"
        }
        val entity = CachedWeatherEntity(
            id = uniqueId,
            cityName = city.cityName,
            country = city.country,
            weatherJson = json,
            isFavorite = city.isFavorite,
            timestamp = System.currentTimeMillis(),
            region = city.region
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
        _isGpsActive.value = false
        var lookupName = cityName
        if (cityName.startsWith("COORDS:")) {
            try {
                val parts = cityName.substring(7).split("|")
                lookupName = parts.getOrNull(1) ?: cityName
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        val existing = _cities.value.find { it.cityName.equals(lookupName, ignoreCase = true) }
        if (existing != null && existing.weatherDetails.currentTemp != 0) {
            _selectedCity.value = existing
            updateUnitForCountryIfNeeded(existing.country)
        }
        
        CoroutineScope(Dispatchers.IO).launch {
            _isUpdating.value = true
            try {
                val result = fetchWeatherFromApi(cityName, forceRefresh = false)
                result.onSuccess { fullCityWeather ->
                    val withFav = fullCityWeather.copy(isFavorite = existing?.isFavorite ?: false)
                    saveCityToCache(withFav)
                    _selectedCity.value = withFav
                    updateUnitForCountryIfNeeded(withFav.country)
                }
            } finally {
                _isUpdating.value = false
            }
        }
    }

    data class GeocodedLocation(
        val city: String,
        val region: String?,
        val country: String
    )

    private val defaultCitiesCoordinates = mapOf(
        "London" to Pair(51.5074, -0.1278),
        "New York" to Pair(40.7128, -74.0060),
        "Tokyo" to Pair(35.6762, 139.6503),
        "Paris" to Pair(48.8566, 2.3522),
        "Sydney" to Pair(-33.8688, 151.2093),
        "Cairo" to Pair(30.0444, 31.2357),
        "Rio de Janeiro" to Pair(-22.9068, -43.1729),
        "Cape Town" to Pair(-33.9249, 18.4241),
        "Mumbai" to Pair(19.0760, 72.8777),
        "Dubai" to Pair(25.2048, 55.2708),
        "Moscow" to Pair(55.7558, 37.6173),
        "Singapore" to Pair(1.3521, 103.8198),
        "Los Angeles" to Pair(34.0522, -118.2437),
        "Toronto" to Pair(43.6532, -79.3832),
        "Berlin" to Pair(52.5200, 13.4050),
        "Rome" to Pair(41.9028, 12.4964),
        "Beijing" to Pair(39.9042, 116.4074),
        "Sao Paulo" to Pair(-23.5505, -46.6333),
        "Buenos Aires" to Pair(-34.6037, -58.3816),
        "Bangkok" to Pair(13.7563, 100.5018),
        "Nairobi" to Pair(-1.2921, 36.8219),
        "Istanbul" to Pair(41.0082, 28.9784)
    )

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0 // Earth's radius in km
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return r * c
    }

    private fun findNearestValidCity(latitude: Double, longitude: Double): String {
        var closestCity = "London"
        var minDistance = Double.MAX_VALUE
        for ((city, coords) in defaultCitiesCoordinates) {
            val dist = calculateDistance(latitude, longitude, coords.first, coords.second)
            if (dist < minDistance) {
                minDistance = dist
                closestCity = city
            }
        }
        return closestCity
    }

    private suspend fun reverseGeocode(latitude: Double, longitude: Double): GeocodedLocation? = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.bigdatacloud.net/data/reverse-geocode-client?latitude=$latitude&longitude=$longitude&localityLanguage=en"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "SkySphere/1.0")
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (body != null) {
                        val json = JSONObject(body)
                        val city = json.optString("city").takeIf { it.isNotBlank() }
                            ?: json.optString("locality").takeIf { it.isNotBlank() }
                        val country = json.optString("countryName").takeIf { it.isNotBlank() } ?: "Unknown"
                        val region = json.optString("principalSubdivision").takeIf { it.isNotBlank() }
                        if (!city.isNullOrBlank()) {
                            return@withContext GeocodedLocation(city = city, region = region, country = country)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            val url = "https://nominatim.openstreetmap.org/reverse?format=json&lat=$latitude&lon=$longitude&zoom=10&addressdetails=1"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "SkySphere/1.0 (zakaullah718@gmail.com)")
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (body != null) {
                        val json = JSONObject(body)
                        val address = json.optJSONObject("address")
                        if (address != null) {
                            val city = address.optString("city").takeIf { it.isNotBlank() }
                                ?: address.optString("town").takeIf { it.isNotBlank() }
                                ?: address.optString("village").takeIf { it.isNotBlank() }
                                ?: address.optString("municipality").takeIf { it.isNotBlank() }
                                ?: address.optString("county").takeIf { it.isNotBlank() }
                            val country = address.optString("country").takeIf { it.isNotBlank() } ?: "Unknown"
                            val region = address.optString("state").takeIf { it.isNotBlank() }
                                ?: address.optString("region").takeIf { it.isNotBlank() }
                            if (!city.isNullOrBlank()) {
                                return@withContext GeocodedLocation(city = city, region = region, country = country)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return@withContext null
    }

    fun selectLocationCoordinates(latitude: Double, longitude: Double) {
        _isGpsActive.value = true
        CoroutineScope(Dispatchers.IO).launch {
            _isUpdating.value = true
            try {
                val resolved = reverseGeocode(latitude, longitude)
                if (resolved == null) {
                    val nearestCityName = findNearestValidCity(latitude, longitude)
                    _repositoryError.value = "GPS could not determine city name. Showing nearest city: $nearestCityName"
                    val result = fetchWeatherFromApi(nearestCityName, forceRefresh = true)
                    result.onSuccess { fullCityWeather ->
                        _selectedCity.value = fullCityWeather
                        updateUnitForCountryIfNeeded(fullCityWeather.country)
                    }
                    result.onFailure {
                        _repositoryError.value = "Failed to load weather for nearest city: $nearestCityName"
                    }
                } else {
                    val query = "$latitude,$longitude"
                    val result = fetchWeatherFromApi(query, forceRefresh = true)
                    result.onSuccess { fullCityWeather ->
                        val friendlyCity = fullCityWeather.copy(
                            cityName = resolved.city,
                            region = resolved.region,
                            country = resolved.country
                        )
                        saveCityToCache(friendlyCity)
                        _selectedCity.value = friendlyCity
                        updateUnitForCountryIfNeeded(friendlyCity.country)
                    }
                    result.onFailure {
                        val cityResult = fetchWeatherFromApi(resolved.city, forceRefresh = true)
                        cityResult.onSuccess { fullCityWeather ->
                            val friendlyCity = fullCityWeather.copy(
                                region = resolved.region,
                                country = resolved.country
                            )
                            saveCityToCache(friendlyCity)
                            _selectedCity.value = friendlyCity
                            updateUnitForCountryIfNeeded(friendlyCity.country)
                        }
                        cityResult.onFailure {
                            _repositoryError.value = "Failed to fetch weather data for ${resolved.city}."
                        }
                    }
                }
            } finally {
                _isUpdating.value = false
            }
        }
    }

    suspend fun forceRefreshActiveCity() {
        val active = _selectedCity.value
        if (active.cityName == "Loading...") return
        _isUpdating.value = true
        try {
            val result = fetchWeatherFromApi(active.cityName, forceRefresh = true)
            result.onSuccess { fullCityWeather ->
                val withFav = fullCityWeather.copy(isFavorite = active.isFavorite)
                saveCityToCache(withFav)
                _selectedCity.value = withFav
                updateUnitForCountryIfNeeded(withFav.country)
            }
        } finally {
            _isUpdating.value = false
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
