package com.example.data.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

class WeatherRepository(private val context: Context) {

    companion object {
        @Volatile
        private var INSTANCE: WeatherRepository? = null

        fun getInstance(context: Context): WeatherRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: WeatherRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val prefs = context.getSharedPreferences("skysphere_prefs", Context.MODE_PRIVATE)

    // Global settings flows (preserved from original)
    private val _isCelsius = MutableStateFlow(prefs.getBoolean("is_celsius", true))
    val isCelsius = _isCelsius.asStateFlow()

    private val _isUpdating = MutableStateFlow(false)
    val isUpdating = _isUpdating.asStateFlow()

    private val _windUnit = MutableStateFlow(prefs.getString("wind_unit", "km/h") ?: "km/h")
    val windUnit = _windUnit.asStateFlow()

    private val _appTheme = MutableStateFlow(prefs.getString("app_theme", "MIDNIGHT_BLUE") ?: "MIDNIGHT_BLUE")
    val appTheme = _appTheme.asStateFlow()

    fun setAppTheme(themeId: String) {
        _appTheme.value = themeId
        prefs.edit().putString("app_theme", themeId).apply()
    }

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

    private val initialProviderName = prefs.getString("selected_provider", ProviderType.OPEN_METEO.name) ?: ProviderType.OPEN_METEO.name
    private val initialProvider = try { ProviderType.valueOf(initialProviderName) } catch (e: Exception) { ProviderType.OPEN_METEO }
    private val _selectedProvider = MutableStateFlow(initialProvider)
    val selectedProvider = _selectedProvider.asStateFlow()

    fun setProvider(provider: ProviderType) {
        _selectedProvider.value = provider
        prefs.edit().putString("selected_provider", provider.name).apply()
        CoroutineScope(Dispatchers.IO).launch {
            forceRefreshActiveCity()
        }
    }

    // Initialize Database
    private val database = AppDatabase.getDatabase(context)

    private val weatherDao = database.weatherDao()
    private val recentSearchDao = database.recentSearchDao()
    val radarMetadataDao = database.radarMetadataDao()

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

    private fun updateSelectedCity(city: CityWeather, source: String) {
        _selectedCity.value = city
        logWeatherSync(
            source = source,
            cityName = city.cityName,
            country = city.country,
            provider = _selectedProvider.value,
            condition = city.weatherDetails.condition,
            currentTemp = city.weatherDetails.currentTemp,
            feelsLike = city.weatherDetails.feelsLike,
            humidity = city.weatherDetails.humidity,
            windSpeed = city.weatherDetails.windSpeed,
            pressure = city.weatherDetails.pressureHpa
        )
        try {
            com.example.widget.SkySphereWidgetManager.updateAllWidgets(context)
        } catch (e: Exception) {
            Log.w("WeatherRepository", "Widget update trigger failed: ${e.message}")
        }
    }

    private fun logWeatherSync(
        source: String,
        cityName: String,
        country: String,
        provider: ProviderType,
        condition: WeatherCondition,
        currentTemp: Int,
        feelsLike: Int,
        humidity: Int,
        windSpeed: Double,
        pressure: Int
    ) {
        Log.d(
            "SkySphereWeatherSync",
            """
            ====================================================
            [SkySphere Single Source of Truth Sync Log]
            Source/Caller  : $source
            Provider Used  : $provider
            Location       : $cityName, $country
            Condition      : $condition
            Current Temp   : ${currentTemp}°F (${feelsLike}°F feels like)
            Humidity       : ${humidity}%
            Wind Speed     : $windSpeed
            Pressure       : $pressure hPa
            Timestamp      : ${System.currentTimeMillis()}
            ====================================================
            """.trimIndent()
        )
    }

    suspend fun getOrFetchActiveCity(): CityWeather = withContext(Dispatchers.IO) {
        val current = _selectedCity.value
        if (current.cityName != "Loading..." && current.cityName.isNotBlank()) {
            return@withContext current
        }

        try {
            val cachedList = weatherDao.getAllCachedWeather()
            if (cachedList.isNotEmpty()) {
                val mappedList = cachedList.mapNotNull { cached ->
                    try {
                        val details = moshi.adapter(WeatherDetails::class.java).fromJson(cached.weatherJson)
                        if (details != null) {
                            CityWeather(
                                cityName = cached.cityName,
                                country = cached.country,
                                isFavorite = cached.isFavorite,
                                weatherDetails = alignWeatherDetailsHourly(details),
                                region = cached.region
                            )
                        } else null
                    } catch (e: Exception) {
                        null
                    }
                }
                val restored = mappedList.find { it.isFavorite } ?: mappedList.firstOrNull()
                if (restored != null) {
                    updateSelectedCity(restored, "RestoredFromCache")
                    return@withContext restored
                }
            }
        } catch (e: Exception) {
            // fallback seed
        }

        // Seed default if empty
        seedDefaultCities()
        delay(200)
        _selectedCity.value
    }

    private fun saveLastSelectedLocation(
        cityName: String,
        isGps: Boolean,
        lat: Double? = null,
        lon: Double? = null,
        region: String? = null,
        country: String? = null
    ) {
        val editor = prefs.edit()
            .putString("last_selected_city", cityName)
            .putBoolean("last_selected_is_gps", isGps)
        
        if (lat != null && lon != null) {
            editor.putFloat("last_gps_lat", lat.toFloat())
            editor.putFloat("last_gps_lon", lon.toFloat())
        }
        if (!region.isNullOrBlank()) {
            editor.putString("last_selected_region", region)
        } else {
            editor.remove("last_selected_region")
        }
        if (!country.isNullOrBlank()) {
            editor.putString("last_selected_country", country)
        } else {
            editor.remove("last_selected_country")
        }
        editor.apply()
    }

    init {
        CoroutineScope(Dispatchers.IO).launch {
            // Observe Room's cached weather records and stream to UI components reactively
            weatherDao.getAllCachedWeatherFlow().collect { cachedList ->
                val mappedList = cachedList.mapNotNull { cached ->
                    try {
                        val details = moshi.adapter(WeatherDetails::class.java).fromJson(cached.weatherJson)
                        if (details != null) {
                            val alignedDetails = alignWeatherDetailsHourly(details)
                            CityWeather(
                                cityName = cached.cityName,
                                country = cached.country,
                                isFavorite = cached.isFavorite,
                                weatherDetails = alignedDetails,
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
                        val lastCityName = prefs.getString("last_selected_city", null)
                        val lastIsGps = prefs.getBoolean("last_selected_is_gps", false)
                        val lastLat = if (prefs.contains("last_gps_lat")) prefs.getFloat("last_gps_lat", 0f).toDouble() else null
                        val lastLon = if (prefs.contains("last_gps_lon")) prefs.getFloat("last_gps_lon", 0f).toDouble() else null

                        _isGpsActive.value = lastIsGps

                        var restoredCity: CityWeather? = null
                        if (!lastCityName.isNullOrBlank()) {
                            restoredCity = mappedList.find { it.cityName.equals(lastCityName, ignoreCase = true) }
                        }

                        if (restoredCity != null) {
                            updateSelectedCity(restoredCity, "RoomCacheInit")
                            updateUnitForCountryIfNeeded(restoredCity.country)
                        } else if (lastIsGps && lastLat != null && lastLon != null) {
                            val fallback = mappedList.find { it.isFavorite } ?: mappedList.first()
                            updateSelectedCity(fallback, "RoomCacheFallbackGps")
                            updateUnitForCountryIfNeeded(fallback.country)
                            selectLocationCoordinates(lastLat, lastLon)
                        } else if (!lastCityName.isNullOrBlank()) {
                            val fallback = mappedList.find { it.isFavorite } ?: mappedList.first()
                            updateSelectedCity(fallback, "RoomCacheFallbackCity")
                            updateUnitForCountryIfNeeded(fallback.country)
                            selectCity(lastCityName)
                        } else {
                            val fallback = mappedList.find { it.isFavorite } ?: mappedList.first()
                            updateSelectedCity(fallback, "RoomCacheFallbackDefault")
                            updateUnitForCountryIfNeeded(fallback.country)
                        }
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
            updateSelectedCity(seeded.first(), "SeedDefaultCities")
            saveLastSelectedLocation(
                cityName = seeded.first().cityName,
                isGps = false,
                country = seeded.first().country
            )
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

    suspend fun getCityByNameFromFavoritesOrApi(cityName: String): CityWeather? {
        val existing = _cities.value.find { it.cityName.equals(cityName, ignoreCase = true) }
        if (existing != null && existing.weatherDetails.currentTemp != 0) {
            return existing
        }
        val result = fetchWeatherFromApi(cityName, forceRefresh = false)
        return result.getOrNull()
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
            updateSelectedCity(existing, "SelectCityMemory")
            updateUnitForCountryIfNeeded(existing.country)
            saveLastSelectedLocation(
                cityName = existing.cityName,
                isGps = false,
                region = existing.region,
                country = existing.country
            )
        }
        
        CoroutineScope(Dispatchers.IO).launch {
            _isUpdating.value = true
            try {
                val result = fetchWeatherFromApi(cityName, forceRefresh = false)
                result.onSuccess { fullCityWeather ->
                    val withFav = fullCityWeather.copy(isFavorite = existing?.isFavorite ?: false)
                    saveCityToCache(withFav)
                    updateSelectedCity(withFav, "SelectCityApi")
                    updateUnitForCountryIfNeeded(withFav.country)
                    saveLastSelectedLocation(
                        cityName = withFav.cityName,
                        isGps = false,
                        region = withFav.region,
                        country = withFav.country
                    )
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
                        updateSelectedCity(fullCityWeather, "GPSNearestCityApi")
                        updateUnitForCountryIfNeeded(fullCityWeather.country)
                        saveLastSelectedLocation(
                            cityName = fullCityWeather.cityName,
                            isGps = true,
                            lat = latitude,
                            lon = longitude,
                            region = fullCityWeather.region,
                            country = fullCityWeather.country
                        )
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
                        updateSelectedCity(friendlyCity, "GPSReverseGeocodeApi")
                        updateUnitForCountryIfNeeded(friendlyCity.country)
                        saveLastSelectedLocation(
                            cityName = friendlyCity.cityName,
                            isGps = true,
                            lat = latitude,
                            lon = longitude,
                            region = friendlyCity.region,
                            country = friendlyCity.country
                        )
                    }
                    result.onFailure {
                        val cityResult = fetchWeatherFromApi(resolved.city, forceRefresh = true)
                        cityResult.onSuccess { fullCityWeather ->
                            val friendlyCity = fullCityWeather.copy(
                                region = resolved.region,
                                country = resolved.country
                            )
                            saveCityToCache(friendlyCity)
                            updateSelectedCity(friendlyCity, "GPSCityFallbackApi")
                            updateUnitForCountryIfNeeded(friendlyCity.country)
                            saveLastSelectedLocation(
                                cityName = friendlyCity.cityName,
                                isGps = true,
                                lat = latitude,
                                lon = longitude,
                                region = friendlyCity.region,
                                country = friendlyCity.country
                            )
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
                val alignedDetails = alignWeatherDetailsHourly(fullCityWeather.weatherDetails)
                val finalHourly = if (alignedDetails.hourlyForecast.isNotEmpty()) {
                    alignedDetails.hourlyForecast
                } else {
                    active.weatherDetails.hourlyForecast
                }
                val finalDaily = if (alignedDetails.dailyForecast.isNotEmpty()) {
                    alignedDetails.dailyForecast
                } else {
                    active.weatherDetails.dailyForecast
                }

                val refreshedCity = fullCityWeather.copy(
                    isFavorite = active.isFavorite,
                    weatherDetails = alignedDetails.copy(
                        hourlyForecast = finalHourly,
                        dailyForecast = finalDaily
                    )
                )
                saveCityToCache(refreshedCity)
                updateSelectedCity(refreshedCity, "ForceRefreshActiveCity")
                updateUnitForCountryIfNeeded(refreshedCity.country)
            }
            result.onFailure { error ->
                _repositoryError.value = "Failed to refresh weather for ${active.cityName}: ${error.localizedMessage ?: "Network connection error"}"
            }
        } catch (e: Exception) {
            _repositoryError.value = "Refresh error: ${e.localizedMessage ?: "Unable to update weather"}"
        } finally {
            _isUpdating.value = false
        }
    }

    suspend fun refreshLiveWeatherForNotification(context: Context): CityWeather? = withContext(Dispatchers.IO) {
        val isGpsActiveMode = _isGpsActive.value || prefs.getBoolean("last_selected_is_gps", false)
        
        // Priority 1: Current GPS location ONLY if GPS mode is active
        if (isGpsActiveMode) {
            val gpsCoords = getDeviceLocation(context)
            if (gpsCoords != null) {
                val (lat, lon) = gpsCoords
                try {
                    val resolved = reverseGeocode(lat, lon)
                    val query = "$lat,$lon"
                    val result = fetchWeatherFromApi(query, forceRefresh = true)
                    if (result.isSuccess) {
                        val fullCityWeather = result.getOrThrow()
                        val finalCityName = resolved?.city ?: fullCityWeather.cityName
                        val finalRegion = resolved?.region ?: fullCityWeather.region
                        val finalCountry = resolved?.country ?: fullCityWeather.country

                        val liveCity = fullCityWeather.copy(
                            cityName = finalCityName,
                            region = finalRegion,
                            country = finalCountry,
                            latitude = lat,
                            longitude = lon
                        )
                        saveCityToCache(liveCity)
                        updateSelectedCity(liveCity, "NotificationGPSRefresh")
                        updateUnitForCountryIfNeeded(liveCity.country)
                        saveLastSelectedLocation(
                            cityName = liveCity.cityName,
                            isGps = true,
                            lat = lat,
                            lon = lon,
                            region = liveCity.region,
                            country = liveCity.country
                        )
                        Log.d("WeatherRepository", "Notification weather updated via GPS: ${liveCity.cityName}")
                        return@withContext liveCity
                    }
                } catch (e: Exception) {
                    Log.w("WeatherRepository", "GPS weather refresh failed for notification: ${e.message}")
                }
            }
        }

        // Priority 2: Last city selected by the user
        val lastCityName = prefs.getString("last_selected_city", null)
        val currentSelectedName = _selectedCity.value.cityName.takeIf { it != "Loading..." && it.isNotBlank() }
        val targetCityName = currentSelectedName ?: lastCityName

        if (!targetCityName.isNullOrBlank()) {
            try {
                val result = fetchWeatherFromApi(targetCityName, forceRefresh = true)
                if (result.isSuccess) {
                    val fullCityWeather = result.getOrThrow()
                    val alignedDetails = alignWeatherDetailsHourly(fullCityWeather.weatherDetails)
                    val refreshedCity = fullCityWeather.copy(weatherDetails = alignedDetails)
                    saveCityToCache(refreshedCity)
                    updateSelectedCity(refreshedCity, "NotificationTargetCityRefresh")
                    updateUnitForCountryIfNeeded(refreshedCity.country)
                    Log.d("WeatherRepository", "Notification weather updated via selected city: ${refreshedCity.cityName}")
                    return@withContext refreshedCity
                }
            } catch (e: Exception) {
                Log.w("WeatherRepository", "Target city weather refresh failed for notification: ${e.message}")
            }
        }

        // Priority 3: Cached location from Room database or active state
        try {
            val cachedList = weatherDao.getAllCachedWeather()
            if (cachedList.isNotEmpty()) {
                val mappedList = cachedList.mapNotNull { cached ->
                    try {
                        val details = moshi.adapter(WeatherDetails::class.java).fromJson(cached.weatherJson)
                        if (details != null && details.currentTemp != 0) {
                            CityWeather(
                                cityName = cached.cityName,
                                country = cached.country,
                                isFavorite = cached.isFavorite,
                                weatherDetails = alignWeatherDetailsHourly(details),
                                region = cached.region
                            )
                        } else null
                    } catch (e: Exception) {
                        null
                    }
                }
                val cachedCity = mappedList.find { it.cityName.equals(targetCityName, ignoreCase = true) }
                    ?: mappedList.find { it.isFavorite }
                    ?: mappedList.firstOrNull()

                if (cachedCity != null) {
                    updateSelectedCity(cachedCity, "NotificationCachedCity")
                    Log.d("WeatherRepository", "Notification weather retrieved from cache: ${cachedCity.cityName}")
                    return@withContext cachedCity
                }
            }
        } catch (e: Exception) {
            Log.e("WeatherRepository", "Error loading cached weather for notification: ${e.message}")
        }

        val active = _selectedCity.value
        if (active.cityName != "Loading..." && active.cityName.isNotBlank() && active.weatherDetails.currentTemp != 0) {
            return@withContext active
        }

        return@withContext null
    }

    private fun getDeviceLocation(context: Context): Pair<Double, Double>? {
        try {
            val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (hasFine || hasCoarse) {
                val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                if (locationManager != null && try { LocationManagerCompat.isLocationEnabled(locationManager) } catch (t: Throwable) { false }) {
                    val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
                    for (provider in providers) {
                        try {
                            if (try { locationManager.isProviderEnabled(provider) } catch (t: Throwable) { false }) {
                                val loc = locationManager.getLastKnownLocation(provider)
                                if (loc != null && loc.latitude != 0.0 && loc.longitude != 0.0) {
                                    return Pair(loc.latitude, loc.longitude)
                                }
                            }
                        } catch (t: Throwable) {
                            // ignore provider error
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            Log.w("WeatherRepository", "Device location check error: ${t.message}")
        }
        return null
    }

    fun toggleFavorite(cityName: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val updated = _cities.value.map {
                if (it.cityName.equals(cityName, ignoreCase = true)) {
                    val newFavState = !it.isFavorite
                    weatherDao.updateFavorite(cityName.lowercase(), newFavState)
                    val updatedCity = it.copy(isFavorite = newFavState)
                    if (updatedCity.cityName == _selectedCity.value.cityName) {
                        updateSelectedCity(updatedCity, "ToggleFavorite")
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
                        val hasValidTimestamps = details.hourlyForecast.any { it.timestampEpochMillis > 0L }
                        if (hasValidTimestamps) {
                            val alignedDetails = alignWeatherDetailsHourly(details)
                            return Result.success(
                                CityWeather(
                                    cityName = cached.cityName,
                                    country = cached.country,
                                    isFavorite = cached.isFavorite,
                                    weatherDetails = alignedDetails,
                                    region = cached.region
                                )
                            )
                        }
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
        result.onFailure {
            if (cached != null) {
                try {
                    val details = moshi.adapter(WeatherDetails::class.java).fromJson(cached.weatherJson)
                    if (details != null) {
                        val alignedDetails = alignWeatherDetailsHourly(details)
                        return Result.success(
                            CityWeather(
                                cityName = cached.cityName,
                                country = cached.country,
                                isFavorite = cached.isFavorite,
                                weatherDetails = alignedDetails,
                                region = cached.region
                            )
                        )
                    }
                } catch (e: Exception) {
                    // Ignore decoding error, return original failure
                }
            }
        }
        return result
    }

    private fun alignWeatherDetailsHourly(details: WeatherDetails): WeatherDetails {
        val list = details.hourlyForecast
        if (list.isEmpty()) return details
        val hasTimestamps = list.any { it.timestampEpochMillis > 0L }
        if (!hasTimestamps) return details

        val nowMillis = System.currentTimeMillis()
        val cal = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val currentHourStartMillis = cal.timeInMillis

        val futureList = list.filter { it.timestampEpochMillis >= currentHourStartMillis }
        val selectedList = if (futureList.isNotEmpty()) futureList.take(12) else list.take(12)

        val displayFormat = java.text.SimpleDateFormat("h:mm a", java.util.Locale.US)

        val reMapped = selectedList.mapIndexed { idx, hour ->
            val label = if (idx == 0) "Now" else {
                if (hour.timestampEpochMillis > 0L) {
                    displayFormat.format(java.util.Date(hour.timestampEpochMillis))
                } else {
                    hour.time
                }
            }
            val isNight = if (hour.timestampEpochMillis > 0L) {
                com.example.utils.WeatherTimeUtils.isNightForLocation(
                    timestampEpochMillis = hour.timestampEpochMillis,
                    sunriseStr = details.sunrise,
                    sunsetStr = details.sunset
                )
            } else {
                hour.isNight
            }
            hour.copy(time = label, isNight = isNight)
        }

        return details.copy(hourlyForecast = reMapped)
    }

    // Live geocoding search for worldwide location detection and autocomplete
    suspend fun searchLocationsAndFetch(query: String): List<CityWeather> {
        if (query.isBlank() || query.length < 2) return emptyList()
        val result = getProvider().searchLocations(query)
        return result.getOrDefault(emptyList())
    }
}
