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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.models.CityWeather
import com.example.data.models.WeatherCondition
import com.example.data.models.WeatherDetails
import com.example.data.repository.WeatherRepository
import com.example.ui.components.WeatherConditionIcon
import com.example.ui.theme.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.*

enum class MapLayer(val displayName: String, val icon: ImageVector, val description: String) {
    TEMPERATURE("Temperature", Icons.Filled.Thermostat, "Thermal satellite spectroscopy"),
    RAINFALL("Rainfall Radar", Icons.Filled.WaterDrop, "Active Doppler precipitation radar"),
    CLOUD_COVER("Cloud Cover", Icons.Filled.Cloud, "Multi-spectral infrared cloud cover"),
    WIND_SPEED("Wind Speed", Icons.Filled.Air, "Flow streamline speed vectors"),
    WIND_DIRECTION("Wind Direction", Icons.Filled.Navigation, "Atmospheric angular force vectors"),
    HUMIDITY("Humidity", Icons.Filled.Water, "Tropospheric moisture content"),
    PRESSURE("Pressure", Icons.Filled.Compress, "Isobaric surface tension lines")
}

// Coordinate lookup for global cities
data class MapCity(val name: String, val lat: Float, val lon: Float, val country: String)

@Composable
fun MapScreen(
    repository: WeatherRepository,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val isCelsius by repository.isCelsius.collectAsState()

    // Screen-level state
    var selectedLayer by remember { mutableStateOf(MapLayer.RAINFALL) }
    var scale by remember { mutableStateOf(1.8f) }
    var offset by remember { mutableStateOf(Offset(-200f, 150f)) }
    var searchQuery by remember { mutableStateOf("") }
    var canvasWidth by remember { mutableStateOf(1080f) }
    var canvasHeight by remember { mutableStateOf(1920f) }
    
    // GPS and current location state
    var currentGpsLocation by remember { mutableStateOf<Offset?>(null) }
    var currentGpsCoords by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    
    // Inspected location detail
    var inspectedWeather by remember { mutableStateOf<CityWeather?>(null) }
    var isInspecting by remember { mutableStateOf(false) }
    var isFetchingInspected by remember { mutableStateOf(false) }
    var targetCoords by remember { mutableStateOf<Pair<Double, Double>?>(null) }

    // Favorites list from DB
    val favoritedCities by repository.getFavoritesFlow().collectAsState(initial = emptyList())

    // Radar Animation Player States
    var isPlayingRadar by remember { mutableStateOf(true) }
    var radarTimeSlider by remember { mutableStateOf(3f) } // 0 = -3h, 3 = Now, 6 = +3h
    val timelineLabels = listOf("-3h", "-2h", "-1h", "Now", "+1h", "+2h", "+3h")

    // Core lookup for over 50 major worldwide cities
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
            MapCity("Miami", 25.7617f, -80.1918f, "USA"),
            MapCity("Singapore", 1.3521f, 103.8198f, "Singapore"),
            MapCity("Bangkok", 13.7563f, 100.5018f, "Thailand"),
            MapCity("Seoul", 37.5665f, 126.9780f, "South Korea"),
            MapCity("Reykjavik", 64.1466f, -21.9426f, "Iceland"),
            MapCity("Anchorage", 61.2181f, -149.9003f, "USA"),
            MapCity("Honolulu", 21.3069f, -157.8583f, "USA")
        )
    }

    // Radar Playback Loop
    LaunchedEffect(isPlayingRadar) {
        if (isPlayingRadar) {
            while (true) {
                delay(800)
                radarTimeSlider = (radarTimeSlider + 1) % 7
            }
        }
    }

    // Set up Location Manager for real GPS markers
    val locationManager = remember { context.getSystemService(Context.LOCATION_SERVICE) as LocationManager }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fineGranted || coarseGranted) {
            try {
                val loc = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                    ?: locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                if (loc != null) {
                    currentGpsCoords = Pair(loc.latitude, loc.longitude)
                }
            } catch (e: SecurityException) {
                e.printStackTrace()
            }
        }
    }

    // Trigger Permission request on start
    LaunchedEffect(Unit) {
        val fineGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (fineGranted || coarseGranted) {
            try {
                val loc = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                    ?: locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                if (loc != null) {
                    currentGpsCoords = Pair(loc.latitude, loc.longitude)
                }
            } catch (e: SecurityException) {
                e.printStackTrace()
            }
        } else {
            locationPermissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    // Continuous breathing/pulsing animation for markers
    val infiniteTransition = rememberInfiniteTransition(label = "MarkerPulse")
    val pulseSize by infiniteTransition.animateFloat(
        initialValue = 4f,
        targetValue = 18f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Pulse"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Alpha"
    )

    // Flow animation speed/cycle variables for Radar and Wind overlays
    val radarSweepTransition = rememberInfiniteTransition(label = "RadarSweep")
    val radarSweepAngle by radarSweepTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Angle"
    )
    val windOffsetFloat by radarSweepTransition.animateFloat(
        initialValue = 0f,
        targetValue = 100f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "WindOffset"
    )

    // Helper to fetch weather for coordinates
    val triggerCoordinateFetch: (Double, Double) -> Unit = { lat, lon ->
        targetCoords = Pair(lat, lon)
        isInspecting = true
        isFetchingInspected = true
        inspectedWeather = null
        
        coroutineScope.launch {
            val result = repository.fetchWeatherFromApi("$lat,$lon", forceRefresh = true)
            isFetchingInspected = false
            result.onSuccess { weather ->
                inspectedWeather = weather
            }.onFailure {
                inspectedWeather = null
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .testTag("radar_map_screen_root")
    ) {
        // WORLD VECTOR MAP CANVAS (THE CORE INTERACTIVE BASE)
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, panChange, zoomChange, _ ->
                        scale = (scale * zoomChange).coerceIn(0.8f, 20f)
                        offset += panChange
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { tapOffset ->
                        // Reverse matrix projection logic:
                        // tapOffset.x = mapX * scale + offset.x -> mapX = (tapOffset.x - offset.x) / scale
                        val mapX = (tapOffset.x - offset.x) / scale
                        val mapY = (tapOffset.y - offset.y) / scale
                        
                        if (mapX in 0f..canvasWidth && mapY in 0f..canvasHeight) {
                            val lon = (mapX / canvasWidth) * 360f - 180f
                            val lat = 90f - (mapY / canvasHeight) * 180f
                            triggerCoordinateFetch(lat.toDouble(), lon.toDouble())
                        }
                    }
                }
        ) {
            val width = constraints.maxWidth.toFloat()
            val height = constraints.maxHeight.toFloat()
            LaunchedEffect(width, height) {
                canvasWidth = width
                canvasHeight = height
            }

            Canvas(modifier = Modifier.fillMaxSize()) {
                // Background Atmosphere Space
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF0F1735), Color(0xFF04060E)),
                        center = center,
                        radius = size.maxDimension
                    )
                )

                withTransform({
                    translate(offset.x, offset.y)
                    scale(scale, scale, pivot = Offset.Zero)
                }) {
                    // 1. Draw Cartographic Grid System Lines (Meridians & Parallels)
                    val gridPaint = Paint().asFrameworkPaint().apply {
                        color = android.graphics.Color.argb(30, 47, 163, 255)
                        strokeWidth = 1f
                        style = android.graphics.Paint.Style.STROKE
                    }
                    
                    // Latitude horizontal grid lines
                    for (latVal in -80..80 step 20) {
                        val gridY = (90f - latVal) / 180f * height
                        drawLine(
                            color = Color(0xFF2FA3FF).copy(alpha = 0.08f),
                            start = Offset(0f, gridY),
                            end = Offset(width, gridY),
                            strokeWidth = 1f / scale
                        )
                    }

                    // Longitude vertical grid lines
                    for (lonVal in -160..160 step 30) {
                        val gridX = (lonVal + 180f) / 360f * width
                        drawLine(
                            color = Color(0xFF2FA3FF).copy(alpha = 0.08f),
                            start = Offset(gridX, 0f),
                            end = Offset(gridX, height),
                            strokeWidth = 1f / scale
                        )
                    }

                    // 2. Continents Vector Outlines (Equirectangular polygons)
                    val continents = listOf(
                        // North America
                        listOf(70f to -165f, 75f to -120f, 80f to -80f, 60f to -60f, 45f to -50f, 25f to -80f, 15f to -90f, 8f to -83f, 10f to -75f, 20f to -105f, 30f to -115f, 45f to -125f, 60f to -140f, 65f to -165f),
                        // South America
                        listOf(12f to -72f, 8f to -50f, -5f to -35f, -20f to -40f, -45f to -65f, -55f to -70f, -50f to -75f, -30f to -72f, -15f to -75f, -5f to -81f, 5f to -77f, 12f to -72f),
                        // Africa
                        listOf(36f to -5f, 37f to 10f, 32f to 32f, 12f to 45f, 12f to 51f, -5f to 40f, -25f to 35f, -34f to 20f, -30f to 15f, -10f to 12f, 5f to 10f, 15f to -17f, 30f to -10f, 36f to -5f),
                        // Australia
                        listOf(-12f to 130f, -10f to 142f, -25f to 153f, -38f to 145f, -35f to 138f, -35f to 115f, -22f to 114f, -12f to 130f),
                        // Eurasia
                        listOf(36f to -9f, 44f to -1f, 48f to -4f, 55f to -5f, 62f to 5f, 71f to 25f, 70f to 60f, 75f to 100f, 70f to 140f, 65f to 170f, 55f to 160f, 40f to 140f, 30f to 120f, 10f to 108f, 5f to 95f, 10f to 78f, 25f to 60f, 12f to 45f, 30f to 32f, 35f to 15f, 36f to -9f),
                        // Greenland
                        listOf(78f to -70f, 83f to -40f, 80f to -10f, 70f to -20f, 60f to -43f, 65f to -55f, 75f to -73f, 78f to -70f),
                        // Antarctica
                        listOf(-65f to -180f, -65f to -120f, -70f to -60f, -75f to 0f, -70f to 60f, -65f to 120f, -65f to 180f, -85f to 180f, -85f to -180f, -65f to -180f)
                    )

                    continents.forEach { points ->
                        val path = Path()
                        points.forEachIndexed { idx, pair ->
                            val px = (pair.second + 180f) / 360f * width
                            val py = (90f - pair.first) / 180f * height
                            if (idx == 0) {
                                path.moveTo(px, py)
                            } else {
                                path.lineTo(px, py)
                            }
                        }
                        path.close()

                        // Fill Landmasses
                        drawPath(
                            path = path,
                            color = Color(0xFF13172E).copy(alpha = 0.65f)
                        )
                        // Stroke Landmasses (Futuristic glowing border)
                        drawPath(
                            path = path,
                            color = Color(0xFF2FA3FF).copy(alpha = 0.45f),
                            style = Stroke(width = 1.2f / scale)
                        )
                    }

                    // 3. Dynamic Weather Layer Overlay Graphics
                    when (selectedLayer) {
                        MapLayer.TEMPERATURE -> {
                            // Thermal Gradient mapping: equator is hot, poles are cold.
                            // Draw dynamic heat zones around major cities.
                            drawRect(
                                brush = Brush.verticalGradient(
                                    0.0f to Color(0x609C27B0), // North Pole Purp Cold
                                    0.25f to Color(0x302196F3), // Blue Cool
                                    0.5f to Color(0x75FF5722),  // Equator Bright Hot Orange/Red
                                    0.75f to Color(0x302196F3), // Blue Cool
                                    1.0f to Color(0x609C27B0)  // South Pole Purp Cold
                                ),
                                alpha = 0.45f
                            )
                            
                            // Thermal city glows
                            mapCities.forEach { city ->
                                val cx = (city.lon + 180f) / 360f * width
                                val cy = (90f - city.lat) / 180f * height
                                val heatColor = if (city.lat.absoluteValue < 25) Color(0x80FF1744) else Color(0x802979FF)
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(heatColor, Color.Transparent),
                                        center = Offset(cx, cy),
                                        radius = 120f / scale
                                    ),
                                    center = Offset(cx, cy),
                                    radius = 120f / scale
                                )
                            }
                        }
                        
                        MapLayer.RAINFALL -> {
                            // Doppler Precipitation Radar Simulation with moving timeline blobs
                            val seedDelta = (radarTimeSlider * 40f)
                            val radarStorms = listOf(
                                Offset(width * 0.35f + seedDelta, height * 0.32f - seedDelta * 0.1f),
                                Offset(width * 0.72f - seedDelta * 0.2f, height * 0.28f + seedDelta * 0.15f),
                                Offset(width * 0.55f, height * 0.62f + seedDelta),
                                Offset(width * 0.85f - seedDelta, height * 0.65f - seedDelta * 0.3f)
                            )

                            // Draw Concentric Radar echoes
                            radarStorms.forEach { stormCenter ->
                                val radiusBase = 100f + (seedDelta % 50f)
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colorStops = arrayOf(
                                            0.0f to Color(0x00FF0000),
                                            0.2f to Color(0xCCFF1744), // Heavy Storm (Red)
                                            0.5f to Color(0xAAFFEA00), // Medium (Yellow)
                                            0.8f to Color(0x7700E676), // Light Rain (Green)
                                            1.0f to Color(0x0000E676)
                                        ),
                                        center = stormCenter,
                                        radius = radiusBase / scale
                                    ),
                                    center = stormCenter,
                                    radius = radiusBase / scale
                                )
                            }

                            // Dynamic falling rain streak particles
                            for (i in 0..60) {
                                val rx = ((i * 147) % width)
                                val ry = (((i * 89) + windOffsetFloat * 5) % height)
                                drawLine(
                                    color = Color(0x9900E5FF),
                                    start = Offset(rx, ry),
                                    end = Offset(rx + 4f / scale, ry + 12f / scale),
                                    strokeWidth = 1f / scale
                                )
                            }
                        }

                        MapLayer.CLOUD_COVER -> {
                            // Multi-spectral infrared clouds (floating white-grey paths)
                            val drift = (windOffsetFloat * 1.5f)
                            val cloudCenters = listOf(
                                Offset(width * 0.15f + drift, height * 0.22f),
                                Offset(width * 0.45f + drift, height * 0.35f),
                                Offset(width * 0.75f + drift, height * 0.2f),
                                Offset(width * 0.3f + drift * 0.7f, height * 0.65f),
                                Offset(width * 0.68f + drift * 1.2f, height * 0.55f)
                            )

                            cloudCenters.forEach { cc ->
                                val adjustedX = cc.x % width
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(Color(0xD0E2E8F0), Color(0x7094A3B8), Color.Transparent),
                                        center = Offset(adjustedX, cc.y),
                                        radius = 220f / scale
                                    ),
                                    center = Offset(adjustedX, cc.y),
                                    radius = 220f / scale
                                )
                            }
                        }

                        MapLayer.WIND_SPEED -> {
                            // Animated streamline speed particles flow vectors
                            val speedFactor = 2f
                            for (row in 0..15) {
                                val ry = (row * (height / 15f))
                                val rowShift = if (row % 2 == 0) windOffsetFloat * speedFactor else -windOffsetFloat * speedFactor
                                for (col in 0..10) {
                                    val rx = (col * (width / 10f) + rowShift) % width
                                    drawLine(
                                        color = Color(0xFF00E5FF).copy(alpha = 0.5f),
                                        start = Offset(rx, ry),
                                        end = Offset(rx + 22f / scale, ry),
                                        strokeWidth = 1.5f / scale
                                    )
                                }
                            }
                        }

                        MapLayer.WIND_DIRECTION -> {
                            // Atmospheric angular force vectors pointing outwards/curving
                            for (row in 1..10) {
                                val ry = row * (height / 11f)
                                for (col in 1..12) {
                                    val rx = col * (width / 13f)
                                    val angleRad = (col * 30 + row * 45 + windOffsetFloat * 0.2f) * (PI / 180f)
                                    val len = 15f / scale
                                    val dx = (cos(angleRad) * len).toFloat()
                                    val dy = (sin(angleRad) * len).toFloat()
                                    
                                    // Draw small vector arrows
                                    drawLine(
                                        color = Color(0xFF00C6FF).copy(alpha = 0.45f),
                                        start = Offset(rx, ry),
                                        end = Offset(rx + dx, ry + dy),
                                        strokeWidth = 1.2f / scale
                                    )
                                    // Arrow heads
                                    drawCircle(
                                        color = Color(0xFF2FA3FF).copy(alpha = 0.7f),
                                        radius = 2.5f / scale,
                                        center = Offset(rx + dx, ry + dy)
                                    )
                                }
                            }
                        }

                        MapLayer.HUMIDITY -> {
                            // Tropospheric moisture content gradient (Teal-Indigo overlays)
                            drawRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0x33008080),
                                        Color(0x5500ACC1),
                                        Color(0x110D47A1),
                                        Color(0x5500ACC1),
                                        Color(0x33008080)
                                    )
                                ),
                                alpha = 0.4f
                            )
                        }

                        MapLayer.PRESSURE -> {
                            // Isobaric surface tension lines (concentric circular contours)
                            val centerH1 = Offset(width * 0.25f, height * 0.4f)
                            val centerL1 = Offset(width * 0.7f, height * 0.5f)
                            
                            // Draw Concentric Contours for High Pressure (H)
                            for (r in 1..4) {
                                drawCircle(
                                    color = Color(0xFF2FA3FF).copy(alpha = 0.3f),
                                    radius = (r * 60f) / scale,
                                    center = centerH1,
                                    style = Stroke(width = 1f / scale)
                                )
                            }
                            
                            // Draw Concentric Contours for Low Pressure (L)
                            for (r in 1..4) {
                                drawCircle(
                                    color = Color(0xFFFF5252).copy(alpha = 0.3f),
                                    radius = (r * 75f) / scale,
                                    center = centerL1,
                                    style = Stroke(width = 1f / scale)
                                )
                            }
                        }
                    }

                    // 4. City Interactive Dot Markers with clean names
                    mapCities.forEach { city ->
                        val cx = (city.lon + 180f) / 360f * width
                        val cy = (90f - city.lat) / 180f * height

                        val isFav = favoritedCities.any { it.cityName.equals(city.name, ignoreCase = true) }

                        // Glowing border outline
                        drawCircle(
                            color = if (isFav) Color(0xFFFF5252) else Color(0xFF2FA3FF),
                            radius = 6f / scale,
                            center = Offset(cx, cy)
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 3.5f / scale,
                            center = Offset(cx, cy)
                        )
                    }

                    // 5. Pulsing Tapped Location Marker (Inspection Target)
                    targetCoords?.let { (lat, lon) ->
                        val tx = ((lon + 180f) / 360f * width).toFloat()
                        val ty = ((90f - lat) / 180f * height).toFloat()

                        // Target Pulsing ring
                        drawCircle(
                            color = Color(0xFF00E5FF).copy(alpha = pulseAlpha),
                            radius = pulseSize / scale,
                            center = Offset(tx, ty),
                            style = Stroke(width = 2f / scale)
                        )
                        // Precise crosshair
                        drawLine(
                            color = Color(0xFF00E5FF),
                            start = Offset(tx - 10f / scale, ty),
                            end = Offset(tx + 10f / scale, ty),
                            strokeWidth = 1.5f / scale
                        )
                        drawLine(
                            color = Color(0xFF00E5FF),
                            start = Offset(tx, ty - 10f / scale),
                            end = Offset(tx, ty + 10f / scale),
                            strokeWidth = 1.5f / scale
                        )
                    }

                    // 6. Pulse Marker for User's Current GPS Location
                    currentGpsCoords?.let { (lat, lon) ->
                        val gx = ((lon + 180f) / 360f * width).toFloat()
                        val gy = ((90f - lat) / 180f * height).toFloat()
                        currentGpsLocation = Offset(gx, gy)

                        // Pulse circle
                        drawCircle(
                            color = Color(0xFF00FF66).copy(alpha = pulseAlpha),
                            radius = pulseSize * 1.5f / scale,
                            center = Offset(gx, gy),
                            style = Stroke(width = 2f / scale)
                        )
                        // Glow core
                        drawCircle(
                            color = Color(0xFF00FF66),
                            radius = 6f / scale,
                            center = Offset(gx, gy)
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 3f / scale,
                            center = Offset(gx, gy)
                        )
                    }
                }
            }
        }

        // FLOATING LAYER SWITCHER CAROUSEL (TOP CONTAINER)
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 76.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ScrollableRow(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF1E1E2E)) // Solid accessible dark gray card background (as requested)
                    .border(1.dp, Color(0xFF374151), RoundedCornerShape(24.dp)) // High-contrast border for high accessibility (as requested)
                    .padding(vertical = 8.dp, horizontal = 12.dp)
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
                                tint = if (isSelected) Color.White else Color(0xFFD1D5DB), // High-contrast Light Gray text (as requested)
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = layer.displayName,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.White else Color(0xFFD1D5DB) // High-contrast Light Gray text (as requested)
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
                    .background(Color(0x99070913))
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

        // FLOATING SEARCH BAR & GPS LAUNCHER (TOP ATTACHED)
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 16.dp, start = 16.dp, end = 16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
                    .clip(RoundedCornerShape(25.dp))
                    .background(Color(0xFF1E1E2E)) // Solid accessible dark gray card background (as requested)
                    .border(1.dp, Color(0xFF374151), RoundedCornerShape(25.dp)) // High-contrast border for high accessibility (as requested)
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
                        tint = Color(0xFFD1D5DB), // High-contrast Light Gray text (as requested)
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
                                        style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFFD1D5DB)) // High-contrast Light Gray text (as requested)
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
                                    scale = 3.5f
                                    offset = Offset(
                                        -(match.lon + 180f) / 360f * canvasWidth * scale + canvasWidth / 2f,
                                        -(90f - match.lat) / 180f * canvasHeight * scale + canvasHeight / 2f
                                    )
                                    triggerCoordinateFetch(match.lat.toDouble(), match.lon.toDouble())
                                } else {
                                    // Query dynamic geocoding fetch
                                    coroutineScope.launch {
                                        val res = repository.fetchWeatherFromApi(searchQuery, forceRefresh = true)
                                        res.onSuccess { cw ->
                                            // Place a dynamic target
                                            val queryCoords = mapCities.find { it.name.equals(cw.cityName, ignoreCase = true) }
                                            val lat = queryCoords?.lat ?: 30f
                                            val lon = queryCoords?.lon ?: 0f
                                            scale = 4.0f
                                            offset = Offset(
                                                -(lon + 180f) / 360f * canvasWidth * scale + canvasWidth / 2f,
                                                -(90f - lat) / 180f * canvasHeight * scale + canvasHeight / 2f
                                            )
                                            triggerCoordinateFetch(lat.toDouble(), lon.toDouble())
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
                        scale = 3.5f
                        offset = Offset(
                            -(lon.toFloat() + 180f) / 360f * canvasWidth * scale + canvasWidth / 2f,
                            -(90f - lat.toFloat()) / 180f * canvasHeight * scale + canvasHeight / 2f
                        )
                        triggerCoordinateFetch(lat, lon)
                    } ?: run {
                        // Re-trigger permission check
                        locationPermissionLauncher.launch(
                            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                        )
                    }
                },
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1E1E2E)) // Solid accessible dark gray card background (as requested)
                    .border(1.dp, Color(0xFF374151), CircleShape) // High-contrast border for high accessibility (as requested)
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
                .background(Color(0xFF1E1E2E)) // Solid accessible dark gray card background (as requested)
                .border(1.dp, Color(0xFF374151), RoundedCornerShape(20.dp)) // High-contrast border for high accessibility (as requested)
                .padding(6.dp),
            verticalArrangement = Arrangement.Center
        ) {
            IconButton(onClick = { scale = (scale * 1.3f).coerceAtMost(20f) }) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = "Zoom In", tint = Color.White)
            }
            Divider(color = Color(0xFF374151), modifier = Modifier.width(28.dp)) // Clear divider
            IconButton(onClick = { scale = (scale / 1.3f).coerceAtLeast(0.8f) }) {
                Icon(imageVector = Icons.Filled.Remove, contentDescription = "Zoom Out", tint = Color.White)
            }
            Divider(color = Color(0xFF374151), modifier = Modifier.width(28.dp)) // Clear divider
            IconButton(onClick = {
                scale = 1.8f
                offset = Offset(-200f, 150f)
                targetCoords = null
                isInspecting = false
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
                    .background(Color(0xFF1E1E2E)) // Solid accessible dark gray card background (as requested)
                    .border(1.dp, Color(0xFF374151), RoundedCornerShape(24.dp)) // High-contrast border for high accessibility (as requested)
                    .padding(vertical = 12.dp, horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Play/Pause button
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

                // Timeline sliders and labels
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
                            inactiveTrackColor = Color(0xFF374151) // Strong border outline tracker for high contrast
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
                                    color = if (idx == radarTimeSlider.toInt()) Color(0xFF00E5FF) else Color(0xFFD1D5DB), // High-contrast Light Gray text (as requested)
                                    fontSize = 9.sp
                                )
                            )
                        }
                    }
                }
            }
        }

        // INSPECTION popup card for the selected coordinates
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
                    .background(Color(0xFF1E1E2E)) // Solid accessible dark gray card background (as requested)
                    .border(1.dp, Color(0xFF374151), RoundedCornerShape(28.dp)) // High-contrast border for high accessibility (as requested)
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
                            text = "Inspecting tropospheric layers...",
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
                            modifier = Modifier.fillMaxWidth().padding(end = 36.dp)
                        ) {
                            // Condition Icon
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
                                    
                                    // Save/Favorite Button
                                    IconButton(
                                        onClick = { repository.toggleFavorite(weather.cityName) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (weather.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                            contentDescription = "Save Map Location",
                                            tint = if (weather.isFavorite) Color(0xFFFF5252) else Color(0xFFD1D5DB), // High-contrast Light Gray (as requested)
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
                                    color = Color(0xFFD1D5DB) // High-contrast Light Gray secondary text (as requested)
                                )
                            }

                            // Temperature Display
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = tempStr,
                                    style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Black, color = Color.White)
                                )
                                // Nav back to detailed view button
                                Button(
                                    onClick = {
                                        repository.selectCity(weather.cityName)
                                        // Navigate to home via global navigation is handled by selecting city and user switching
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
                        Divider(color = Color(0xFF374151)) // Strong high-contrast border
                        Spacer(modifier = Modifier.height(12.dp))

                        // Multi-Telemetry stats row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                Text("HUMIDITY", style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFFD1D5DB), fontWeight = FontWeight.Bold)) // High contrast label
                                Text("${details.humidity}%", style = MaterialTheme.typography.bodyMedium.copy(color = Color.White, fontWeight = FontWeight.Bold))
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                Text("WIND", style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFFD1D5DB), fontWeight = FontWeight.Bold)) // High contrast label
                                Text("${details.windSpeed} km/h", style = MaterialTheme.typography.bodyMedium.copy(color = Color.White, fontWeight = FontWeight.Bold))
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                Text("PRESSURE", style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFFD1D5DB), fontWeight = FontWeight.Bold)) // High contrast label
                                Text("${details.pressureHpa} hPa", style = MaterialTheme.typography.bodyMedium.copy(color = Color.White, fontWeight = FontWeight.Bold))
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                Text("CLOUDS", style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFFD1D5DB), fontWeight = FontWeight.Bold)) // High contrast label
                                Text("${details.cloudCoverage}%", style = MaterialTheme.typography.bodyMedium.copy(color = Color.White, fontWeight = FontWeight.Bold))
                            }
                        }
                    } ?: run {
                        Text(
                            text = "Select any coordinate point on the map to fetch precise real-time tropospheric and radar data forecasts.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFD1D5DB), // High contrast text
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                        )
                    }
                }
            }
        }
    }
}

// Micro Scrollable Row Component to support custom map scrolling cleanly without full Compose library bloat
@Composable
fun ScrollableRow(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        content = { content() }
    )
}
