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
                    providerName.contains("4x4") -> R.layout.widget_4x4
                    else -> R.layout.widget_2x2
                }
                val sizeCategory = when {
                    providerName.contains("1x1") -> 1
                    providerName.contains("2x2") -> 2
                    providerName.contains("4x2") -> 3
                    providerName.contains("4x4") -> 4
                    else -> 2
                }

                val remoteViews = RemoteViews(context.packageName, layoutId)
                val bitmap = when (sizeCategory) {
                    1 -> SkySphereWidgetPainter.drawWidget1x1(context, activeCity, isCelsius)
                    2 -> SkySphereWidgetPainter.drawWidget2x2(context, activeCity, isCelsius)
                    3 -> SkySphereWidgetPainter.drawWidget4x2(context, activeCity, isCelsius)
                    4 -> SkySphereWidgetPainter.drawWidget4x4(context, activeCity, isCelsius)
                    else -> SkySphereWidgetPainter.drawWidget2x2(context, activeCity, isCelsius)
                }

                remoteViews.setImageViewBitmap(R.id.widget_image_canvas, bitmap)
                setupWidgetPendingIntents(context, remoteViews, sizeCategory, activeCity)
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

                // 4x4 Widgets
                val ids4x4 = appWidgetManager.getAppWidgetIds(ComponentName(context, SkySphereWidget4x4Provider::class.java))
                for (id in ids4x4) {
                    updateWidgetInstance(context, appWidgetManager, id, R.layout.widget_4x4, 4, repository, isCelsius, activeCity)
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
        sizeCategory: Int, // 1: 1x1, 2: 2x2, 3: 4x2, 4: 4x4
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
            4 -> SkySphereWidgetPainter.drawWidget4x4(context, finalCity, isCelsius)
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
        // General tap opens Home screen
        val openHomeIntent = createPendingIntent(context, "home", cityWeather.cityName, 100)
        views.setOnClickPendingIntent(R.id.widget_image_canvas, openHomeIntent)

        if (sizeCategory >= 3) {
            // Location tap opens Favorites / City search
            val openFavIntent = createPendingIntent(context, "favorites", cityWeather.cityName, 101)
            views.setOnClickPendingIntent(R.id.widget_click_location, openFavIntent)

            // Icon tap triggers manual refresh
            val refreshIntent = createRefreshPendingIntent(context, 102)
            views.setOnClickPendingIntent(R.id.widget_click_icon, refreshIntent)
        }

        if (sizeCategory == 4) {
            val openHourlyIntent = createPendingIntent(context, "details", cityWeather.cityName, 103)
            views.setOnClickPendingIntent(R.id.widget_click_hourly, openHourlyIntent)
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
