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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.BeachAccess
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.WaterDrop
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
    val weatherLayerManager = remember { FutureWeatherLayerManager() }
    val mapState by controller.mapState.collectAsState()

    var showLayerSelectorSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // References for overlays
    val locationOverlayRef = remember { arrayOfNulls<MyLocationNewOverlay>(1) }
    val weatherOverlayRef = remember { arrayOfNulls<TilesOverlay>(1) }

    // Helper to trigger center & reverse geocode
    fun centerOnLocation(mapView: MapView?, lat: Double, lon: Double, zoomLevel: Double = 11.0) {
        val geoPoint = GeoPoint(lat, lon)
        mapView?.controller?.setZoom(zoomLevel)
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
        if (myLocation != null) {
            centerOnLocation(mapView, myLocation.latitude, myLocation.longitude, 11.0)
        } else {
            val lastLocation = mapRepository.getLastKnownLocation(context)
            if (lastLocation != null) {
                centerOnLocation(mapView, lastLocation.latitude, lastLocation.longitude, 11.0)
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

                val defaultCenter = mapRepository.getDefaultCenter()
                val defaultZoom = mapRepository.getDefaultZoom()

                this.controller.setZoom(defaultZoom)
                this.controller.setCenter(GeoPoint(defaultCenter.first, defaultCenter.second))

                val hasLocationPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

                val myLocationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(context), this).apply {
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
    LaunchedEffect(mapState.selectedLayer, mapState.radarTimestamp, mapView) {
        if (mapView == null) return@LaunchedEffect

        if (mapState.selectedLayer != MapWeatherLayer.NONE) {
            val minZ = mapState.selectedLayer.minZoom
            val maxZ = mapState.selectedLayer.maxZoom
            val currentZoom = mapView.zoomLevelDouble
            if (currentZoom < minZ || currentZoom > maxZ) {
                val targetZoom = currentZoom.coerceIn(minZ, maxZ)
                mapView.controller.animateTo(mapView.mapCenter, targetZoom, 500L)
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(
                        message = "Zoom adjusted to optimal range (${minZ.toInt()}-${maxZ.toInt()}) for ${mapState.selectedLayer.displayName}",
                        duration = SnackbarDuration.Short
                    )
                }
            }

            weatherLayerManager.fetchLatestWeatherMapPaths()
            if (mapState.selectedLayer == MapWeatherLayer.RAIN_RADAR && mapState.radarTimestamp == null) {
                val latestTs = weatherLayerManager.fetchLatestRadarTimestamp()
                if (latestTs != null) {
                    controller.setRadarTimestamp(latestTs)
                }
            }
        }

        // Remove previous weather layer overlay
        weatherOverlayRef[0]?.let { oldOverlay ->
            mapView.overlays.remove(oldOverlay)
            try {
                oldOverlay.onDetach(mapView)
            } catch (e: Exception) {
                Log.w("WeatherRadar", "Error detaching old weather overlay: ${e.localizedMessage}")
            }
            weatherOverlayRef[0] = null
            Log.d("WeatherRadar", "Overlay removed")
        }

        // Attach new weather layer overlay if enabled
        if (mapState.selectedLayer != MapWeatherLayer.NONE) {
            val newOverlay = weatherLayerManager.createTilesOverlay(
                context = context,
                layer = mapState.selectedLayer,
                radarTimestamp = mapState.radarTimestamp
            )
            if (newOverlay != null) {
                Log.d("WeatherRadar", "Overlay created for ${mapState.selectedLayer.displayName}")
                // Insert weather overlay underneath the location marker overlay
                val insertIndex = if (mapView.overlays.isNotEmpty()) mapView.overlays.size - 1 else 0
                mapView.overlays.add(insertIndex, newOverlay)
                weatherOverlayRef[0] = newOverlay
                Log.d("WeatherRadar", "Overlay attached for ${mapState.selectedLayer.displayName}")
            }
        }

        mapView.invalidate()
    }

    // Lifecycle & battery optimization
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    mapView?.onResume()
                    if (mapState.isLocationPermissionGranted) {
                        locationOverlayRef[0]?.enableMyLocation()
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

                // Active Weather Layer Indicator Pill with Quick Close Button
                if (mapState.selectedLayer != MapWeatherLayer.NONE) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 16.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
                            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
                            .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = getLayerIcon(mapState.selectedLayer),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Live ${mapState.selectedLayer.displayName}",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = { controller.setWeatherLayer(MapWeatherLayer.NONE) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Hide weather layer",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                // Control Buttons Column (Right Side - Placed safely at bottom = 104.dp above floating bottom bar)
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .navigationBarsPadding()
                        .padding(bottom = 104.dp, end = 20.dp),
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
                            imageVector = Icons.Filled.Layers,
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
                                imageVector = Icons.Filled.MyLocation,
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
                            imageVector = Icons.Filled.Layers,
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
                        Icon(imageVector = Icons.Filled.Close, contentDescription = "Close sheet")
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
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    controller.setWeatherLayer(layer)
                                    showLayerSelectorSheet = false
                                    coroutineScope.launch {
                                        if (layer != MapWeatherLayer.NONE) {
                                            snackbarHostState.showSnackbar("Enabled ${layer.displayName} overlay")
                                        } else {
                                            snackbarHostState.showSnackbar("Weather overlays hidden")
                                        }
                                    }
                                }
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
                                        imageVector = Icons.Filled.Check,
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
        MapWeatherLayer.NONE -> Icons.Filled.Map
        MapWeatherLayer.RAIN_RADAR -> Icons.Filled.BeachAccess
        MapWeatherLayer.CLOUDS -> Icons.Filled.Cloud
        MapWeatherLayer.TEMPERATURE -> Icons.Filled.Thermostat
        MapWeatherLayer.WIND -> Icons.Filled.Air
        MapWeatherLayer.PRESSURE -> Icons.Filled.Compress
        MapWeatherLayer.HUMIDITY -> Icons.Filled.WaterDrop
    }
}
