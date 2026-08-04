package com.example.ui.screens.map

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.IMyLocationConsumer

class SafeGpsMyLocationProvider(private val context: Context) : GpsMyLocationProvider(context), LocationListener {

    private var consumer: IMyLocationConsumer? = null
    private var isProviderRunning = false
    private val registeredProviders = HashSet<String>()

    override fun startLocationProvider(myLocationConsumer: IMyLocationConsumer?): Boolean {
        this.consumer = myLocationConsumer

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

        if (isProviderRunning && registeredProviders.isNotEmpty()) {
            return true
        }

        registeredProviders.clear()

        val networkEnabled = try { locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) } catch (t: Throwable) { false }
        val gpsEnabled = hasFine && try { locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) } catch (t: Throwable) { false }
        val passiveEnabled = try { locationManager.isProviderEnabled(LocationManager.PASSIVE_PROVIDER) } catch (t: Throwable) { false }

        val providersToTry = mutableListOf<String>()
        if (networkEnabled) providersToTry.add(LocationManager.NETWORK_PROVIDER)
        if (gpsEnabled) providersToTry.add(LocationManager.GPS_PROVIDER)
        if (passiveEnabled && providersToTry.isEmpty()) providersToTry.add(LocationManager.PASSIVE_PROVIDER)

        var startedAny = false
        for (provider in providersToTry) {
            try {
                locationManager.requestLocationUpdates(
                    provider,
                    locationUpdateMinTime,
                    locationUpdateMinDistance,
                    this
                )
                registeredProviders.add(provider)
                startedAny = true
                break
            } catch (e: SecurityException) {
                Log.w("SafeLocationProvider", "SecurityException requesting updates for $provider: ${e.localizedMessage}")
            } catch (t: Throwable) {
                Log.w("SafeLocationProvider", "Error requesting updates for $provider: ${t.localizedMessage}")
            }
        }

        isProviderRunning = startedAny
        return startedAny
    }

    override fun stopLocationProvider() {
        this.consumer = null
        if (!isProviderRunning && registeredProviders.isEmpty()) {
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

    override fun onLocationChanged(location: Location) {
        try {
            consumer?.onLocationChanged(location, this)
        } catch (t: Throwable) {
            Log.w("SafeLocationProvider", "Error delivering location to consumer: ${t.localizedMessage}")
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}

    override fun getLastKnownLocation(): Location? {
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) return null

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        if (!LocationManagerCompat.isLocationEnabled(locationManager)) return null

        return try {
            val networkLocation = if (try { locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) } catch (t: Throwable) { false }) {
                try { locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) } catch (t: Throwable) { null }
            } else null

            val gpsLocation = if (hasFine && try { locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) } catch (t: Throwable) { false }) {
                try { locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER) } catch (t: Throwable) { null }
            } else null

            networkLocation ?: gpsLocation
        } catch (e: SecurityException) {
            null
        } catch (t: Throwable) {
            null
        }
    }
}


