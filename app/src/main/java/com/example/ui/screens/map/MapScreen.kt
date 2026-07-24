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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    val mapState by controller.mapState.collectAsState()

    // Location Overlay Instance Reference
    val locationOverlayRef = remember { arrayOfNulls<MyLocationNewOverlay>(1) }

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
            // Fallback to Last Known Location
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
            locationOverlayRef[0]?.enableMyLocation()
            // Try centering on current location
            locationOverlayRef[0]?.runOnFirstFix {
                val loc = locationOverlayRef[0]?.myLocation
                if (loc != null) {
                    coroutineScope.launch {
                        centerOnLocation(null, loc.latitude, loc.longitude, 11.0)
                    }
                }
            }
            // Immediate check for last known location
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
                snackbarHostState.showSnackbar("Location permission denied. Showing world map.")
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

                // Configure User Location Marker Overlay
                val myLocationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(context), this).apply {
                    enableMyLocation()
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

    // Battery optimization & Lifecycle management
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

                // Floating "My Location" FAB
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
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .navigationBarsPadding()
                        .padding(bottom = 24.dp, end = 24.dp)
                        .testTag("my_location_fab")
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
            } else {
                Text(
                    text = mapState.errorMessage ?: "Failed to load map engine.",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}
