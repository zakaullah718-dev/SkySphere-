package com.example.ui.screens.map

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.util.Log
import androidx.core.content.ContextCompat
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.IMyLocationConsumer

class SafeGpsMyLocationProvider(private val context: Context) : GpsMyLocationProvider(context) {

    override fun startLocationProvider(myLocationConsumer: IMyLocationConsumer?): Boolean {
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (!hasFine && !hasCoarse) {
            Log.d("SafeLocationProvider", "Skipping location provider start: Location permissions not granted.")
            return false
        }

        clearLocationSources()
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

        if (hasFine && try { locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true } catch (e: Exception) { false }) {
            addLocationSource(LocationManager.GPS_PROVIDER)
        }
        if (try { locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true } catch (e: Exception) { false }) {
            addLocationSource(LocationManager.NETWORK_PROVIDER)
        }

        return try {
            super.startLocationProvider(myLocationConsumer)
        } catch (e: SecurityException) {
            Log.w("SafeLocationProvider", "SecurityException starting location provider: ${e.localizedMessage}")
            false
        } catch (e: Exception) {
            Log.w("SafeLocationProvider", "Error starting location provider: ${e.localizedMessage}")
            false
        }
    }

    override fun stopLocationProvider() {
        try {
            super.stopLocationProvider()
        } catch (e: SecurityException) {
            Log.w("SafeLocationProvider", "SecurityException stopping location provider: ${e.localizedMessage}")
        } catch (e: Exception) {
            Log.w("SafeLocationProvider", "Error stopping location provider: ${e.localizedMessage}")
        }
    }

    override fun getLastKnownLocation(): Location? {
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) return null

        return try {
            super.getLastKnownLocation()
        } catch (e: SecurityException) {
            null
        } catch (e: Exception) {
            null
        }
    }
}
