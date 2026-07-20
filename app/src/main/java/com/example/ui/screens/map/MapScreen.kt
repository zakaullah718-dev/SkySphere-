package com.example.ui.screens.map

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.example.BuildConfig
import com.example.data.models.CityWeather
import com.example.data.models.WeatherCondition
import com.example.data.models.WeatherDetails
import com.example.data.repository.WeatherRepository
import com.example.ui.components.WeatherConditionIcon
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class MapLayer(val displayName: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val description: String) {
    RAINFALL("Rain Radar", Icons.Filled.WaterDrop, "Active Live Doppler precipitation radar"),
    CLOUD_COVER("Clouds", Icons.Filled.Cloud, "Multi-spectral infrared satellite cloud cover"),
    TEMPERATURE("Temperature", Icons.Filled.Thermostat, "Tropospheric thermal spectroscopy"),
    WIND_SPEED("Wind Speed", Icons.Filled.Air, "Flow streamline velocity vectors"),
    PRESSURE("Pressure", Icons.Filled.Compress, "Isobaric atmospheric surface tension"),
    HUMIDITY("Humidity", Icons.Filled.Water, "Relative tropospheric moisture concentration")
}

data class MapCity(val name: String, val lat: Float, val lon: Float, val country: String)

@Composable
fun MapScreen(
    repository: WeatherRepository,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val isCelsius by repository.isCelsius.collectAsState()

    // Modular Map Provider
    val mapProvider = remember { MapLibreWebViewProvider() }
    var isMapReady by remember { mutableStateOf(false) }

    // Screen UI overlays state
    var selectedLayer by remember { mutableStateOf(MapLayer.RAINFALL) }
    var activeLocationHeader by remember { mutableStateOf("LOADING RADAR...") }
    var searchQuery by remember { mutableStateOf("") }
    
    // GPS and location status
    var currentGpsCoords by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    
    // Bottom detail inspection sheet
    var inspectedWeather by remember { mutableStateOf<CityWeather?>(null) }
    var isInspecting by remember { mutableStateOf(false) }
    var isFetchingInspected by remember { mutableStateOf(false) }
    var targetCoords by remember { mutableStateOf<Pair<Double, Double>?>(null) }

    // Favorites list
    val favoritedCities by repository.getFavoritesFlow().collectAsState(initial = emptyList())

    // Timeline player
    var isPlayingRadar by remember { mutableStateOf(true) }
    var radarTimeSlider by remember { mutableStateOf(3f) }
    val timelineLabels = listOf("-3h", "-2h", "-1h", "Now", "+1h", "+2h", "+3h")

    // Static major worldwide cities list for quick matching
    val mapCities = remember {
        listOf(
            MapCity("London", 51.5074f, -0.1278f, "UK"),
            MapCity("New York", 40.7128f, -74.0060f, "USA"),
            MapCity("Tokyo", 35.6762f, 139.6503f, "Japan"),
            MapCity("Paris", 48.8566f, 2.3522f, "France"),
            MapCity("Sydney", -33.8688f, 151.2093f, "Australia"),
            MapCity("Cairo", 30.0444f, 31.2357f, "Egypt"),
            MapCity("Moscow", 55.7558f, 37.6173f, "Russia"),
            MapCity("Mumbai", 19.0760f, 72.8777f, "India"),
            MapCity("Rio de Janeiro", -22.9068f, -43.1729f, "Brazil"),
            MapCity("Cape Town", -33.9249f, 18.4241f, "South Africa"),
            MapCity("Beijing", 39.9042f, 116.4074f, "China"),
            MapCity("Dubai", 25.2048f, 55.2708f, "UAE"),
            MapCity("Toronto", 43.6532f, -79.3832f, "Canada"),
            MapCity("Berlin", 52.5200f, 13.4050f, "Germany"),
            MapCity("Rome", 41.9028f, 12.4964f, "Italy"),
            MapCity("Los Angeles", 34.0522f, -118.2437f, "USA"),
            MapCity("Seattle", 47.6062f, -122.3321f, "USA"),
            MapCity("Singapore", 1.3521f, 103.8198f, "Singapore"),
            MapCity("Seoul", 37.5665f, 126.9780f, "South Korea")
        )
    }

    // Timeline auto-slider
    LaunchedEffect(isPlayingRadar) {
        if (isPlayingRadar) {
            while (true) {
                delay(1200)
                radarTimeSlider = (radarTimeSlider + 1) % 7
            }
        }
    }

    // Helper to query location-specific micro-climate data
    val triggerCoordinateFetch: (Double, Double, String?) -> Unit = { lat, lon, customName ->
        targetCoords = Pair(lat, lon)
        isInspecting = true
        isFetchingInspected = true
        inspectedWeather = null
        
        coroutineScope.launch {
            val result = repository.fetchWeatherFromApi("$lat,$lon", forceRefresh = true)
            isFetchingInspected = false
            result.onSuccess { weather ->
                inspectedWeather = weather
                activeLocationHeader = "${weather.cityName.uppercase()}, ${weather.country.uppercase()}"
            }.onFailure {
                inspectedWeather = null
                activeLocationHeader = customName ?: "COORDINATES: ${String.format("%.4f", lat)}, ${String.format("%.4f", lon)}"
            }
        }
    }

    // Set up Location Manager for GPS location detection
    val locationManager = remember { context.getSystemService(Context.LOCATION_SERVICE) as LocationManager }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if ((fineGranted || coarseGranted) && LocationManagerCompat.isLocationEnabled(locationManager)) {
            try {
                val provider = when {
                    locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                    locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                    else -> null
                }
                if (provider != null) {
                    val loc = locationManager.getLastKnownLocation(provider)
                    if (loc != null) {
                        currentGpsCoords = Pair(loc.latitude, loc.longitude)
                    }
                }
            } catch (e: SecurityException) {
                e.printStackTrace()
            }
        }
    }

    // Initial load permission checking
    LaunchedEffect(Unit) {
        val fineGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (fineGranted || coarseGranted) {
            if (LocationManagerCompat.isLocationEnabled(locationManager)) {
                try {
                    val provider = when {
                        locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                        locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                        else -> null
                    }
                    if (provider != null) {
                        val loc = locationManager.getLastKnownLocation(provider)
                        if (loc != null) {
                            currentGpsCoords = Pair(loc.latitude, loc.longitude)
                        }
                    }
                } catch (e: SecurityException) {
                    e.printStackTrace()
                }
            }
        } else {
            locationPermissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    // Sync map layers and settings reactively when map is ready
    LaunchedEffect(isMapReady) {
        if (isMapReady) {
            mapProvider.setWeatherApiKey(BuildConfig.WEATHER_API_KEY)
            mapProvider.setWeatherLayer(selectedLayer)
        }
    }

    LaunchedEffect(selectedLayer, isMapReady) {
        if (isMapReady) {
            mapProvider.setWeatherLayer(selectedLayer)
        }
    }

    // Primary FlyTo centering effect
    LaunchedEffect(isMapReady, currentGpsCoords) {
        if (isMapReady) {
            val gps = currentGpsCoords
            if (gps != null) {
                mapProvider.setCenter(gps.first, gps.second, 5.0f)
                triggerCoordinateFetch(gps.first, gps.second, "CURRENT LOCATION")
            } else {
                // Denied or off: fly to last selected city
                val lastCity = repository.selectedCity.value
                if (lastCity.cityName != "Loading...") {
                    val lat = lastCity.latitude ?: 40.7128
                    val lon = lastCity.longitude ?: -74.0060
                    mapProvider.setCenter(lat, lon, 4.5f)
                    activeLocationHeader = "${lastCity.cityName.uppercase()}, ${lastCity.country.uppercase()}"
                    
                    targetCoords = Pair(lat, lon)
                    isInspecting = true
                    inspectedWeather = lastCity
                } else {
                    // absolute default fallback to London
                    mapProvider.setCenter(51.5074, -0.1278, 4.0f)
                    activeLocationHeader = "LONDON, UK"
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .testTag("radar_map_screen_root")
    ) {
        // INTERACTIVE MAP CONTAINER
        AndroidView(
            factory = { ctx ->
                mapProvider.createMapView(
                    context = ctx,
                    onMapLoaded = {
                        isMapReady = true
                    },
                    onCoordinatesSelected = { lat, lon ->
                        triggerCoordinateFetch(lat, lon, null)
                    },
                    onApiKeyMissing = { layer ->
                        // Managed elegantly via fallbacks inside WebView layer
                    }
                )
            },
            modifier = Modifier.fillMaxSize()
        )

        // TOP PREMIUM DESIGN HEADER ROW (CURRENT ACTIVE LOCATION INDICATOR)
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xE61E1E2E)) // Transparent overlay
                    .border(1.dp, Color(0xFF374151).copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                    .padding(vertical = 10.dp, horizontal = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (isMapReady) Color(0xFF00FF66) else Color(0xFFFF5252))
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = activeLocationHeader,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            letterSpacing = 1.2.sp
                        ),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // FLOATING LAYER SWITCHER CAROUSEL (ATTACHED BELOW HEADER)
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 132.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xE61E1E2E))
                    .border(1.dp, Color(0xFF374151).copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                    .padding(vertical = 8.dp, horizontal = 12.dp)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MapLayer.values().forEach { layer ->
                    val isSelected = selectedLayer == layer
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isSelected) Color(0xFF2FA3FF) else Color.Transparent)
                            .clickable { selectedLayer = layer }
                            .padding(vertical = 8.dp, horizontal = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = layer.icon,
                                contentDescription = layer.displayName,
                                tint = if (isSelected) Color.White else Color(0xFFD1D5DB),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = layer.displayName,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.White else Color(0xFFD1D5DB)
                                )
                            )
                        }
                    }
                }
            }
            
            // Sub-bar description of active layer
            Box(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xCC070913))
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    text = selectedLayer.description.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color(0xFF00E5FF),
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp,
                        fontSize = 9.sp
                    )
                )
            }
        }

        // FLOATING SEARCH BAR & GPS LAUNCHER (TOP VISUAL OVERLAY)
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 70.dp, start = 16.dp, end = 16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
                    .clip(RoundedCornerShape(25.dp))
                    .background(Color(0xE61E1E2E))
                    .border(1.dp, Color(0xFF374151).copy(alpha = 0.5f), RoundedCornerShape(25.dp))
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = "Search",
                        tint = Color(0xFFD1D5DB),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("map_search_textfield"),
                        decorationBox = @Composable { innerTextField: @Composable () -> Unit ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = "Search worldwide cities (e.g. Paris)...",
                                        style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFFD1D5DB))
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                val match = mapCities.find { it.name.equals(searchQuery, ignoreCase = true) }
                                if (match != null) {
                                    mapProvider.setCenter(match.lat.toDouble(), match.lon.toDouble(), 7.0f)
                                    triggerCoordinateFetch(match.lat.toDouble(), match.lon.toDouble(), match.name)
                                } else {
                                    // Query dynamic geocoding fetch from current live API
                                    coroutineScope.launch {
                                        val res = repository.fetchWeatherFromApi(searchQuery, forceRefresh = true)
                                        res.onSuccess { cw ->
                                            val lat = cw.latitude ?: 30.0
                                            val lon = cw.longitude ?: 0.0
                                            mapProvider.setCenter(lat, lon, 7.0f)
                                            triggerCoordinateFetch(lat, lon, cw.cityName)
                                        }
                                    }
                                }
                                searchQuery = ""
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ArrowForward,
                                contentDescription = "Perform Search",
                                tint = Color(0xFF2FA3FF)
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            
            // GPS Location Trigger Button
            IconButton(
                onClick = {
                    currentGpsCoords?.let { (lat, lon) ->
                        mapProvider.setCenter(lat, lon, 7.5f)
                        triggerCoordinateFetch(lat, lon, "CURRENT LOCATION")
                    } ?: run {
                        locationPermissionLauncher.launch(
                            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                        )
                    }
                },
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(Color(0xE61E1E2E))
                    .border(1.dp, Color(0xFF374151).copy(alpha = 0.5f), CircleShape)
                    .testTag("map_gps_trigger")
            ) {
                Icon(
                    imageVector = Icons.Filled.MyLocation,
                    contentDescription = "My GPS Location",
                    tint = if (currentGpsCoords != null) Color(0xFF00FF66) else Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // FLOATING SIDE CONTROLS (ZOOM BUTTONS ON THE RIGHT)
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xE61E1E2E))
                .border(1.dp, Color(0xFF374151).copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                .padding(6.dp),
            verticalArrangement = Arrangement.Center
        ) {
            IconButton(onClick = { mapProvider.setCenter(targetCoords?.first ?: 20.0, targetCoords?.second ?: 0.0, 8.0f) }) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = "Zoom In", tint = Color.White)
            }
            Divider(color = Color(0xFF374151), modifier = Modifier.width(28.dp))
            IconButton(onClick = { mapProvider.setCenter(targetCoords?.first ?: 20.0, targetCoords?.second ?: 0.0, 3.0f) }) {
                Icon(imageVector = Icons.Filled.Remove, contentDescription = "Zoom Out", tint = Color.White)
            }
            Divider(color = Color(0xFF374151), modifier = Modifier.width(28.dp))
            IconButton(onClick = {
                val lastCity = repository.selectedCity.value
                val lat = lastCity.latitude ?: 40.7128
                val lon = lastCity.longitude ?: -74.0060
                mapProvider.setCenter(lat, lon, 4.5f)
                triggerCoordinateFetch(lat, lon, lastCity.cityName)
            }) {
                Icon(imageVector = Icons.Filled.FilterCenterFocus, contentDescription = "Reset Zoom", tint = Color.White)
            }
        }

        // FLOATING RADAR TIMELINE CONTROLS (BOTTOM ATTACHED)
        AnimatedVisibility(
            visible = selectedLayer == MapLayer.RAINFALL,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = if (isInspecting) 220.dp else 100.dp, start = 16.dp, end = 16.dp)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xE61E1E2E))
                    .border(1.dp, Color(0xFF374151).copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                    .padding(vertical = 12.dp, horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { isPlayingRadar = !isPlayingRadar },
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color(0xFF2FA3FF), CircleShape)
                ) {
                    Icon(
                        imageVector = if (isPlayingRadar) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = "Radar Timeline Controls",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Slider(
                        value = radarTimeSlider,
                        onValueChange = {
                            isPlayingRadar = false
                            radarTimeSlider = it
                        },
                        valueRange = 0f..6f,
                        steps = 5,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF00E5FF),
                            activeTrackColor = Color(0xFF2FA3FF),
                            inactiveTrackColor = Color(0xFF374151)
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        timelineLabels.forEachIndexed { idx, label ->
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (idx == radarTimeSlider.toInt()) FontWeight.Bold else FontWeight.Medium,
                                    color = if (idx == radarTimeSlider.toInt()) Color(0xFF00E5FF) else Color(0xFFD1D5DB),
                                    fontSize = 9.sp
                                )
                            )
                        }
                    }
                }
            }
        }

        // INSPECTION POPUP CARD FOR SELECTED COORDS
        AnimatedVisibility(
            visible = isInspecting,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 100.dp, start = 16.dp, end = 16.dp)
                .fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color(0xE61E1E2E))
                    .border(1.dp, Color(0xFF374151).copy(alpha = 0.5f), RoundedCornerShape(28.dp))
                    .padding(16.dp)
            ) {
                IconButton(
                    onClick = { isInspecting = false },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(32.dp)
                        .background(Color(0x30FFFFFF), CircleShape)
                ) {
                    Icon(imageVector = Icons.Filled.Close, contentDescription = "Close Detail Card", tint = Color.White, modifier = Modifier.size(16.dp))
                }

                if (isFetchingInspected) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(color = Color(0xFF2FA3FF), modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Inspecting coordinates & live satellite telemetry...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF94A3B8)
                        )
                    }
                } else {
                    inspectedWeather?.let { weather ->
                        val details = weather.weatherDetails
                        val isCelsiusSelected by repository.isCelsius.collectAsState()
                        val tempStr = if (isCelsiusSelected) {
                            "${((details.currentTemp - 32) * 5 / 9)}°C"
                        } else {
                            "${details.currentTemp}°F"
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(end = 36.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(
                                        Brush.linearGradient(
                                            colors = listOf(details.condition.startColor, details.condition.endColor)
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                WeatherConditionIcon(
                                    condition = details.condition,
                                    modifier = Modifier.size(36.dp),
                                    tint = Color.White
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = weather.cityName.uppercase(),
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold, color = Color.White)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    
                                    IconButton(
                                        onClick = { repository.toggleFavorite(weather.cityName) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (weather.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                            contentDescription = "Save Map Location",
                                            tint = if (weather.isFavorite) Color(0xFFFF5252) else Color(0xFFD1D5DB),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = weather.country,
                                    style = MaterialTheme.typography.labelMedium.copy(color = Color(0xFF2FA3FF), fontWeight = FontWeight.Bold)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = details.condition.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFD1D5DB)
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = tempStr,
                                    style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Black, color = Color.White)
                                )
                                Button(
                                    onClick = {
                                        repository.selectCity(weather.cityName)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2FA3FF)),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text("SELECT", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = Color.White)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Divider(color = Color(0xFF374151))
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                Text("HUMIDITY", style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFFD1D5DB), fontWeight = FontWeight.Bold))
                                Text("${details.humidity}%", style = MaterialTheme.typography.bodyMedium.copy(color = Color.White, fontWeight = FontWeight.Bold))
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                Text("WIND", style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFFD1D5DB), fontWeight = FontWeight.Bold))
                                Text("${details.windSpeed} km/h", style = MaterialTheme.typography.bodyMedium.copy(color = Color.White, fontWeight = FontWeight.Bold))
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                Text("PRESSURE", style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFFD1D5DB), fontWeight = FontWeight.Bold))
                                Text("${details.pressureHpa} hPa", style = MaterialTheme.typography.bodyMedium.copy(color = Color.White, fontWeight = FontWeight.Bold))
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                Text("CLOUDS", style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFFD1D5DB), fontWeight = FontWeight.Bold))
                                Text("${details.cloudCoverage}%", style = MaterialTheme.typography.bodyMedium.copy(color = Color.White, fontWeight = FontWeight.Bold))
                            }
                        }
                    } ?: run {
                        Text(
                            text = "Select any coordinate point on the map to fetch precise real-time tropospheric and radar data forecasts.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFD1D5DB),
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp)
                        )
                    }
                }
            }
        }
    }
}
