package com.example.ui.screens.map

import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.util.Log
import com.example.data.repository.WeatherRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

class MapRepository(
    private val weatherRepository: WeatherRepository
) {
    fun getDefaultZoom(): Double = 2.5
    fun getDefaultCenter(): Pair<Double, Double> = Pair(20.0, 0.0)

    @Suppress("DEPRECATION")
    suspend fun reverseGeocode(context: Context, lat: Double, lon: Double): String? = withContext(Dispatchers.IO) {
        try {
            val geocoder = Geocoder(context, Locale.getDefault())
            val addresses = geocoder.getFromLocation(lat, lon, 1)
            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                val city = address.locality ?: address.subAdminArea ?: address.adminArea
                val country = address.countryName
                when {
                    city != null && country != null -> "$city, $country"
                    city != null -> city
                    country != null -> country
                    else -> address.featureName
                }
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("MapRepository", "Reverse geocoding error: ${e.localizedMessage}")
            null
        }
    }

    fun getLastKnownLocation(context: Context): Location? {
        return try {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
            val hasFine = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
            val hasCoarse = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!hasFine && !hasCoarse) return null

            val gpsLocation = if (hasFine && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                try { locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER) } catch (e: Exception) { null }
            } else null

            val networkLocation = if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                try { locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) } catch (e: Exception) { null }
            } else null

            val passiveLocation = if (hasFine && locationManager.isProviderEnabled(LocationManager.PASSIVE_PROVIDER)) {
                try { locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER) } catch (e: Exception) { null }
            } else null

            gpsLocation ?: networkLocation ?: passiveLocation
        } catch (e: Exception) {
            Log.e("MapRepository", "Error fetching last known location: ${e.localizedMessage}")
            null
        }
    }
}
