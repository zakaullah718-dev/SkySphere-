package com.example.ui.screens.map

data class MapState(
    val isInitialized: Boolean = false,
    val isStyleLoaded: Boolean = false,
    val isReady: Boolean = false,
    val centerLatitude: Double = 20.0,
    val centerLongitude: Double = 0.0,
    val zoomLevel: Double = 2.5,
    val userLatitude: Double? = null,
    val userLongitude: Double? = null,
    val locationName: String? = null,
    val isLocationPermissionGranted: Boolean = false,
    val isLocating: Boolean = false,
    val errorMessage: String? = null
)
