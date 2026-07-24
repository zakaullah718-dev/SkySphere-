package com.example.ui.screens.map

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.data.repository.WeatherRepository
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    repository: WeatherRepository,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val mapRepository = remember { MapRepository(repository) }
    val controller = remember { MapController() }
    val mapState by controller.mapState.collectAsState()

    val mapView = remember {
        try {
            // Configure user agent for OpenStreetMap tiles
            Configuration.getInstance().userAgentValue = context.packageName
            controller.onMapInitialized()

            MapView(context).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                controller.onMapStyleLoaded()

                setMultiTouchControls(true)
                setBuiltInZoomControls(false)

                val defaultCenter = mapRepository.getDefaultCenter()
                val defaultZoom = mapRepository.getDefaultZoom()

                this.controller.setZoom(defaultZoom)
                this.controller.setCenter(GeoPoint(defaultCenter.first, defaultCenter.second))

                controller.onMapReady()
            }
        } catch (e: Exception) {
            Log.e("MapEngine", "Error initializing native map engine: ${e.localizedMessage}", e)
            controller.onMapError(e)
            null
        }
    }

    // Manage MapView lifecycle to handle orientation and configuration changes smoothly
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView?.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView?.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView?.onDetach()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Map",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.fillMaxWidth().testTag("map_top_bar")
            )
        },
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
