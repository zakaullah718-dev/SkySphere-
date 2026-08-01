package com.example.ui.screens.map

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.asImageBitmap
import coil.compose.AsyncImage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.ui.icons.SkySphereIcons
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.data.repository.WeatherRepository
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.TilesOverlay
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    repository: WeatherRepository,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val mapRepository = remember { MapRepository(repository) }
    val controller = remember { MapController() }
    val radarRepository = remember { FutureRadarRepository(context) }
    val weatherLayerManager = remember { FutureWeatherLayerManager(radarRepository) }
    val mapState by controller.mapState.collectAsState()

    var showLayerSelectorSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Time-Lapse Radar Controller & State
    val timeLapseController = remember { RadarTimeLapseController(radarRepository) }
    val timeLapseState by timeLapseController.state.collectAsState()

    // Initialize or pause time-lapse whenever active weather layer changes
    LaunchedEffect(mapState.selectedLayer) {
        if (mapState.selectedLayer != MapWeatherLayer.NONE) {
            val centerLat = mapState.userLatitude ?: 37.7749
            val centerLon = mapState.userLongitude ?: -122.4194
            timeLapseController.initializeForLayer(mapState.selectedLayer, centerLat, centerLon, 5)
        } else {
            timeLapseController.pause()
        }
    }

    LaunchedEffect(mapState.userLatitude, mapState.userLongitude) {
        val lat = mapState.userLatitude
        val lon = mapState.userLongitude
        if (lat != null && lon != null) {
            timeLapseController.onLocationChanged(lat, lon)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            timeLapseController.destroy()
        }
    }

    // References for overlays
    val locationOverlayRef = remember { arrayOfNulls<MyLocationNewOverlay>(1) }
    val weatherOverlayRef = remember { arrayOfNulls<WeatherTilesOverlay>(1) }

    // Helper to trigger center & reverse geocode
    fun centerOnLocation(mapView: MapView?, lat: Double, lon: Double, zoomLevel: Double = 11.0) {
        val geoPoint = GeoPoint(lat, lon)
        val maxRadarZoom = FutureWeatherLayerManager.RAIN_RADAR_PROVIDER_MAX_ZOOM.toDouble()
        val targetZoom = if (mapState.selectedLayer == MapWeatherLayer.RAIN_RADAR) {
            minOf(zoomLevel, maxRadarZoom)
        } else {
            zoomLevel
        }
        mapView?.controller?.setZoom(targetZoom)
        mapView?.controller?.animateTo(geoPoint)
        controller.updateUserLocation(lat, lon, mapState.locationName)

        coroutineScope.launch {
            controller.setLocating(true)
            val name = mapRepository.reverseGeocode(context, lat, lon)
            if (!name.isNullOrBlank()) {
                controller.setLocationName(name)
            }
            controller.setLocating(false)
        }
    }

    // Function to acquire best location
    fun moveToCurrentLocation(mapView: MapView?) {
        val overlay = locationOverlayRef[0]
        val myLocation = overlay?.myLocation
        val currentZoom = mapView?.zoomLevelDouble ?: 11.0
        val maxRadarZoom = FutureWeatherLayerManager.RAIN_RADAR_PROVIDER_MAX_ZOOM.toDouble()
        val targetZoom = if (mapState.selectedLayer == MapWeatherLayer.RAIN_RADAR) {
            maxRadarZoom
        } else {
            if (currentZoom < 8.0) 11.0 else currentZoom
        }

        if (myLocation != null) {
            centerOnLocation(mapView, myLocation.latitude, myLocation.longitude, targetZoom)
        } else {
            val lastLocation = mapRepository.getLastKnownLocation(context)
            if (lastLocation != null) {
                centerOnLocation(mapView, lastLocation.latitude, lastLocation.longitude, targetZoom)
            } else {
                Toast.makeText(context, "Acquiring GPS location fix...", Toast.LENGTH_SHORT).show()
                controller.setLocating(true)
            }
        }
    }

    // Permission Launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

        if (fineGranted || coarseGranted) {
            controller.setLocationPermissionGranted(true)
            try {
                locationOverlayRef[0]?.enableMyLocation()
            } catch (e: Exception) {
                Log.w("MapEngine", "Error enabling my location overlay: ${e.localizedMessage}")
            }
            locationOverlayRef[0]?.runOnFirstFix {
                val loc = locationOverlayRef[0]?.myLocation
                if (loc != null) {
                    coroutineScope.launch {
                        centerOnLocation(null, loc.latitude, loc.longitude, 11.0)
                    }
                }
            }
            val lastLoc = mapRepository.getLastKnownLocation(context)
            if (lastLoc != null) {
                controller.updateUserLocation(lastLoc.latitude, lastLoc.longitude)
                coroutineScope.launch {
                    val name = mapRepository.reverseGeocode(context, lastLoc.latitude, lastLoc.longitude)
                    if (!name.isNullOrBlank()) {
                        controller.setLocationName(name)
                    }
                }
            }
        } else {
            controller.setLocationPermissionGranted(false)
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Location permission denied. Showing default view.")
            }
        }
    }

    // Check permissions on composition
    LaunchedEffect(Unit) {
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (hasFine || hasCoarse) {
            controller.setLocationPermissionGranted(true)
            val lastLoc = mapRepository.getLastKnownLocation(context)
            if (lastLoc != null) {
                controller.updateUserLocation(lastLoc.latitude, lastLoc.longitude)
                launch {
                    val name = mapRepository.reverseGeocode(context, lastLoc.latitude, lastLoc.longitude)
                    if (!name.isNullOrBlank()) {
                        controller.setLocationName(name)
                    }
                }
            }
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    val mapView = remember {
        try {
            Configuration.getInstance().userAgentValue = context.packageName
            controller.onMapInitialized()

            MapView(context).apply mapApply@{
                setTileSource(TileSourceFactory.MAPNIK)
                controller.onMapStyleLoaded()

                setMultiTouchControls(true)
                setBuiltInZoomControls(false)
                minZoomLevel = 2.0
                maxZoomLevel = 20.0

                addMapListener(object : org.osmdroid.events.MapListener {
                    override fun onScroll(event: org.osmdroid.events.ScrollEvent?): Boolean = false
                    override fun onZoom(event: org.osmdroid.events.ZoomEvent?): Boolean {
                        postInvalidate()
                        return false
                    }
                })

                val defaultCenter = mapRepository.getDefaultCenter()
                val defaultZoom = mapRepository.getDefaultZoom()

                this.controller.setZoom(defaultZoom)
                this.controller.setCenter(GeoPoint(defaultCenter.first, defaultCenter.second))

                val hasLocationPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

                val locationProvider = SafeGpsMyLocationProvider(context)
                val myLocationOverlay = MyLocationNewOverlay(locationProvider, this).apply {
                    if (hasLocationPermission) {
                        try {
                            enableMyLocation()
                        } catch (e: Exception) {
                            Log.w("MapEngine", "Could not enable location overlay: ${e.localizedMessage}")
                        }
                    }
                    runOnFirstFix {
                        val loc = myLocation
                        if (loc != null) {
                            coroutineScope.launch {
                                centerOnLocation(this@mapApply, loc.latitude, loc.longitude, 11.0)
                            }
                        }
                    }
                }
                locationOverlayRef[0] = myLocationOverlay
                overlays.add(myLocationOverlay)

                controller.onMapReady()
            }
        } catch (e: Exception) {
            Log.e("MapEngine", "Error initializing native map engine: ${e.localizedMessage}", e)
            controller.onMapError(e)
            null
        }
    }

    // Dynamic Weather Overlay Manager
    val activeFrame = timeLapseState.currentFrame

    LaunchedEffect(mapState.selectedLayer, mapView) {
        if (mapView == null) return@LaunchedEffect

        // Remove previous weather layer overlay
        weatherOverlayRef[0]?.let { oldOverlay ->
            mapView.overlays.remove(oldOverlay)
            try {
                oldOverlay.onDetach(mapView)
            } catch (e: Exception) {
                Log.w("WeatherRadar", "Error detaching old weather overlay: ${e.localizedMessage}")
            }
            weatherOverlayRef[0] = null
        }

        // Attach new weather layer overlay if enabled
        if (mapState.selectedLayer != MapWeatherLayer.NONE) {
            val newOverlay = weatherLayerManager.createTilesOverlay(
                context = context,
                layer = mapState.selectedLayer,
                radarTimestamp = activeFrame?.timestamp ?: radarRepository.getFallbackTimestamp(),
                customRadarFrame = activeFrame?.radarFrame
            )
            if (newOverlay != null) {
                // Insert weather overlay underneath the location marker overlay
                val insertIndex = if (mapView.overlays.isNotEmpty()) mapView.overlays.size - 1 else 0
                mapView.overlays.add(insertIndex, newOverlay)
                weatherOverlayRef[0] = newOverlay
            }
        }

        mapView.postInvalidate()
    }

    LaunchedEffect(activeFrame) {
        val overlay = weatherOverlayRef[0]
        if (overlay != null && activeFrame != null) {
            overlay.updateFrame(activeFrame, mapView)
        }
    }

    LaunchedEffect(timeLapseState.isPlaying) {
        weatherOverlayRef[0]?.setPlaybackActive(timeLapseState.isPlaying)
    }

    // Lifecycle & battery optimization
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    mapView?.onResume()
                    if (mapState.isLocationPermissionGranted) {
                        try {
                            locationOverlayRef[0]?.enableMyLocation()
                        } catch (e: Exception) {
                            Log.w("MapEngine", "Could not enable location overlay on resume: ${e.localizedMessage}")
                        }
                    }
                }
                Lifecycle.Event.ON_PAUSE -> {
                    mapView?.onPause()
                    locationOverlayRef[0]?.disableMyLocation()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            locationOverlayRef[0]?.disableMyLocation()
            mapView?.onDetach()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Map",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
                        if (!mapState.locationName.isNullOrBlank()) {
                            Text(
                                text = mapState.locationName!!,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.fillMaxWidth().testTag("map_top_bar")
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .testTag("interactive_world_map_container")
        ) {
            if (mapView != null) {
                AndroidView(
                    factory = { mapView },
                    modifier = Modifier.fillMaxSize().testTag("native_world_map_view")
                )

                // Active Weather Layer Indicator & Production Radar Legend Card
                if (mapState.selectedLayer != MapWeatherLayer.NONE) {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 16.dp, start = 16.dp, end = 16.dp)
                            .fillMaxWidth(0.90f)
                            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
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
                                        imageVector = getLayerIcon(mapState.selectedLayer),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "Live ${mapState.selectedLayer.displayName}",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    )
                                }

                                IconButton(
                                    onClick = { controller.setWeatherLayer(MapWeatherLayer.NONE) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = SkySphereIcons.Close,
                                        contentDescription = "Hide weather layer",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            // Layer Legend Bar for active weather overlay
                            Spacer(modifier = Modifier.height(8.dp))
                            LayerLegendBar(layer = mapState.selectedLayer)
                        }
                    }
                }

                // Floating Glassmorphism Radar Time-Lapse Control Panel
                if (mapState.selectedLayer != MapWeatherLayer.NONE) {
                    RadarTimeLapsePanel(
                        state = timeLapseState,
                        onTogglePlayPause = {
                            timeLapseController.togglePlayPause { frame ->
                                controller.setRadarTimestamp(frame.timestamp)
                            }
                        },
                        onPreviousFrame = {
                            timeLapseController.previousFrame { frame ->
                                controller.setRadarTimestamp(frame.timestamp)
                            }
                        },
                        onNextFrame = {
                            timeLapseController.nextFrame { frame ->
                                controller.setRadarTimestamp(frame.timestamp)
                            }
                        },
                        onSeekToFrame = { index ->
                            timeLapseController.seekToFrame(index) { frame ->
                                controller.setRadarTimestamp(frame.timestamp)
                            }
                        },
                        onCycleSpeed = {
                            timeLapseController.cycleSpeed()
                        },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(bottom = 76.dp)
                    )
                }

                // Control Buttons Column (Right Side)
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .navigationBarsPadding()
                        .padding(
                            bottom = if (mapState.selectedLayer != MapWeatherLayer.NONE) 220.dp else 92.dp,
                            end = 16.dp
                        ),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Weather Layers Floating Button
                    FloatingActionButton(
                        onClick = { showLayerSelectorSheet = true },
                        containerColor = if (mapState.selectedLayer != MapWeatherLayer.NONE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (mapState.selectedLayer != MapWeatherLayer.NONE) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        shape = CircleShape,
                        modifier = Modifier.testTag("layer_selector_fab")
                    ) {
                        Icon(
                            imageVector = SkySphereIcons.Map,
                            contentDescription = "Weather Layers"
                        )
                    }

                    // My Location Floating Button
                    FloatingActionButton(
                        onClick = {
                            val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                            val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

                            if (hasFine || hasCoarse) {
                                moveToCurrentLocation(mapView)
                            } else {
                                permissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        shape = CircleShape,
                        modifier = Modifier.testTag("my_location_fab")
                    ) {
                        if (mapState.isLocating) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = SkySphereIcons.MyLocation,
                                contentDescription = "My Location"
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = mapState.errorMessage ?: "Failed to load map engine.",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }

    // Weather Layer Selection Modal Bottom Sheet
    if (showLayerSelectorSheet) {
        ModalBottomSheet(
            onDismissRequest = { showLayerSelectorSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 36.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = SkySphereIcons.Map,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Live Weather Overlays",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                    IconButton(onClick = { showLayerSelectorSheet = false }) {
                        Icon(imageVector = SkySphereIcons.Close, contentDescription = "Close sheet")
                    }
                }

                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(MapWeatherLayer.values(), key = { layer -> layer.name }) { layer ->
                        val isSelected = mapState.selectedLayer == layer
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            ),
                            shape = RoundedCornerShape(16.dp),
                            onClick = {
                                controller.setWeatherLayer(layer)
                                showLayerSelectorSheet = false
                                coroutineScope.launch {
                                    if (layer != MapWeatherLayer.NONE) {
                                        snackbarHostState.showSnackbar("Enabled ${layer.displayName} overlay")
                                    } else {
                                        snackbarHostState.showSnackbar("Weather overlays hidden")
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = {
                                        controller.setWeatherLayer(layer)
                                        showLayerSelectorSheet = false
                                        coroutineScope.launch {
                                            if (layer != MapWeatherLayer.NONE) {
                                                snackbarHostState.showSnackbar("Enabled ${layer.displayName} overlay")
                                            } else {
                                                snackbarHostState.showSnackbar("Weather overlays hidden")
                                            }
                                        }
                                    },
                                    colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    imageVector = getLayerIcon(layer),
                                    contentDescription = null,
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = layer.displayName,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                        )
                                    )
                                    Text(
                                        text = layer.description,
                                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    )
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = SkySphereIcons.Check,
                                        contentDescription = "Active",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
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

private fun getLayerIcon(layer: MapWeatherLayer): ImageVector {
    return when (layer) {
        MapWeatherLayer.NONE -> SkySphereIcons.Map
        MapWeatherLayer.RAIN_RADAR -> SkySphereIcons.RainRadar
        MapWeatherLayer.CLOUDS -> SkySphereIcons.Cloud
        MapWeatherLayer.TEMPERATURE -> SkySphereIcons.Thermostat
        MapWeatherLayer.WIND -> SkySphereIcons.Wind
        MapWeatherLayer.HUMIDITY -> SkySphereIcons.Humidity
        MapWeatherLayer.PRESSURE -> SkySphereIcons.Pressure
    }
}

@Composable
private fun LayerLegendBar(layer: MapWeatherLayer) {
    val (colors, labels) = when (layer) {
        MapWeatherLayer.RAIN_RADAR -> Pair(
            listOf(Color(0xFF6D6DCD), Color(0xFF00ECEC), Color(0xFF00A000), Color(0xFFF8E000), Color(0xFFF88000), Color(0xFFE00000), Color(0xFFB705EF)),
            listOf("Trace", "Light", "Moderate", "Heavy", "Severe")
        )
        MapWeatherLayer.TEMPERATURE -> Pair(
            listOf(Color(0xFF1A237E), Color(0xFF0288D1), Color(0xFF4CAF50), Color(0xFFFFB300), Color(0xFFD32F2F)),
            listOf("Very Cold", "Cold", "Mild", "Warm", "Hot")
        )
        MapWeatherLayer.WIND -> Pair(
            listOf(Color(0xFFE0F7FA), Color(0xFF4DD0E1), Color(0xFF1976D2), Color(0xFF7B1FA2), Color(0xFF4A148C)),
            listOf("Calm", "Breeze", "Moderate", "Strong", "Storm")
        )
        MapWeatherLayer.HUMIDITY -> Pair(
            listOf(Color(0xFFFFF9C4), Color(0xFF81D4FA), Color(0xFF29B6F6), Color(0xFF0288D1), Color(0xFF01579B)),
            listOf("Dry (0%)", "Low", "Moderate", "High", "Saturated")
        )
        MapWeatherLayer.PRESSURE -> Pair(
            listOf(Color(0xFF512DA8), Color(0xFF0288D1), Color(0xFF009688), Color(0xFFF57C00)),
            listOf("Low", "Normal", "High")
        )
        MapWeatherLayer.CLOUDS -> Pair(
            listOf(Color(0xFFE0E0E0), Color(0xFFBDBDBD), Color(0xFF9E9E9E), Color(0xFF616161), Color(0xFF37474F)),
            listOf("Clear", "Few", "Scattered", "Broken", "Overcast")
        )
        MapWeatherLayer.NONE -> return
    }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
        ) {
            colors.forEach { color ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .background(color)
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 3.dp)
        ) {
            labels.forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }
    }
}
