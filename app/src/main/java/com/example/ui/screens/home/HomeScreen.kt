package com.example.ui.screens.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import com.example.ui.icons.SkySphereIcons
import androidx.compose.foundation.border
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.example.ui.theme.LuxurySkyBlue
import com.example.ui.theme.LuxuryCyan
import com.example.ui.components.WindGaugeCard
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Surface
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.zIndex
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
import com.example.ui.components.isDayTime
import com.example.data.models.CityWeather
import com.example.data.models.WeatherDetails
import com.example.ui.components.SkySphereCard
import com.example.ui.components.WeatherConditionIcon
import com.example.ui.components.WeatherAnimatedBackground
import kotlinx.coroutines.delay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeOut
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.material3.FloatingActionButton
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    modifier: Modifier = Modifier
) {
    val cityWeather by viewModel.selectedCityWeather.collectAsState()
    val allCities by viewModel.allCities.collectAsState()
    val isCelsius by viewModel.isCelsius.collectAsState()
    val isUpdating by viewModel.isUpdating.collectAsState()
    val windUnit by viewModel.windUnit.collectAsState()
    val errorState by viewModel.errorState.collectAsState()
    val isGpsActive by viewModel.isGpsActive.collectAsState()
    val context = LocalContext.current
    val locationManager = remember { context.getSystemService(Context.LOCATION_SERVICE) as LocationManager }

    LaunchedEffect(isGpsActive) {
        if (isGpsActive) {
            fetchGpsLocation(context, locationManager, viewModel)
        }
    }

    var showIntelligentHub by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fineGranted || coarseGranted) {
            fetchGpsLocation(context, locationManager, viewModel)
        } else {
            viewModel.setError("Location permissions were denied. Please enable them in your device settings.")
        }
    }

    Crossfade(
        targetState = showIntelligentHub,
        animationSpec = tween(500),
        label = "ScreenTransition"
    ) { isHubVisible ->
        if (isHubVisible) {
            IntelligentHub(
                cityWeather = cityWeather,
                allCities = allCities,
                isCelsius = isCelsius,
                onClose = { showIntelligentHub = false },
                modifier = modifier
            )
        } else {
            // Smooth entry transition
            AnimatedVisibility(
                visible = true,
                enter = fadeIn() + slideInVertically(initialOffsetY = { 50 }),
                modifier = modifier.fillMaxSize()
            ) {
                HomeScreenContent(
                    cityWeather = cityWeather,
                    isCelsius = isCelsius,
                    isUpdating = isUpdating,
                    windUnit = windUnit,
                    errorState = errorState,
                    onClearError = { viewModel.clearError() },
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
                    onRefresh = { viewModel.refreshActiveCity() },
                    onOpenHub = { showIntelligentHub = true }
                )
            }
        }
    }
}

private fun fetchGpsLocation(context: Context, locationManager: LocationManager, viewModel: HomeViewModel) {
    val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    if (hasFine || hasCoarse) {
        try {
            if (!LocationManagerCompat.isLocationEnabled(locationManager)) {
                viewModel.setError("GPS location services are disabled on this device. Please turn them on in system settings.")
                return
            }
            val isGpsEnabled = hasFine && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            val isNetworkEnabled = (hasFine || hasCoarse) && locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            val provider = when {
                isNetworkEnabled -> LocationManager.NETWORK_PROVIDER
                isGpsEnabled -> LocationManager.GPS_PROVIDER
                else -> null
            }
            if (provider != null) {
                try {
                    val lastKnown = locationManager.getLastKnownLocation(provider)
                    if (lastKnown != null) {
                        viewModel.loadWeatherForCurrentLocation(lastKnown.latitude, lastKnown.longitude)
                    }
                } catch (t: Throwable) {
                    // Ignore lastKnown errors
                }

                try {
                    val consumer = androidx.core.util.Consumer<Location?> { location ->
                        if (location != null) {
                            viewModel.loadWeatherForCurrentLocation(location.latitude, location.longitude)
                        }
                    }
                    LocationManagerCompat.getCurrentLocation(
                        locationManager,
                        provider,
                        null as android.os.CancellationSignal?,
                        ContextCompat.getMainExecutor(context),
                        consumer
                    )
                } catch (t: Throwable) {
                    // Ignore LocationManagerCompat fallback errors
                }
            } else {
                viewModel.setError("GPS location services are disabled on this device. Please turn them on in system settings.")
            }
        } catch (e: SecurityException) {
            viewModel.setError("Location permission is required to detect your location.")
        } catch (e: Exception) {
            viewModel.setError("GPS location detection failed. Please search for a city manually.")
        }
    } else {
        viewModel.setError("Location permission denied. Please allow location access in your device settings.")
    }
}

@Composable
fun HomeScreenContent(
    cityWeather: CityWeather,
    isCelsius: Boolean,
    isUpdating: Boolean = false,
    windUnit: String,
    errorState: String?,
    onClearError: () -> Unit,
    onToggleFavorite: () -> Unit,
    onGpsClick: () -> Unit,
    onRefresh: () -> Unit,
    onOpenHub: () -> Unit
) {
    val details = cityWeather.weatherDetails
    val isDark = true

    val lazyListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var pullOffset by remember { mutableFloatStateOf(0f) }
    var isPullRefreshing by remember { mutableStateOf(false) }

    val locationText = remember(cityWeather.cityName, cityWeather.region, cityWeather.country) {
        buildString {
            append(cityWeather.cityName.uppercase())
            if (!cityWeather.region.isNullOrBlank()) {
                append(", ")
                append(cityWeather.region.uppercase())
            }
            if (cityWeather.country.isNotBlank()) {
                append(", ")
                append(cityWeather.country.uppercase())
            }
        }
    }

    val showStickyHeader by remember {
        derivedStateOf {
            val firstIndex = lazyListState.firstVisibleItemIndex
            val firstOffset = lazyListState.firstVisibleItemScrollOffset
            if (errorState != null) {
                firstIndex > 1 || (firstIndex == 1 && firstOffset > 60)
            } else {
                firstIndex > 0 || (firstIndex == 0 && firstOffset > 60)
            }
        }
    }

    LaunchedEffect(isUpdating) {
        if (!isUpdating) {
            isPullRefreshing = false
            pullOffset = 0f
        }
    }

    val nestedScrollConnection = remember {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            override fun onPreScroll(
                available: androidx.compose.ui.geometry.Offset,
                source: androidx.compose.ui.input.nestedscroll.NestedScrollSource
            ): androidx.compose.ui.geometry.Offset {
                if (isPullRefreshing || isUpdating) return androidx.compose.ui.geometry.Offset.Zero
                val delta = available.y
                if (delta < 0 && pullOffset > 0f) {
                    val newOffset = (pullOffset + delta).coerceAtLeast(0f)
                    val consumed = newOffset - pullOffset
                    pullOffset = newOffset
                    return androidx.compose.ui.geometry.Offset(0f, consumed)
                }
                return androidx.compose.ui.geometry.Offset.Zero
            }

            override fun onPostScroll(
                consumed: androidx.compose.ui.geometry.Offset,
                available: androidx.compose.ui.geometry.Offset,
                source: androidx.compose.ui.input.nestedscroll.NestedScrollSource
            ): androidx.compose.ui.geometry.Offset {
                if (isPullRefreshing || isUpdating) return androidx.compose.ui.geometry.Offset.Zero
                // ONLY trigger pull-to-refresh on direct User Touch Input when scroll is at top
                if (source == androidx.compose.ui.input.nestedscroll.NestedScrollSource.UserInput) {
                    val delta = available.y
                    if (delta > 0 && lazyListState.firstVisibleItemIndex == 0 && lazyListState.firstVisibleItemScrollOffset == 0) {
                        pullOffset += delta * 0.45f
                        return androidx.compose.ui.geometry.Offset(0f, delta)
                    }
                }
                return androidx.compose.ui.geometry.Offset.Zero
            }

            override suspend fun onPreFling(available: androidx.compose.ui.unit.Velocity): androidx.compose.ui.unit.Velocity {
                if (pullOffset > 0f) {
                    if (!isPullRefreshing && !isUpdating && pullOffset > 180f) {
                        isPullRefreshing = true
                        pullOffset = 120f
                        onRefresh()
                    } else {
                        pullOffset = 0f
                    }
                    return available
                }
                return androidx.compose.ui.unit.Velocity.Zero
            }

            override suspend fun onPostFling(
                consumed: androidx.compose.ui.unit.Velocity,
                available: androidx.compose.ui.unit.Velocity
            ): androidx.compose.ui.unit.Velocity {
                if (pullOffset > 0f && !isPullRefreshing) {
                    pullOffset = 0f
                }
                return super.onPostFling(consumed, available)
            }
        }
    }

    val animatedPullOffset by animateFloatAsState(
        targetValue = pullOffset,
        animationSpec = tween(300),
        label = "PullOffsetAnimation"
    )

    WeatherAnimatedBackground(
        condition = details.condition,
        sunrise = details.sunrise,
        sunset = details.sunset,
        visibilityKm = details.visibilityKm,
        windSpeed = details.windSpeed
    ) {
        if (cityWeather.cityName == "Loading...") {
            HomeScreenSkeleton()
        } else {
            Box(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(nestedScrollConnection)
        ) {
            // Elegant glowing refresh spinner indicator
            if (animatedPullOffset > 0f) {
                val progressFraction = (animatedPullOffset / 180f).coerceIn(0f, 1f)
                val rotationAngle = if (isPullRefreshing) {
                    val infiniteTransition = rememberInfiniteTransition(label = "PullRefresh")
                    val spin by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 360f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1000, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "PullSpin"
                    )
                    spin
                } else {
                    progressFraction * 360f
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = (animatedPullOffset * 0.35f).dp.coerceAtMost(28.dp))
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1E1E2E)) // Solid accessible dark gray card background (as requested)
                        .border(1.dp, Color(0xFF374151), CircleShape), // High-contrast border for high accessibility (as requested)
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = SkySphereIcons.Refresh,
                        contentDescription = "Refreshing",
                        tint = if (isPullRefreshing) Color(0xFF2FA3FF) else Color(0xFF2FA3FF).copy(alpha = progressFraction),
                        modifier = Modifier
                            .size(20.dp)
                            .rotate(rotationAngle)
                    )
                }
            }

            // STICKY LOCATION HEADER (Appears smoothly when scrolling down)
            AnimatedVisibility(
                visible = showStickyHeader,
                enter = fadeIn(animationSpec = tween(280)) + slideInVertically(
                    initialOffsetY = { -it },
                    animationSpec = tween(280)
                ),
                exit = fadeOut(animationSpec = tween(220)) + slideOutVertically(
                    targetOffsetY = { -it },
                    animationSpec = tween(220)
                ),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .zIndex(100f)
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("home_sticky_location_header"),
                    color = Color(0xFF0F172A).copy(alpha = 0.95f),
                    shadowElevation = 8.dp,
                    border = BorderStroke(
                        width = 1.dp,
                        color = Color(0xFF1E293B)
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = locationText,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.8.sp,
                                color = MaterialTheme.colorScheme.onBackground,
                                fontSize = 14.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.testTag("home_sticky_location_text")
                        )
                    }
                }
            }

            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationY = animatedPullOffset }
                    .testTag("home_screen_content"),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
            // ERROR WARNING BANNER
            if (errorState != null) {
                item(key = "home_error_banner") {
                    SkySphereCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("home_error_banner"),
                        onClick = onClearError
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = SkySphereIcons.Warning,
                                    contentDescription = "Warning",
                                    tint = Color(0xFFFF5252),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "METEOROLOGICAL TELEMETRY ALERT",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = Color(0xFFFF5252),
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.sp
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = errorState,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = MaterialTheme.colorScheme.onBackground
                                        )
                                    )
                                }
                            }
                            IconButton(onClick = onClearError) {
                                Icon(
                                    imageVector = SkySphereIcons.Close,
                                    contentDescription = "Dismiss error",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // TOP HEADER
            item(key = "home_top_header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = locationText,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Medium,
                                letterSpacing = 2.sp,
                                color = MaterialTheme.colorScheme.onBackground,
                                fontSize = 18.sp
                            ),
                            modifier = Modifier.testTag("home_city_name")
                        )
                        if (!cityWeather.localTime.isNullOrBlank() || isUpdating) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (!cityWeather.localTime.isNullOrBlank()) {
                                    Text(
                                        text = cityWeather.localTime,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Medium
                                        )
                                    )
                                }
                                if (isUpdating) {
                                    if (!cityWeather.localTime.isNullOrBlank()) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(10.dp),
                                            strokeWidth = 1.5.dp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Updating...",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onGpsClick,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (isDark) Color(0xFF1E254C) else Color(0xFFE2E8F0))
                                .testTag("home_gps_button")
                        ) {
                            Icon(
                                imageVector = SkySphereIcons.MyLocation,
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
                            imageVector = if (cityWeather.isFavorite) SkySphereIcons.VaultActive else SkySphereIcons.Vault,
                            contentDescription = "Toggle Favorite",
                            tint = if (cityWeather.isFavorite) Color(0xFFFF5252) else MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }
        }



        // 2. Large current temperature card with weather condition
        item(key = "home_temp_card") {
            SkySphereCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("current_weather_temp_card")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AnimatedContent(
                                targetState = details.currentTemp,
                                transitionSpec = {
                                    if (targetState > initialState) {
                                        slideInVertically { height -> height } + fadeIn() togetherWith
                                                slideOutVertically { height -> -height } + fadeOut()
                                    } else {
                                        slideInVertically { height -> -height } + fadeIn() togetherWith
                                                slideOutVertically { height -> height } + fadeOut()
                                    }.using(SizeTransform(clip = false))
                                },
                                label = "TempAnimation"
                            ) { targetTemp ->
                                Text(
                                    text = formatTemp(targetTemp, isCelsius),
                                    style = MaterialTheme.typography.displayLarge.copy(
                                        color = MaterialTheme.colorScheme.onBackground,
                                        fontWeight = FontWeight.Light,
                                        fontSize = 72.sp
                                    ),
                                    modifier = Modifier.testTag("home_current_temp")
                                )
                            }
                            Text(
                                text = "°",
                                style = MaterialTheme.typography.displayMedium.copy(
                                    color = LuxurySkyBlue,
                                    fontWeight = FontWeight.Light,
                                    fontSize = 48.sp
                                ),
                                modifier = Modifier.padding(bottom = 24.dp)
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
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
                            text = "H: ${formatTemp(details.highTemp, isCelsius)}°  •  L: ${formatTemp(details.lowTemp, isCelsius)}°",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                    
                    // Animated weather icon
                    Box(
                        modifier = Modifier.size(110.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        AnimatedContent(
                            targetState = details,
                            transitionSpec = {
                                scaleIn(animationSpec = tween(500)) + fadeIn(animationSpec = tween(500)) togetherWith
                                        scaleOut(animationSpec = tween(500)) + fadeOut(animationSpec = tween(500))
                            },
                            label = "IconAnimation"
                        ) { targetDetails ->
                            WeatherConditionIcon(
                                weatherDetails = targetDetails,
                                modifier = Modifier.size(100.dp)
                            )
                        }
                    }
                }
            }
        }

        // 3. Feels Like, Humidity, Wind Speed, Pressure, UV Index
        item(key = "home_metrics_card") {
            SkySphereCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("key_atmospheric_metrics_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "CURRENT CONDITIONS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = LuxurySkyBlue,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            KeyMetricCell(
                                title = "FEELS LIKE",
                                value = "${formatTemp(details.feelsLike, isCelsius)}°",
                                icon = SkySphereIcons.Thermostat,
                                iconColor = Color(0xFFFFB74D)
                            )
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            KeyMetricCell(
                                title = "HUMIDITY",
                                value = "${details.humidity}%",
                                icon = SkySphereIcons.Humidity,
                                iconColor = LuxurySkyBlue
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            KeyMetricCell(
                                title = "WIND SPEED",
                                value = formatWind(details.windSpeed, windUnit),
                                icon = SkySphereIcons.Wind,
                                iconColor = LuxurySkyBlue
                            )
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            KeyMetricCell(
                                title = "PRESSURE",
                                value = "${details.pressureHpa} hPa",
                                icon = SkySphereIcons.Pressure,
                                iconColor = Color(0xFFB0BEC5)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    KeyMetricCell(
                        title = "UV INDEX",
                        value = "${details.uvIndex} - ${getUvDescription(details.uvIndex)}",
                        icon = SkySphereIcons.UVIndex,
                        iconColor = Color(0xFFFF7043)
                    )
                }
            }
        }



        // HOURLY FORECAST
        item(key = "home_hourly_forecast") {
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
                    itemsIndexed(details.hourlyForecast, key = { index, _ -> "hourly_card_$index" }) { _, hour ->
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFF1E1E2E) // Solid accessible dark gray card background (as requested)
                            ),
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier
                                .width(82.dp)
                                .border(
                                    1.dp,
                                    Color(0xFF374151), // High-contrast border for high accessibility (as requested)
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
                                    isNight = hour.isNight,
                                    modifier = Modifier.size(24.dp)
                                )
                                Text(
                                    text = "${formatTemp(hour.temperature, isCelsius)}°",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                )
                                Text(
                                    text = "${hour.precipitationChance.coerceIn(0, 100)}%",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = if (hour.precipitationChance > 0) LuxuryCyan else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        // 10-DAY FORECAST
        item(key = "home_10day_forecast") {
            Column {
                Text(
                    text = "10-DAY FORECAST",
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

        // 5.5. Wind Conditions Gauge
        item {
            WindGaugeCard(
                windSpeedKmH = details.windSpeed,
                windDirection = details.windDirection,
                windUnit = windUnit,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }

        // 6. Weather details
        item {
            Column {
                Text(
                    text = "WEATHER DETAILS",
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
                            icon = SkySphereIcons.Wind,
                            iconColor = LuxuryCyan
                        )
                        TelemetryCard(
                            title = "UV INDEX",
                            value = "${details.uvIndex}",
                            subtitle = if (details.uvIndex <= 2) "Low exposure" else if (details.uvIndex <= 5) "Moderate exposure" else "High exposure",
                            icon = SkySphereIcons.UVIndex,
                            iconColor = LuxurySkyBlue
                        )
                        TelemetryCard(
                            title = "CLOUD COVERAGE",
                            value = "${details.cloudCoverage}%",
                            subtitle = if (details.cloudCoverage < 10) "Completely clear" else if (details.cloudCoverage < 50) "Scattered clouds" else "Overcast skies",
                            icon = SkySphereIcons.Cloud,
                            iconColor = LuxurySkyBlue
                        )
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        TelemetryCard(
                            title = "VISIBILITY",
                            value = "${details.visibilityKm} km",
                            subtitle = "Atmospheric clarity",
                            icon = SkySphereIcons.Visibility,
                            iconColor = LuxuryCyan
                        )
                        TelemetryCard(
                            title = "DEW POINT",
                            value = "${formatTemp(details.currentTemp - ((100 - details.humidity) / 5), isCelsius)}°",
                            subtitle = "Condensation index",
                            icon = SkySphereIcons.Humidity,
                            iconColor = LuxuryCyan
                        )
                        TelemetryCard(
                            title = "BAROMETER",
                            value = "${details.pressureHpa} hPa",
                            subtitle = "Steady pressure",
                            icon = SkySphereIcons.Pressure,
                            iconColor = Color(0xFFB0BEC5)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                com.example.ui.components.HumidityChartComponent(
                    details = details,
                    isCelsius = isCelsius,
                    modifier = Modifier.fillMaxWidth().testTag("humidity_spline_chart")
                )

                Spacer(modifier = Modifier.height(24.dp))
                
                com.example.ui.components.AstronomyCard(
                    sunrise = details.sunrise,
                    sunset = details.sunset,
                    latitude = cityWeather.latitude,
                    longitude = cityWeather.longitude,
                    cityName = cityWeather.cityName,
                    modifier = Modifier.fillMaxWidth().testTag("astronomy_card")
                )
            }
        }

        // 7. Smart Weather Insights at the bottom
        item {
            val advices = remember(details, isCelsius) {
                com.example.data.processing.WeatherAdviceGenerator.generateAdvice(details, isCelsius)
            }
            SkySphereCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = SkySphereIcons.Sunny,
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
                Spacer(modifier = Modifier.height(16.dp))
                
                advices.forEachIndexed { index, advice ->
                    val priorityColor = when (advice.priority) {
                        com.example.data.processing.AdvicePriority.CRITICAL -> Color(0xFFFF5252)
                        com.example.data.processing.AdvicePriority.WARNING -> Color(0xFFFFB74D)
                        com.example.data.processing.AdvicePriority.INFO -> Color(0xFF40C4FF)
                        com.example.data.processing.AdvicePriority.COMFORT -> Color(0xFF69F0AE)
                    }
                    
                    Row(
                        verticalAlignment = Alignment.Top,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(priorityColor.copy(alpha = 0.15f))
                        ) {
                            Icon(
                                imageVector = advice.icon,
                                contentDescription = null,
                                tint = priorityColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        
                        Spacer(modifier = Modifier.width(12.dp))
                        
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = advice.title,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = advice.description,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    lineHeight = 20.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                    
                    if (index < advices.lastIndex) {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }

        // 8. COGNITIVE WEATHER HUB Entrance Card at the bottom
        item {
            SkySphereCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("intelligent_hub_entrance_card"),
                onClick = onOpenHub
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(Color(0xFF2FA3FF), Color(0xFF00C6FF))
                                )
                            )
                    ) {
                        Icon(
                            imageVector = SkySphereIcons.AutoAwesome,
                            contentDescription = "Intelligent Cognitive Hub",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "COGNITIVE WEATHER HUB",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color(0xFF2FA3FF),
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "AI Assistant, Smart Alerts & Scores",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Explore natural summaries, comparisons & travel planners.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = SkySphereIcons.ChevronRight,
                        contentDescription = "Open Hub",
                        tint = Color(0x7FFFFFFF),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
        }

        // FLOATING ACTION BUTTON short-cut to launch Intelligent AI Weather Hub
        FloatingActionButton(
            onClick = onOpenHub,
            containerColor = Color.Transparent,
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .testTag("ai_hub_fab")
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(
                        Brush.linearGradient(colors = listOf(Color(0xFF2FA3FF), Color(0xFF00C6FF))),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = SkySphereIcons.AutoAwesome,
                    contentDescription = "AI Hub shortcut",
                    modifier = Modifier.size(24.dp)
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

@Composable
fun KeyMetricCell(
    title: String,
    value: String,
    icon: ImageVector,
    iconColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(iconColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            )
        }
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
    return com.example.data.processing.WeatherAdviceGenerator.generateSimpleInsights(details, isCelsius)
}

@Composable
fun SunriseSunsetArc(
    sunrise: String,
    sunset: String,
    modifier: Modifier = Modifier
) {
    val isDark = true
    
    val state = remember(sunrise, sunset) {
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
            
            if (currentMinutes in (sunriseMin + 1)..<sunsetMin) {
                val totalDaylight = sunsetMin - sunriseMin
                val progress = (currentMinutes - sunriseMin).toFloat() / totalDaylight.toFloat()
                Pair(progress, true) // (progress, isDay)
            } else {
                // Nighttime progress
                val totalNight = (1440 - sunsetMin) + sunriseMin
                val progress = if (currentMinutes >= sunsetMin) {
                    (currentMinutes - sunsetMin).toFloat() / totalNight.toFloat()
                } else {
                    ((1440 - sunsetMin) + currentMinutes).toFloat() / totalNight.toFloat()
                }
                Pair(progress, false) // (progress, isDay)
            }
        } catch (e: Exception) {
            Pair(0.5f, true)
        }
    }

    val animatedProgress by animateFloatAsState(
        targetValue = state.first,
        animationSpec = tween(1500, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "SunProgress"
    )
    val isDay = state.second

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
            
            // Base dotted arc path
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

            // Dynamic progress arc path
            drawArc(
                color = if (isDay) Color(0xFFFFD54F) else Color(0xFF81D4FA),
                startAngle = 180f,
                sweepAngle = 180f * animatedProgress,
                useCenter = false,
                style = Stroke(
                    width = 2.5.dp.toPx()
                ),
                topLeft = Offset(arcLeft, arcTop),
                size = Size(arcWidth, arcHeight)
            )

            // Horizon separator line
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

            if (isDay) {
                // Glowing Sun
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
            } else {
                // Glowing crescent Moon
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF81D4FA).copy(alpha = 0.35f), Color.Transparent),
                        center = Offset(sunX.toFloat(), sunY.toFloat()),
                        radius = 18.dp.toPx()
                    ),
                    radius = 18.dp.toPx(),
                    center = Offset(sunX.toFloat(), sunY.toFloat())
                )

                drawCircle(
                    color = Color(0xFFE3F2FD),
                    radius = 6.dp.toPx(),
                    center = Offset(sunX.toFloat(), sunY.toFloat())
                )

                drawCircle(
                    color = if (isDark) Color(0xFF1E254C) else Color(0xFFE2E8F0),
                    radius = 5.dp.toPx(),
                    center = Offset(sunX.toFloat() - 3.dp.toPx(), sunY.toFloat() - 2.dp.toPx())
                )
            }
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

@Composable
fun Modifier.shimmerEffect(): Modifier {
    val transition = rememberInfiniteTransition(label = "ShimmerTransition")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ShimmerTranslate"
    )

    val isDark = true
    val shimmerColors = if (isDark) {
        listOf(
            Color(0x1F1E254C),
            Color(0x501E254C),
            Color(0x1F1E254C)
        )
    } else {
        listOf(
            Color(0x0F000000),
            Color(0x2B000000),
            Color(0x0F000000)
        )
    }

    return this.background(
        brush = Brush.linearGradient(
            colors = shimmerColors,
            start = Offset(translateAnim - 400f, translateAnim - 400f),
            end = Offset(translateAnim + 400f, translateAnim + 400f)
        )
    )
}

@Composable
fun HomeScreenSkeleton() {
    val isDark = true
    
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("home_skeleton_loader"),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Header skeleton
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Box(
                        modifier = Modifier
                            .width(180.dp)
                            .height(28.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .shimmerEffect()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .width(80.dp)
                            .height(16.dp)
                            .clip(MaterialTheme.shapes.small)
                            .shimmerEffect()
                    )
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .shimmerEffect()
                )
            }
        }

        // Summary skeleton
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(130.dp)
                        .clip(CircleShape)
                        .shimmerEffect()
                )
                Spacer(modifier = Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .width(140.dp)
                        .height(24.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .shimmerEffect()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .width(220.dp)
                        .height(16.dp)
                        .clip(MaterialTheme.shapes.small)
                        .shimmerEffect()
                )
            }
        }

        // Smart Insights skeleton
        item {
            SkySphereCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .width(150.dp)
                        .height(16.dp)
                        .clip(MaterialTheme.shapes.small)
                        .shimmerEffect()
                )
                Spacer(modifier = Modifier.height(16.dp))
                repeat(2) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .shimmerEffect()
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .width(100.dp)
                                    .height(14.dp)
                                    .clip(MaterialTheme.shapes.small)
                                    .shimmerEffect()
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(12.dp)
                                    .clip(MaterialTheme.shapes.small)
                                    .shimmerEffect()
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }

        // Hourly Forecast skeleton
        item {
            Column {
                Box(
                    modifier = Modifier
                        .width(120.dp)
                        .height(16.dp)
                        .clip(MaterialTheme.shapes.small)
                        .shimmerEffect()
                )
                Spacer(modifier = Modifier.height(12.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(5) {
                        Box(
                            modifier = Modifier
                                .width(82.dp)
                                .height(110.dp)
                                .clip(MaterialTheme.shapes.medium)
                                .border(
                                    1.dp,
                                    Color(0xFF374151), // High-contrast border for high accessibility (as requested)
                                    MaterialTheme.shapes.medium
                                )
                                .shimmerEffect()
                        )
                    }
                }
            }
        }

        // 7-day outlook skeleton
        item {
            Column {
                Box(
                    modifier = Modifier
                        .width(120.dp)
                        .height(16.dp)
                        .clip(MaterialTheme.shapes.small)
                        .shimmerEffect()
                )
                Spacer(modifier = Modifier.height(12.dp))
                SkySphereCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    repeat(3) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(60.dp)
                                    .height(16.dp)
                                    .clip(MaterialTheme.shapes.small)
                                    .shimmerEffect()
                            )
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .shimmerEffect()
                            )
                            Box(
                                modifier = Modifier
                                    .width(80.dp)
                                    .height(16.dp)
                                    .clip(MaterialTheme.shapes.small)
                                    .shimmerEffect()
                            )
                        }
                    }
                }
            }
        }
    }
}
