package com.example.ui.screens.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.foundation.border
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.example.ui.theme.LuxurySkyBlue
import com.example.ui.theme.LuxuryCyan
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.models.CityWeather
import com.example.data.models.WeatherDetails
import com.example.ui.components.SkySphereCard
import com.example.ui.components.WeatherConditionIcon
import com.example.ui.components.WeatherAnimatedBackground
import com.example.ui.components.isDayTime
import androidx.compose.material.icons.filled.Refresh
import kotlinx.coroutines.delay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.drawscope.Stroke
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.core.content.ContextCompat

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    modifier: Modifier = Modifier
) {
    val cityWeather by viewModel.selectedCityWeather.collectAsState()
    val isCelsius by viewModel.isCelsius.collectAsState()
    val windUnit by viewModel.windUnit.collectAsState()
    val context = LocalContext.current
    val locationManager = remember { context.getSystemService(Context.LOCATION_SERVICE) as LocationManager }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fineGranted || coarseGranted) {
            fetchGpsLocation(context, locationManager, viewModel)
        }
    }

    // Smooth entry transition
    AnimatedVisibility(
        visible = true,
        enter = fadeIn() + slideInVertically(initialOffsetY = { 50 }),
        modifier = modifier.fillMaxSize()
    ) {
        HomeScreenContent(
            cityWeather = cityWeather,
            isCelsius = isCelsius,
            windUnit = windUnit,
            onToggleFavorite = { viewModel.toggleFavorite(cityWeather.cityName) },
            onGpsClick = {
                val fineGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                val coarseGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                if (fineGranted || coarseGranted) {
                    fetchGpsLocation(context, locationManager, viewModel)
                } else {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }
            },
            onRefresh = { viewModel.refreshActiveCity() }
        )
    }
}

private fun fetchGpsLocation(context: Context, locationManager: LocationManager, viewModel: HomeViewModel) {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    ) {
        try {
            val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            val isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            val provider = when {
                isGpsEnabled -> LocationManager.GPS_PROVIDER
                isNetworkEnabled -> LocationManager.NETWORK_PROVIDER
                else -> null
            }
            if (provider != null) {
                val lastKnown = locationManager.getLastKnownLocation(provider)
                if (lastKnown != null) {
                    viewModel.loadWeatherForCurrentLocation(lastKnown.latitude, lastKnown.longitude)
                }
                locationManager.requestLocationUpdates(
                    provider,
                    0L,
                    0f,
                    object : LocationListener {
                        override fun onLocationChanged(location: Location) {
                            viewModel.loadWeatherForCurrentLocation(location.latitude, location.longitude)
                            locationManager.removeUpdates(this)
                        }
                        override fun onProviderEnabled(provider: String) {}
                        override fun onProviderDisabled(provider: String) {}
                        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                    }
                )
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }
}

@Composable
fun HomeScreenContent(
    cityWeather: CityWeather,
    isCelsius: Boolean,
    windUnit: String,
    onToggleFavorite: () -> Unit,
    onGpsClick: () -> Unit,
    onRefresh: () -> Unit
) {
    val details = cityWeather.weatherDetails
    val isDark = MaterialTheme.colorScheme.background.value == 0xFF070913.toULong()

    WeatherAnimatedBackground(
        condition = details.condition,
        sunrise = details.sunrise,
        sunset = details.sunset
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("home_screen_content"),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // TOP HEADER
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = cityWeather.cityName.uppercase(),
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Light,
                                letterSpacing = 3.sp,
                                color = MaterialTheme.colorScheme.onBackground
                            ),
                            modifier = Modifier.testTag("home_city_name")
                        )
                        Text(
                            text = cityWeather.country,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val infiniteTransition = rememberInfiniteTransition(label = "RefreshRotation")
                        val isRefreshing = remember { androidx.compose.runtime.mutableStateOf(false) }
                        
                        val rotation by infiniteTransition.animateFloat(
                            initialValue = 0f,
                            targetValue = if (isRefreshing.value) 360f else 0f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1000, easing = LinearEasing),
                                repeatMode = RepeatMode.Restart
                            ),
                            label = "SpinAngle"
                        )

                        IconButton(
                            onClick = {
                                isRefreshing.value = true
                                onRefresh()
                                CoroutineScope(Dispatchers.Main).launch {
                                    delay(1200)
                                    isRefreshing.value = false
                                }
                            },
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (isDark) Color(0xFF1E254C) else Color(0xFFE2E8F0))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh weather",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.rotate(rotation)
                            )
                        }

                        IconButton(
                            onClick = onGpsClick,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (isDark) Color(0xFF1E254C) else Color(0xFFE2E8F0))
                                .testTag("home_gps_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.MyLocation,
                                contentDescription = "Current Location",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                    IconButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (isDark) Color(0xFF1E254C) else Color(0xFFE2E8F0))
                            .testTag("home_favorite_button")
                    ) {
                        Icon(
                            imageVector = if (cityWeather.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = "Toggle Favorite",
                            tint = if (cityWeather.isFavorite) Color(0xFFFF5252) else MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }
        }

        // CURRENT WEATHER SUMMARY
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    // Weather illustration backing
                    WeatherConditionIcon(
                        condition = details.condition,
                        modifier = Modifier.size(130.dp),
                        tint = details.condition.startColor.copy(alpha = 0.15f)
                    )
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = formatTemp(details.currentTemp, isCelsius),
                            style = MaterialTheme.typography.displayLarge.copy(
                                color = MaterialTheme.colorScheme.onBackground,
                                fontWeight = FontWeight.ExtraLight
                            ),
                            modifier = Modifier.testTag("home_current_temp")
                        )
                        Text(
                            text = "°",
                            style = MaterialTheme.typography.displayMedium.copy(
                                color = LuxurySkyBlue,
                                fontWeight = FontWeight.Light
                            ),
                            modifier = Modifier.padding(bottom = 24.dp)
                        )
                    }
                }

                Text(
                    text = details.condition.displayName.uppercase(),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "FEELS LIKE ${formatTemp(details.feelsLike, isCelsius)}°  •  H: ${formatTemp(details.highTemp, isCelsius)}°  L: ${formatTemp(details.lowTemp, isCelsius)}°",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }

        // SMART WEATHER INSIGHTS CARD (Premium Flagship Feature)
        item {
            val insights = remember(details, isCelsius) {
                generateSmartInsights(details, isCelsius)
            }
            SkySphereCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.WbSunny,
                        contentDescription = null,
                        tint = Color(0xFFFFD54F),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "SMART WEATHER INSIGHTS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = LuxurySkyBlue,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp
                        )
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
                insights.forEach { insight ->
                    Row(
                        verticalAlignment = Alignment.Top,
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(top = 6.dp)
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF00E5FF))
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = insight,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                lineHeight = 20.sp,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        )
                    }
                }
            }
        }

        // HOURLY FORECAST
        item {
            Column {
                Text(
                    text = "HOURLY FORECAST",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                )
                Spacer(modifier = Modifier.height(12.dp))
                
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp)
                ) {
                    items(details.hourlyForecast) { hour ->
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isDark) Color(0xFF13172E) else Color(0xFFFFFFFF)
                            ),
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier
                                .width(82.dp)
                                .border(
                                    1.dp,
                                    if (isDark) Color(0xFF1D2447) else Color(0xFFE2E8F0),
                                    MaterialTheme.shapes.medium
                                )
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(vertical = 16.dp)
                                    .fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = hour.time,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp
                                    )
                                )
                                WeatherConditionIcon(
                                    condition = hour.condition,
                                    modifier = Modifier.size(24.dp)
                                )
                                Text(
                                    text = "${formatTemp(hour.temperature, isCelsius)}°",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                )
                                if (hour.precipitationChance > 0) {
                                    Text(
                                        text = "${hour.precipitationChance}%",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = LuxuryCyan,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp
                                        )
                                    )
                                } else {
                                    Spacer(modifier = Modifier.height(14.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        // 7-DAY FORECAST
        item {
            Column {
                Text(
                    text = "7-DAY OUTLOOK",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                )
                Spacer(modifier = Modifier.height(12.dp))
                
                SkySphereCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    details.dailyForecast.forEachIndexed { index, day ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = day.dayName,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onBackground
                                ),
                                modifier = Modifier.width(80.dp)
                            )
                            
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Start,
                                modifier = Modifier.weight(1f)
                            ) {
                                WeatherConditionIcon(
                                    condition = day.condition,
                                    modifier = Modifier.size(22.dp)
                                )
                                if (day.precipitationChance > 0) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "${day.precipitationChance}%",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = LuxuryCyan,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        )
                                    )
                                }
                            }

                            Row(
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${formatTemp(day.lowTemp, isCelsius)}°",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                // Custom atmospheric range bar
                                Box(
                                    modifier = Modifier
                                        .width(60.dp)
                                        .height(4.dp)
                                        .clip(CircleShape)
                                        .background(
                                            Brush.horizontalGradient(
                                                colors = listOf(
                                                    LuxurySkyBlue.copy(alpha = 0.3f),
                                                    LuxurySkyBlue,
                                                    Color(0xFFFFB300)
                                                )
                                            )
                                        )
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "${formatTemp(day.highTemp, isCelsius)}°",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = MaterialTheme.colorScheme.onBackground,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }
                        if (index < details.dailyForecast.lastIndex) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(if (isDark) Color(0xFF1E254C) else Color(0xFFE2E8F0))
                            )
                        }
                    }
                }
            }
        }

        // TELEMETRY DETAILED GRID (Balanced 2x3 Grid with Full-Width Sunrise/Sunset Arc Card)
        item {
            Column {
                Text(
                    text = "ATMOSPHERIC METRICS",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        TelemetryCard(
                            title = "AIR QUALITY",
                            value = "${details.airQuality.aqi}",
                            subtitle = details.airQuality.level,
                            icon = Icons.Filled.Air,
                            iconColor = LuxuryCyan
                        )
                        TelemetryCard(
                            title = "WIND SPEED",
                            value = formatWind(details.windSpeed, windUnit),
                            subtitle = "$windUnit • NE",
                            icon = Icons.Filled.Air,
                            iconColor = LuxurySkyBlue
                        )
                        TelemetryCard(
                            title = "BAROMETER",
                            value = "${details.pressureHpa}",
                            subtitle = "hPa • Steady",
                            icon = Icons.Filled.Compress,
                            iconColor = Color(0xFFB0BEC5)
                        )
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        TelemetryCard(
                            title = "UV INDEX",
                            value = "${details.uvIndex}",
                            subtitle = getUvDescription(details.uvIndex),
                            icon = Icons.Filled.Thermostat,
                            iconColor = Color(0xFFFF7043)
                        )
                        TelemetryCard(
                            title = "HUMIDITY",
                            value = "${details.humidity}%",
                            subtitle = "Dew point is ${formatTemp(52, isCelsius)}°",
                            icon = Icons.Filled.WaterDrop,
                            iconColor = LuxurySkyBlue
                        )
                        TelemetryCard(
                            title = "VISIBILITY",
                            value = "${details.visibilityKm} km",
                            subtitle = "Perfect clear view",
                            icon = Icons.Filled.Visibility,
                            iconColor = LuxuryCyan
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Text(
                    text = "SUNRISE & SUNSET",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                )
                Spacer(modifier = Modifier.height(12.dp))
                
                SkySphereCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SunriseSunsetArc(
                        sunrise = details.sunrise,
                        sunset = details.sunset,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
}

@Composable
fun TelemetryCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    iconColor: Color
) {
    SkySphereCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp
                )
            )
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Light,
                color = MaterialTheme.colorScheme.onBackground
            )
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
    }
}

private fun getUvDescription(uv: Int): String {
    return when {
        uv <= 2 -> "Low"
        uv <= 5 -> "Moderate"
        uv <= 7 -> "High"
        uv <= 10 -> "Very High"
        else -> "Extreme"
    }
}

private fun formatTemp(tempF: Int, isCelsius: Boolean): String {
    return if (isCelsius) {
        val tempC = ((tempF - 32) * 5 / 9)
        "$tempC"
    } else {
        "$tempF"
    }
}

private fun formatWind(speedKph: Double, unit: String): String {
    return when (unit) {
        "km/h" -> "${(speedKph * 10).toInt() / 10.0} km/h"
        "mph" -> "${((speedKph * 0.621371) * 10).toInt() / 10.0} mph"
        "knots" -> "${((speedKph * 0.539957) * 10).toInt() / 10.0} knots"
        else -> "${(speedKph * 10).toInt() / 10.0} km/h"
    }
}

fun generateSmartInsights(details: WeatherDetails, isCelsius: Boolean): List<String> {
    val insights = mutableListOf<String>()
    
    val tempF = details.currentTemp
    val tempC = (((tempF - 32) * 5) / 9)
    val tC = tempC.toInt()
    val tF = tempF.toInt()
    
    if (isCelsius) {
        when {
            tC < 10 -> insights.add("It feels chilly at ${tC}°C. Layer up!")
            tC in 10..17 -> insights.add("Feels cool (${tC}°C). A light jacket is recommended.")
            tC in 18..25 -> insights.add("Feels pleasant (${tC}°C). Great weather for walking!")
            tC in 26..33 -> insights.add("Feels warm (${tC}°C). Stay hydrated out there.")
            else -> insights.add("Extreme heat alert (${tC}°C). Limit outdoor exposure.")
        }
    } else {
        when {
            tF < 50 -> insights.add("It feels chilly at ${tF}°F. Layer up!")
            tF in 50..64 -> insights.add("Feels cool (${tF}°F). A light jacket is recommended.")
            tF in 65..77 -> insights.add("Feels pleasant (${tF}°F). Great weather for walking!")
            tF in 78..92 -> insights.add("Feels warm (${tF}°F). Stay hydrated out there.")
            else -> insights.add("Extreme heat alert (${tF}°F). Limit outdoor exposure.")
        }
    }

    val hasRainChance = details.dailyForecast.any { it.precipitationChance > 30 } || details.hourlyForecast.any { it.precipitationChance > 30 }
    if (hasRainChance) {
        val rainHour = details.hourlyForecast.firstOrNull { it.precipitationChance > 30 }
        if (rainHour != null) {
            insights.add("Carry an umbrella; rain is expected around ${rainHour.time}.")
        } else {
            insights.add("Carry an umbrella; high probability of rain today.")
        }
    } else {
        insights.add("No rain expected today.")
    }

    if (details.windSpeed > 24.0) {
        insights.add("Secure loose outdoor items against strong wind gusts.")
    } else {
        insights.add("Winds are gentle and calming.")
    }

    when {
        details.uvIndex <= 2 -> insights.add("UV index is low. Safe to enjoy the day.")
        details.uvIndex in 3..5 -> insights.add("UV index is moderate. Apply sunscreen if outdoors.")
        details.uvIndex in 6..7 -> insights.add("UV index is high. Sunglasses and hats recommended.")
        else -> insights.add("Very high UV index. Limit sun exposure between 11 AM and 3 PM.")
    }

    if (details.airQuality.aqi > 100) {
        insights.add("Air quality is poor; unhealthy for sensitive groups.")
    } else if (details.airQuality.aqi > 50) {
        insights.add("Air quality is moderate today.")
    } else {
        insights.add("Air quality is pristine. Perfect for deep breathing.")
    }

    return insights
}

@Composable
fun SunriseSunsetArc(
    sunrise: String,
    sunset: String,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.background.value == 0xFF070913.toULong()
    
    val progress = remember(sunrise, sunset) {
        try {
            val now = java.util.Calendar.getInstance()
            val hour = now.get(java.util.Calendar.HOUR_OF_DAY)
            val minute = now.get(java.util.Calendar.MINUTE)
            val currentMinutes = hour * 60 + minute

            fun parseTimeToMinutes(timeStr: String): Int {
                val cleaned = timeStr.trim().uppercase()
                val isPm = cleaned.endsWith("PM")
                val isAm = cleaned.endsWith("AM")
                val timePart = cleaned.replace("AM", "").replace("PM", "").trim()
                val parts = timePart.split(":")
                var h = parts[0].toInt()
                val m = parts[1].toInt()
                if (isPm && h < 12) h += 12
                if (isAm && h == 12) h = 0
                return h * 60 + m
            }

            val sunriseMin = parseTimeToMinutes(sunrise)
            val sunsetMin = parseTimeToMinutes(sunset)
            val totalDaylight = sunsetMin - sunriseMin
            
            if (currentMinutes <= sunriseMin) 0f
            else if (currentMinutes >= sunsetMin) 1f
            else (currentMinutes - sunriseMin).toFloat() / totalDaylight.toFloat()
        } catch (e: Exception) {
            0.5f
        }
    }

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(1500, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "SunProgress"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        androidx.compose.foundation.Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
        ) {
            val width = size.width
            val height = size.height
            val padding = 12.dp.toPx()
            
            val arcWidth = width - (padding * 2)
            val arcHeight = height * 1.6f
            val arcLeft = padding
            val arcTop = height - (arcHeight / 2) - 10.dp.toPx()
            
            drawArc(
                color = if (isDark) Color(0x3DFFFFFF) else Color(0x24000000),
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                style = Stroke(
                    width = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
                ),
                topLeft = Offset(arcLeft, arcTop),
                size = Size(arcWidth, arcHeight)
            )

            drawArc(
                color = Color(0xFFFFD54F),
                startAngle = 180f,
                sweepAngle = 180f * animatedProgress,
                useCenter = false,
                style = Stroke(
                    width = 2.5.dp.toPx()
                ),
                topLeft = Offset(arcLeft, arcTop),
                size = Size(arcWidth, arcHeight)
            )

            drawLine(
                color = if (isDark) Color(0x2BFFFFFF) else Color(0x1B000000),
                start = Offset(0f, height - 12.dp.toPx()),
                end = Offset(width, height - 12.dp.toPx()),
                strokeWidth = 1.dp.toPx()
            )

            val angle = 180f + (180f * animatedProgress)
            val angleRad = Math.toRadians(angle.toDouble())
            val sunX = (width / 2) + (arcWidth / 2) * Math.cos(angleRad)
            val sunY = (height - 10.dp.toPx() + arcHeight / 2) + (arcHeight / 2) * Math.sin(angleRad) - (arcHeight / 2)

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFFD54F).copy(alpha = 0.4f), Color.Transparent),
                    center = Offset(sunX.toFloat(), sunY.toFloat()),
                    radius = 18.dp.toPx()
                ),
                radius = 18.dp.toPx(),
                center = Offset(sunX.toFloat(), sunY.toFloat())
            )

            drawCircle(
                color = Color(0xFFFFB300),
                radius = 5.dp.toPx(),
                center = Offset(sunX.toFloat(), sunY.toFloat())
            )
        }
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.Start) {
                Text(
                    text = "SUNRISE",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = sunrise,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp
                    )
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "SUNSET",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = sunset,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp
                    )
                )
            }
        }
    }
}
