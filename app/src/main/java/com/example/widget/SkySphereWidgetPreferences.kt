package com.example.widget

import android.content.Context

object SkySphereWidgetPreferences {
    private const val PREFS_NAME = "skysphere_widget_prefs"
    private const val KEY_MODE_PREFIX = "widget_mode_"
    private const val KEY_CITY_PREFIX = "widget_city_"

    const val MODE_CURRENT_LOCATION = "CURRENT_LOCATION"
    const val MODE_FIXED_CITY = "FIXED_CITY"

    fun saveWidgetConfig(context: Context, appWidgetId: Int, mode: String, cityName: String? = null) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_MODE_PREFIX + appWidgetId, mode)
            .putString(KEY_CITY_PREFIX + appWidgetId, cityName ?: "")
            .apply()
    }

    fun getWidgetMode(context: Context, appWidgetId: Int): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_MODE_PREFIX + appWidgetId, MODE_CURRENT_LOCATION) ?: MODE_CURRENT_LOCATION
    }

    fun getWidgetCity(context: Context, appWidgetId: Int): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val city = prefs.getString(KEY_CITY_PREFIX + appWidgetId, null)
        return if (city.isNullOrBlank()) null else city
    }

    fun deleteWidgetConfig(context: Context, appWidgetId: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .remove(KEY_MODE_PREFIX + appWidgetId)
            .remove(KEY_CITY_PREFIX + appWidgetId)
            .apply()
    }
}
