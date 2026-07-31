package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.data.models.CityWeather
import com.example.data.repository.WeatherRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object SkySphereWidgetManager {

    private const val TAG = "SkySphereWidgetManager"
    const val ACTION_REFRESH_WIDGET = "com.example.widget.ACTION_REFRESH_WIDGET"

    fun updateWidgetIdsSync(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        try {
            val repository = WeatherRepository.getInstance(context)
            val isCelsius = repository.isCelsius.value
            var activeCity = repository.selectedCity.value

            if (activeCity.cityName == "Loading..." || activeCity.cityName.isBlank()) {
                activeCity = repository.selectedCity.value
            }

            for (id in appWidgetIds) {
                val providerName = appWidgetManager.getAppWidgetInfo(id)?.provider?.className ?: ""
                val layoutId = when {
                    providerName.contains("1x1") -> R.layout.widget_1x1
                    providerName.contains("2x2") -> R.layout.widget_2x2
                    providerName.contains("4x2") -> R.layout.widget_4x2
                    else -> R.layout.widget_2x2
                }
                val sizeCategory = when {
                    providerName.contains("1x1") -> 1
                    providerName.contains("2x2") -> 2
                    providerName.contains("4x2") -> 3
                    else -> 2
                }

                val mode = SkySphereWidgetPreferences.getWidgetMode(context, id)
                val fixedCityName = SkySphereWidgetPreferences.getWidgetCity(context, id)
                val targetCity = if (mode == SkySphereWidgetPreferences.MODE_FIXED_CITY && !fixedCityName.isNullOrBlank()) {
                    repository.getCityByNameCached(fixedCityName) ?: activeCity
                } else {
                    activeCity
                }

                val remoteViews = RemoteViews(context.packageName, layoutId)
                val bitmap = when (sizeCategory) {
                    1 -> SkySphereWidgetPainter.drawWidget1x1(context, targetCity, isCelsius)
                    2 -> SkySphereWidgetPainter.drawWidget2x2(context, targetCity, isCelsius)
                    3 -> SkySphereWidgetPainter.drawWidget4x2(context, targetCity, isCelsius)
                    else -> SkySphereWidgetPainter.drawWidget2x2(context, targetCity, isCelsius)
                }

                remoteViews.setImageViewBitmap(R.id.widget_image_canvas, bitmap)
                setupWidgetPendingIntents(context, remoteViews, sizeCategory, targetCity)
                appWidgetManager.updateAppWidget(id, remoteViews)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in updateWidgetIdsSync", e)
        }
    }

    fun updateAllWidgets(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val appWidgetManager = AppWidgetManager.getInstance(context) ?: return@launch
                val repository = WeatherRepository.getInstance(context)
                val isCelsius = repository.isCelsius.value
                var activeCity = repository.selectedCity.value

                if (activeCity.cityName == "Loading..." || activeCity.cityName.isBlank()) {
                    // Try to restore from database or seed default
                    activeCity = repository.getOrFetchActiveCity()
                }

                // 1x1 Widgets
                val ids1x1 = appWidgetManager.getAppWidgetIds(ComponentName(context, SkySphereWidget1x1Provider::class.java))
                for (id in ids1x1) {
                    updateWidgetInstance(context, appWidgetManager, id, R.layout.widget_1x1, 1, repository, isCelsius, activeCity)
                }

                // 2x2 Widgets
                val ids2x2 = appWidgetManager.getAppWidgetIds(ComponentName(context, SkySphereWidget2x2Provider::class.java))
                for (id in ids2x2) {
                    updateWidgetInstance(context, appWidgetManager, id, R.layout.widget_2x2, 2, repository, isCelsius, activeCity)
                }

                // 4x2 Widgets
                val ids4x2 = appWidgetManager.getAppWidgetIds(ComponentName(context, SkySphereWidget4x2Provider::class.java))
                for (id in ids4x2) {
                    updateWidgetInstance(context, appWidgetManager, id, R.layout.widget_4x2, 3, repository, isCelsius, activeCity)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating all widgets", e)
            }
        }
    }

    private suspend fun updateWidgetInstance(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        layoutId: Int,
        sizeCategory: Int, // 1: 1x1, 2: 2x2, 3: 4x2
        repository: WeatherRepository,
        isCelsius: Boolean,
        activeCity: CityWeather
    ) = withContext(Dispatchers.IO) {
        val mode = SkySphereWidgetPreferences.getWidgetMode(context, appWidgetId)
        val fixedCityName = SkySphereWidgetPreferences.getWidgetCity(context, appWidgetId)

        val targetCity = if (mode == SkySphereWidgetPreferences.MODE_FIXED_CITY && !fixedCityName.isNullOrBlank()) {
            repository.getCityByNameFromFavoritesOrApi(fixedCityName) ?: activeCity
        } else {
            activeCity
        }

        val finalCity = if (targetCity.cityName == "Loading..." || targetCity.cityName.isBlank()) {
            repository.getOrFetchActiveCity()
        } else {
            targetCity
        }

        val remoteViews = RemoteViews(context.packageName, layoutId)

        // Draw Bitmap image for widget background & visual content
        val bitmap = when (sizeCategory) {
            1 -> SkySphereWidgetPainter.drawWidget1x1(context, finalCity, isCelsius)
            2 -> SkySphereWidgetPainter.drawWidget2x2(context, finalCity, isCelsius)
            3 -> SkySphereWidgetPainter.drawWidget4x2(context, finalCity, isCelsius)
            else -> SkySphereWidgetPainter.drawWidget2x2(context, finalCity, isCelsius)
        }

        remoteViews.setImageViewBitmap(R.id.widget_image_canvas, bitmap)

        // Set Deep Link PendingIntents
        setupWidgetPendingIntents(context, remoteViews, sizeCategory, finalCity)

        appWidgetManager.updateAppWidget(appWidgetId, remoteViews)
    }

    private fun setupWidgetPendingIntents(
        context: Context,
        views: RemoteViews,
        sizeCategory: Int,
        cityWeather: CityWeather
    ) {
        val cityName = cityWeather.cityName

        // Default background tap -> Open Home
        val openHomeIntent = createPendingIntent(context, "home", cityName, 100)
        views.setOnClickPendingIntent(R.id.widget_image_canvas, openHomeIntent)

        when (sizeCategory) {
            1 -> {
                // 1x1 Compact Badge - full image canvas tap handles home open
            }
            2, 3 -> {
                // 2x2 & 4x2 Dashboard - location, temp, icon, refresh
                views.setOnClickPendingIntent(R.id.widget_click_location, createPendingIntent(context, "search", cityName, 103))
                views.setOnClickPendingIntent(R.id.widget_click_temp, createPendingIntent(context, "home", cityName, 101))
                views.setOnClickPendingIntent(R.id.widget_click_icon, createPendingIntent(context, "radar", cityName, 102))
                views.setOnClickPendingIntent(R.id.widget_click_refresh, createRefreshPendingIntent(context, 104))
            }
        }
    }

    private fun createRefreshPendingIntent(context: Context, requestCode: Int): PendingIntent {
        val intent = Intent(context, SkySphereWidget1x1Provider::class.java).apply {
            action = ACTION_REFRESH_WIDGET
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createPendingIntent(
        context: Context,
        navigateTo: String,
        cityName: String,
        requestCode: Int
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra("navigate_to", navigateTo)
            putExtra("selected_city", cityName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
