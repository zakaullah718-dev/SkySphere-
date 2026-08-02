package com.example.ui.screens.map

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.IMyLocationConsumer

class SafeGpsMyLocationProvider(private val context: Context) : GpsMyLocationProvider(context) {

    private var isProviderRunning = false
    private val registeredProviders = HashSet<String>()

    override fun startLocationProvider(myLocationConsumer: IMyLocationConsumer?): Boolean {
        if (isProviderRunning && registeredProviders.isNotEmpty()) {
            return true
        }

        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (!hasFine && !hasCoarse) {
            Log.d("SafeLocationProvider", "Skipping location provider start: Location permissions not granted.")
            return false
        }

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (locationManager == null || !LocationManagerCompat.isLocationEnabled(locationManager)) {
            Log.d("SafeLocationProvider", "Location services disabled on device.")
            return false
        }

        registeredProviders.clear()

        val networkEnabled = try { locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) } catch (t: Throwable) { false }
        val gpsEnabled = hasFine && try { locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) } catch (t: Throwable) { false }

        if (!networkEnabled && !gpsEnabled) {
            Log.d("SafeLocationProvider", "No active location providers enabled.")
            return false
        }

        val providerToUse = when {
            networkEnabled -> LocationManager.NETWORK_PROVIDER
            gpsEnabled -> LocationManager.GPS_PROVIDER
            else -> null
        }

        var startedAny = false

        if (providerToUse != null) {
            try {
                locationManager.requestLocationUpdates(
                    providerToUse,
                    locationUpdateMinTime,
                    locationUpdateMinDistance,
                    this
                )
                registeredProviders.add(providerToUse)
                startedAny = true
            } catch (e: SecurityException) {
                Log.w("SafeLocationProvider", "SecurityException requesting updates for $providerToUse: ${e.localizedMessage}")
            } catch (t: Throwable) {
                Log.w("SafeLocationProvider", "Error requesting updates for $providerToUse: ${t.localizedMessage}")
            }
        }

        isProviderRunning = startedAny
        return startedAny
    }

    override fun stopLocationProvider() {
        if (!isProviderRunning || registeredProviders.isEmpty()) {
            isProviderRunning = false
            registeredProviders.clear()
            return
        }

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (locationManager != null) {
            try {
                locationManager.removeUpdates(this)
            } catch (e: SecurityException) {
                Log.w("SafeLocationProvider", "SecurityException stopping location provider: ${e.localizedMessage}")
            } catch (t: Throwable) {
                Log.w("SafeLocationProvider", "Error stopping location provider: ${t.localizedMessage}")
            }
        }

        isProviderRunning = false
        registeredProviders.clear()
    }

    override fun getLastKnownLocation(): Location? {
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) return null

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        if (!LocationManagerCompat.isLocationEnabled(locationManager)) return null

        return try {
            val gpsLocation = if (hasFine && try { locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) } catch (t: Throwable) { false }) {
                try { locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER) } catch (t: Throwable) { null }
            } else null

            val networkLocation = if (try { locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) } catch (t: Throwable) { false }) {
                try { locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) } catch (t: Throwable) { null }
            } else null

            gpsLocation ?: networkLocation
        } catch (e: SecurityException) {
            null
        } catch (t: Throwable) {
            null
        }
    }
}


