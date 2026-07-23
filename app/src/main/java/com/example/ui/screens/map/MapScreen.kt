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
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.example.BuildConfig
import com.example.data.models.CityWeather
import com.example.data.repository.WeatherRepository
import com.example.ui.components.WeatherConditionIcon
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class MapLayer(
    val displayName: String,
    val icon: ImageVector,
    val description: String
) {
    RAINFALL("Rain Radar", Icons.Filled.WaterDrop, "Active live Doppler precipitation radar"),
    CLOUD_COVER("Clouds", Icons.Filled.Cloud, "Infrared satellite cloud density"),
    TEMPERATURE("Temperature", Icons.Filled.Thermostat, "Thermal surface spectroscopy"),
    WIND_SPEED("Wind", Icons.Filled.Air, "Flow streamline velocity vectors"),
    PRESSURE("Pressure", Icons.Filled.Compress, "Isobaric atmospheric surface pressure"),
    HUMIDITY("Humidity", Icons.Filled.Water, "Relative moisture concentration"),
    SATELLITE("Satellite", Icons.Filled.Public, "High-resolution orbital satellite scan"),
    LIGHTNING("Lightning", Icons.Filled.FlashOn, "Real-time electrical discharge activity")
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

    // Map Controller and Provider setup
    val controller = remember { RadarMapController() }
    val mapProvider = remember {
        MapLibreWebViewProvider().apply {
            this.controller = controller
        }
    }
    LaunchedEffect(mapProvider) {
        controller.attachProvider(mapProvider)
    }

    // Controller states
    val selectedLayer by controller.selectedLayer.collectAsState()
    val currentFrameIndex by controller.currentFrameIndex.collectAsState()
    val isPlayingRadar by controller.isPlaying.collectAsState()
    val playbackSpeed by controller.playbackSpeed.collectAsState()
    val radarOpacity by controller.radarOpacity.collectAsState()
    val isOffline by controller.isOffline.collectAsState()
    val isMapReady by controller.isMapLoaded.collectAsState()

    // UI overlays state
    var activeLocationHeader by remember { mutableStateOf("LOADING RADAR...") }
    var showSearchDialog by remember { mutableStateOf(false) }
    var showLayersDialog by remember { mutableStateOf(false) }
    var showOpacitySlider by remember { mutableStateOf(false) }
    var isBottomSheetExpanded by remember { mutableStateOf(false) }

    // GPS and location state
    var currentGpsCoords by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var hasCentredInitially by remember { mutableStateOf(false) }
    var hasLocationPermissions by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }

    // Weather details state for bottom sheet
    var inspectedWeather by remember { mutableStateOf<CityWeather?>(null) }
    var isFetchingInspected by remember { mutableStateOf(false) }
    var targetCoords by remember { mutableStateOf<Pair<Double, Double>?>(null) }

    // Favorites flow
    val favoritedCities by repository.getFavoritesFlow().collectAsState(initial = emptyList())
    val recentSearches by repository.getRecentSearchesFlow().collectAsState(initial = emptyList())

    val timelineLabels = listOf("-3h", "-2h", "-1h", "NOW", "+1h", "+2h", "+3h")

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

    // Helper to query location-specific weather details
    val triggerCoordinateFetch: (Double, Double, String?) -> Unit = { lat, lon, customName ->
        targetCoords = Pair(lat, lon)
        isFetchingInspected = true

        coroutineScope.launch {
            val result = repository.fetchWeatherFromApi("$lat,$lon", forceRefresh = true)
            isFetchingInspected = false
            result.onSuccess { weather ->
                inspectedWeather = weather
                activeLocationHeader = "${weather.cityName.uppercase()}, ${weather.country.uppercase()}"
            }.onFailure {
                inspectedWeather = null
                activeLocationHeader = customName ?: "COORDINATES: ${String.format("%.2f", lat)}, ${String.format("%.2f", lon)}"
            }
        }
    }

    // Location manager for GPS tracking
    val locationManager = remember { context.getSystemService(Context.LOCATION_SERVICE) as LocationManager }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fineGranted || coarseGranted) {
            hasLocationPermissions = true
            if (LocationManagerCompat.isLocationEnabled(locationManager)) {
                try {
                    val provider = when {
                        fineGranted && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                        (fineGranted || coarseGranted) && locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
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
    }

    // Location listener setup
    DisposableEffect(locationManager, hasLocationPermissions) {
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                currentGpsCoords = Pair(location.latitude, location.longitude)
            }
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        }

        var isRegistered = false
        if (hasLocationPermissions && LocationManagerCompat.isLocationEnabled(locationManager)) {
            runCatching {
                val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

                if (hasFine && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        10000L,
                        10f,
                        listener,
                        android.os.Looper.getMainLooper()
                    )
                    isRegistered = true
                } else if ((hasFine || hasCoarse) && locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    locationManager.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER,
                        10000L,
                        10f,
                        listener,
                        android.os.Looper.getMainLooper()
                    )
                    isRegistered = true
                }
            }
        }

        onDispose {
            if (isRegistered) {
                runCatching {
                    locationManager.removeUpdates(listener)
                }
            }
            controller.release()
        }
    }

    // Check location permissions on load
    LaunchedEffect(Unit) {
        val fineGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (fineGranted || coarseGranted) {
            hasLocationPermissions = true
            if (LocationManagerCompat.isLocationEnabled(locationManager)) {
                try {
                    val provider = when {
                        fineGranted && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                        (fineGranted || coarseGranted) && locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
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

    // Sync map layers when map is loaded
    LaunchedEffect(isMapReady) {
        if (isMapReady) {
            controller.setWeatherApiKey(BuildConfig.WEATHER_API_KEY)
            controller.selectLayer(selectedLayer)
        }
    }

    // Initial camera placement
    LaunchedEffect(isMapReady, currentGpsCoords) {
        if (isMapReady && !hasCentredInitially) {
            val gps = currentGpsCoords
            if (gps != null) {
                hasCentredInitially = true
                controller.setCenter(gps.first, gps.second, 5.0f)
                triggerCoordinateFetch(gps.first, gps.second, "CURRENT LOCATION")
            } else {
                val lastCity = repository.selectedCity.value
                if (lastCity.cityName != "Loading...") {
                    hasCentredInitially = true
                    val lat = lastCity.latitude ?: 40.7128
                    val lon = lastCity.longitude ?: -74.0060
                    controller.setCenter(lat, lon, 4.5f)
                    activeLocationHeader = "${lastCity.cityName.uppercase()}, ${lastCity.country.uppercase()}"
                    targetCoords = Pair(lat, lon)
                    inspectedWeather = lastCity
                } else {
                    hasCentredInitially = true
                    controller.setCenter(51.5074, -0.1278, 4.0f)
                    activeLocationHeader = "LONDON, UK"
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF070913))
            .testTag("radar_map_screen_root")
    ) {
        // ==========================================
        // 1. MAP AS THE HERO (100% FULL-BLEED CANVAS)
        // ==========================================
        AndroidView(
            factory = { ctx ->
                mapProvider.createMapView(
                    context = ctx,
                    onMapLoaded = {
                        controller.onMapReady(coroutineScope)
                    },
                    onCoordinatesSelected = { lat, lon ->
                        triggerCoordinateFetch(lat, lon, null)
                    },
                    onApiKeyMissing = {}
                )
            },
            modifier = Modifier.fillMaxSize()
        )

        // ==========================================
        // 2. TOP FLOATING GLASS HEADER CONTROL BAR
        // ==========================================
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 8.dp, start = 16.dp, end = 16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Offline Warning Toast Banner
            if (isOffline) {
                Box(
                    modifier = Modifier
                        .padding(bottom = 6.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xDDE53935))
                        .padding(vertical = 4.dp, horizontal = 12.dp)
                ) {
                    Text(
                        text = "OFFLINE MODE • CACHED RADAR TILES",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 9.sp
                        )
                    )
                }
            }

            // Compact Header Pill
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color(0xCC0F172A))
                    .border(1.dp, Color(0x3300E5FF), RoundedCornerShape(28.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Location Title & GPS Indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp)
                        .clickable { showSearchDialog = true }
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (isMapReady && !isOffline) Color(0xFF00FF66) else Color(0xFFFF9800))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = activeLocationHeader,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            letterSpacing = 0.5.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Header Floating Actions (Search, Layers, Opacity/Settings)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Search Button
                    IconButton(
                        onClick = { showSearchDialog = true },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0x30FFFFFF))
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = "Search Locations",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Layers Button with Active Layer Badge
                    Box {
                        IconButton(
                            onClick = { showLayersDialog = true },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF00E5FF).copy(alpha = 0.25f))
                                .border(1.dp, Color(0xFF00E5FF), CircleShape)
                        ) {
                            Icon(
                                imageVector = selectedLayer.icon,
                                contentDescription = "Weather Layers",
                                tint = Color(0xFF00E5FF),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Opacity / Speed Settings Button
                    IconButton(
                        onClick = { showOpacitySlider = !showOpacitySlider },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (showOpacitySlider) Color(0xFF2FA3FF) else Color(0x30FFFFFF))
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Tune,
                            contentDescription = "Radar Settings",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Quick Layer Chip Pill Indicator
            Row(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xBB0B0F19))
                    .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 3.dp)
                    .clickable { showLayersDialog = true },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = selectedLayer.icon,
                    contentDescription = null,
                    tint = Color(0xFF00E5FF),
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = selectedLayer.displayName.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color(0xFF00E5FF),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        fontSize = 9.sp
                    )
                )
            }
        }

        // ==========================================
        // 3. RIGHT FLOATING MAP CONTROLS
        // ==========================================
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xCC0F172A))
                .border(1.dp, Color(0x3300E5FF), RoundedCornerShape(24.dp))
                .padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(
                onClick = { controller.zoomIn() },
                modifier = Modifier.size(38.dp)
            ) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = "Zoom In", tint = Color.White)
            }
            Divider(color = Color(0x33FFFFFF), modifier = Modifier.width(24.dp))
            IconButton(
                onClick = { controller.zoomOut() },
                modifier = Modifier.size(38.dp)
            ) {
                Icon(imageVector = Icons.Filled.Remove, contentDescription = "Zoom Out", tint = Color.White)
            }
            Divider(color = Color(0x33FFFFFF), modifier = Modifier.width(24.dp))
            IconButton(
                onClick = {
                    currentGpsCoords?.let { (lat, lon) ->
                        controller.setCenter(lat, lon, 7.5f)
                        triggerCoordinateFetch(lat, lon, "CURRENT LOCATION")
                    } ?: run {
                        locationPermissionLauncher.launch(
                            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                        )
                    }
                },
                modifier = Modifier.size(38.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.MyLocation,
                    contentDescription = "My GPS Location",
                    tint = if (currentGpsCoords != null) Color(0xFF00FF66) else Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
            Divider(color = Color(0x33FFFFFF), modifier = Modifier.width(24.dp))
            IconButton(
                onClick = { controller.resetNorth() },
                modifier = Modifier.size(38.dp)
            ) {
                Icon(imageVector = Icons.Filled.Explore, contentDescription = "Reset Compass North", tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }

        // ==========================================
        // 4. FLOATING RADAR OPACITY & SPEED POPUP MENU
        // ==========================================
        AnimatedVisibility(
            visible = showOpacitySlider,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 60.dp, end = 16.dp)
                .width(260.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xF00F172A))
                    .border(1.dp, Color(0xFF00E5FF), RoundedCornerShape(20.dp))
                    .padding(14.dp)
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "RADAR DENSITY",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF00E5FF),
                                letterSpacing = 1.sp
                            )
                        )
                        Text(
                            text = "${(radarOpacity * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = Color.White)
                        )
                    }

                    Slider(
                        value = radarOpacity,
                        onValueChange = { controller.setRadarOpacity(it) },
                        valueRange = 0.1f..1.0f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF00E5FF),
                            activeTrackColor = Color(0xFF00E5FF),
                            inactiveTrackColor = Color(0x33FFFFFF)
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "PLAYBACK SPEED",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF00E5FF),
                            letterSpacing = 1.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf(0.5f, 1.0f, 2.0f).forEach { speed ->
                            val isSelected = playbackSpeed == speed
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSelected) Color(0xFF00E5FF) else Color(0x22FFFFFF))
                                    .clickable { controller.setPlaybackSpeed(speed) }
                                    .padding(vertical = 6.dp, horizontal = 14.dp)
                            ) {
                                Text(
                                    text = "${speed}x",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Color.Black else Color.White
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        // ==========================================
        // 5. FLOATING RADAR TIMELINE CONTROLLER
        // ==========================================
        AnimatedVisibility(
            visible = selectedLayer == MapLayer.RAINFALL,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = if (isBottomSheetExpanded) 340.dp else 120.dp, start = 12.dp, end = 12.dp)
                .fillMaxWidth()
                .widthIn(max = 560.dp)
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color(0xDD0F172A))
                    .border(1.dp, Color(0x4000E5FF), RoundedCornerShape(28.dp))
                    .padding(vertical = 8.dp, horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Play / Pause Toggle
                IconButton(
                    onClick = { controller.togglePlayback(coroutineScope) },
                    modifier = Modifier
                        .size(38.dp)
                        .background(Color(0xFF00E5FF), CircleShape)
                ) {
                    Icon(
                        imageVector = if (isPlayingRadar) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = "Playback Toggle",
                        tint = Color.Black,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Timeline Scrubber Column
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "LIVE DOPPLER RADAR",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF00E5FF),
                                letterSpacing = 1.sp,
                                fontSize = 9.sp
                            )
                        )
                        Text(
                            text = timelineLabels.getOrElse(currentFrameIndex) { "NOW" },
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                fontSize = 10.sp
                            )
                        )
                    }

                    Slider(
                        value = currentFrameIndex.toFloat(),
                        onValueChange = { controller.selectTimelineIndex(it.toInt()) },
                        valueRange = 0f..6f,
                        steps = 5,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF00E5FF),
                            activeTrackColor = Color(0xFF00E5FF),
                            inactiveTrackColor = Color(0x33FFFFFF)
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
                                    fontWeight = if (idx == currentFrameIndex) FontWeight.Black else FontWeight.Normal,
                                    color = if (idx == currentFrameIndex) Color(0xFF00E5FF) else Color(0x88FFFFFF),
                                    fontSize = 8.sp
                                )
                            )
                        }
                    }
                }
            }
        }

        // ==========================================
        // 6. DRAGGABLE BOTTOM SHEET WEATHER CARD
        // ==========================================
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 60.dp, start = 12.dp, end = 12.dp)
                .fillMaxWidth()
                .widthIn(max = 600.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xF00F172A))
                .border(1.dp, Color(0x3300E5FF), RoundedCornerShape(28.dp))
                .pointerInput(Unit) {
                    detectVerticalDragGestures { _, dragAmount ->
                        if (dragAmount < -15) {
                            isBottomSheetExpanded = true
                        } else if (dragAmount > 15) {
                            isBottomSheetExpanded = false
                        }
                    }
                }
                .padding(14.dp)
        ) {
            Column {
                // Drag Handle Bar
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .width(36.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(Color(0x55FFFFFF))
                        .clickable { isBottomSheetExpanded = !isBottomSheetExpanded }
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Collapsed View Row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isBottomSheetExpanded = !isBottomSheetExpanded }
                ) {
                    if (isFetchingInspected) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(color = Color(0xFF00E5FF), modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Fetching location telemetry...", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF94A3B8))
                        }
                    } else {
                        inspectedWeather?.let { weather ->
                            val details = weather.weatherDetails
                            val tempStr = if (isCelsius) "${((details.currentTemp - 32) * 5 / 9)}°C" else "${details.currentTemp}°F"

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Brush.linearGradient(listOf(details.condition.startColor, details.condition.endColor))),
                                    contentAlignment = Alignment.Center
                                ) {
                                    WeatherConditionIcon(condition = details.condition, modifier = Modifier.size(24.dp), tint = Color.White)
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = weather.cityName.uppercase(),
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold, color = Color.White)
                                    )
                                    Text(
                                        text = details.condition.description,
                                        style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF00E5FF), fontWeight = FontWeight.SemiBold)
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = tempStr,
                                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black, color = Color.White)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    imageVector = if (isBottomSheetExpanded) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp,
                                    contentDescription = "Expand details",
                                    tint = Color(0xFF00E5FF)
                                )
                            }
                        } ?: run {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Tap map location to inspect telemetry",
                                    style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFF94A3B8))
                                )
                                Icon(
                                    imageVector = Icons.Filled.TouchApp,
                                    contentDescription = null,
                                    tint = Color(0xFF00E5FF),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                // Expanded Detailed View Breakdown
                AnimatedVisibility(
                    visible = isBottomSheetExpanded && inspectedWeather != null,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    inspectedWeather?.let { weather ->
                        val details = weather.weatherDetails
                        val feelsLikeStr = if (isCelsius) "${((details.feelsLike - 32) * 5 / 9)}°C" else "${details.feelsLike}°F"

                        Column(modifier = Modifier.padding(top = 14.dp)) {
                            Divider(color = Color(0x22FFFFFF))
                            Spacer(modifier = Modifier.height(14.dp))

                            // 4-Column Atmospheric Grid
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                WeatherMetricBox("FEELS LIKE", feelsLikeStr, Icons.Filled.Thermostat, Modifier.weight(1f))
                                WeatherMetricBox("HUMIDITY", "${details.humidity}%", Icons.Filled.Water, Modifier.weight(1f))
                                WeatherMetricBox("WIND", "${details.windSpeed} km/h", Icons.Filled.Air, Modifier.weight(1f))
                                WeatherMetricBox("PRESSURE", "${details.pressureHpa} hPa", Icons.Filled.Compress, Modifier.weight(1f))
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                WeatherMetricBox("CLOUDS", "${details.cloudCoverage}%", Icons.Filled.Cloud, Modifier.weight(1f))
                                WeatherMetricBox("VISIBILITY", "${details.visibilityKm} km", Icons.Filled.Visibility, Modifier.weight(1f))
                                WeatherMetricBox("UV INDEX", "${details.uvIndex}", Icons.Filled.WbSunny, Modifier.weight(1f))
                                WeatherMetricBox("SUNSET", details.sunset, Icons.Filled.NightsStay, Modifier.weight(1f))
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Bottom Action Buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Button(
                                    onClick = { repository.selectCity(weather.cityName) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("SET ACTIVE CITY", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = Color.Black))
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                OutlinedButton(
                                    onClick = { repository.toggleFavorite(weather.cityName) },
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, Color(0xFF00E5FF)),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = if (weather.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                        contentDescription = null,
                                        tint = if (weather.isFavorite) Color(0xFFFF5252) else Color(0xFF00E5FF),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (weather.isFavorite) "SAVED" else "FAVORITE",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = Color(0xFF00E5FF))
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ==========================================
        // 7. FLOATING SEARCH OVERLAY DIALOG
        // ==========================================
        if (showSearchDialog) {
            Dialog(onDismissRequest = { showSearchDialog = false }) {
                var query by remember { mutableStateOf("") }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(28.dp))
                        .background(Color(0xF00F172A))
                        .border(1.dp, Color(0xFF00E5FF), RoundedCornerShape(28.dp))
                        .padding(20.dp)
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "WORLDWIDE SEARCH",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFF00E5FF),
                                    letterSpacing = 1.sp
                                )
                            )
                            IconButton(onClick = { showSearchDialog = false }) {
                                Icon(imageVector = Icons.Filled.Close, contentDescription = "Close Search", tint = Color.White)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Search Field Input
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0x33FFFFFF))
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Filled.Search, contentDescription = null, tint = Color(0xFF00E5FF))
                                Spacer(modifier = Modifier.width(8.dp))
                                BasicTextField(
                                    value = query,
                                    onValueChange = { query = it },
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                    keyboardActions = KeyboardActions(onSearch = {
                                        if (query.isNotEmpty()) {
                                            coroutineScope.launch {
                                                val res = repository.fetchWeatherFromApi(query, forceRefresh = true)
                                                res.onSuccess { cw ->
                                                    val lat = cw.latitude ?: 30.0
                                                    val lon = cw.longitude ?: 0.0
                                                    controller.setCenter(lat, lon, 7.0f)
                                                    triggerCoordinateFetch(lat, lon, cw.cityName)
                                                }
                                            }
                                            showSearchDialog = false
                                        }
                                    }),
                                    modifier = Modifier.weight(1f),
                                    decorationBox = @Composable { innerTextField: @Composable () -> Unit ->
                                        Box(contentAlignment = Alignment.CenterStart) {
                                            if (query.isEmpty()) {
                                                Text("Search city name (e.g. Tokyo)...", style = MaterialTheme.typography.bodyMedium.copy(color = Color(0x88FFFFFF)))
                                            }
                                            innerTextField()
                                        }
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Quick GPS Shortcut Button
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0x2200E5FF))
                                .clickable {
                                    currentGpsCoords?.let { (lat, lon) ->
                                        controller.setCenter(lat, lon, 7.5f)
                                        triggerCoordinateFetch(lat, lon, "CURRENT LOCATION")
                                    }
                                    showSearchDialog = false
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(imageVector = Icons.Filled.MyLocation, contentDescription = null, tint = Color(0xFF00E5FF))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Center on Current GPS Position", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = Color.White))
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text("MAJOR METROPOLISES", style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF00E5FF), fontWeight = FontWeight.ExtraBold))
                        Spacer(modifier = Modifier.height(8.dp))

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(mapCities.take(8)) { city ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0x22FFFFFF))
                                        .clickable {
                                            controller.setCenter(city.lat.toDouble(), city.lon.toDouble(), 7.0f)
                                            triggerCoordinateFetch(city.lat.toDouble(), city.lon.toDouble(), city.name)
                                            showSearchDialog = false
                                        }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(city.name, style = MaterialTheme.typography.labelSmall.copy(color = Color.White, fontWeight = FontWeight.SemiBold))
                                }
                            }
                        }
                    }
                }
            }
        }

        // ==========================================
        // 8. FLOATING LAYER SELECTOR DIALOG
        // ==========================================
        if (showLayersDialog) {
            Dialog(onDismissRequest = { showLayersDialog = false }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(28.dp))
                        .background(Color(0xF00F172A))
                        .border(1.dp, Color(0xFF00E5FF), RoundedCornerShape(28.dp))
                        .padding(20.dp)
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "WEATHER OVERLAYS",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFF00E5FF),
                                    letterSpacing = 1.sp
                                )
                            )
                            IconButton(onClick = { showLayersDialog = false }) {
                                Icon(imageVector = Icons.Filled.Close, contentDescription = "Close Layers", tint = Color.White)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(MapLayer.values()) { layer ->
                                val isSelected = selectedLayer == layer
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(if (isSelected) Color(0xFF00E5FF).copy(alpha = 0.2f) else Color(0x15FFFFFF))
                                        .border(
                                            1.dp,
                                            if (isSelected) Color(0xFF00E5FF) else Color.Transparent,
                                            RoundedCornerShape(16.dp)
                                        )
                                        .clickable {
                                            controller.selectLayer(layer)
                                            showLayersDialog = false
                                        }
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = layer.icon,
                                        contentDescription = null,
                                        tint = if (isSelected) Color(0xFF00E5FF) else Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = layer.displayName,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSelected) Color(0xFF00E5FF) else Color.White
                                            )
                                        )
                                        Text(
                                            text = layer.description,
                                            style = MaterialTheme.typography.labelSmall.copy(color = Color(0x88FFFFFF))
                                        )
                                    }
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = "Selected Layer",
                                            tint = Color(0xFF00E5FF)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeatherMetricBox(
    title: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.padding(vertical = 4.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.height(2.dp))
        Text(title, style = MaterialTheme.typography.labelSmall.copy(color = Color(0x88FFFFFF), fontSize = 8.sp, fontWeight = FontWeight.Bold))
        Text(value, style = MaterialTheme.typography.labelSmall.copy(color = Color.White, fontWeight = FontWeight.Bold, fontSize = 10.sp))
    }
}
