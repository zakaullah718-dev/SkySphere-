package com.example.ui.screens.map

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class MapController {

    private val _mapState = MutableStateFlow(MapState())
    val mapState: StateFlow<MapState> = _mapState.asStateFlow()

    fun onMapInitialized() {
        Log.d("MapEngine", "MAP ENGINE INITIALIZED")
        _mapState.update { it.copy(isInitialized = true) }
    }

    fun onMapStyleLoaded() {
        Log.d("MapEngine", "MAP STYLE LOADED")
        _mapState.update { it.copy(isStyleLoaded = true) }
    }

    fun onMapReady() {
        Log.d("MapEngine", "MAP READY")
        _mapState.update { it.copy(isReady = true) }
    }

    fun onMapError(exception: Throwable) {
        Log.e("MapEngine", "MAP ERROR: ${exception.localizedMessage}", exception)
        _mapState.update { it.copy(errorMessage = exception.localizedMessage) }
    }

    fun updateUserLocation(lat: Double, lon: Double, locationName: String? = null) {
        Log.d("MapEngine", "USER LOCATION UPDATED: ($lat, $lon) -> $locationName")
        _mapState.update {
            it.copy(
                userLatitude = lat,
                userLongitude = lon,
                locationName = locationName ?: it.locationName,
                isLocating = false
            )
        }
    }

    fun setLocationName(name: String?) {
        _mapState.update { it.copy(locationName = name) }
    }

    fun setLocationPermissionGranted(granted: Boolean) {
        _mapState.update { it.copy(isLocationPermissionGranted = granted) }
    }

    fun setLocating(isLocating: Boolean) {
        _mapState.update { it.copy(isLocating = isLocating) }
    }
}
