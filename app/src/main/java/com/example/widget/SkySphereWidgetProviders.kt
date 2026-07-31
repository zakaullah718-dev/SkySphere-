package com.example.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.util.Log

open class BaseSkySphereWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        Log.d("SkySphereWidgetProvider", "onUpdate called for widget ids: ${appWidgetIds.joinToString()}")
        SkySphereWidgetManager.updateWidgetIdsSync(context, appWidgetManager, appWidgetIds)
        SkySphereWidgetManager.updateAllWidgets(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        Log.d("SkySphereWidgetProvider", "onAppWidgetOptionsChanged for widget id: $appWidgetId")
        SkySphereWidgetManager.updateWidgetIdsSync(context, appWidgetManager, intArrayOf(appWidgetId))
        SkySphereWidgetManager.updateAllWidgets(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action
        Log.d("SkySphereWidgetProvider", "onReceive action: $action")
        if (action == SkySphereWidgetManager.ACTION_REFRESH_WIDGET ||
            action == AppWidgetManager.ACTION_APPWIDGET_UPDATE ||
            action == AppWidgetManager.ACTION_APPWIDGET_OPTIONS_CHANGED ||
            action == Intent.ACTION_BOOT_COMPLETED
        ) {
            SkySphereWidgetManager.updateAllWidgets(context)
            if (action == SkySphereWidgetManager.ACTION_REFRESH_WIDGET) {
                com.example.worker.WeatherWorkerScheduler.triggerImmediateWeatherUpdate(context)
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        for (id in appWidgetIds) {
            SkySphereWidgetPreferences.deleteWidgetConfig(context, id)
        }
    }
}

class SkySphereWidget1x1Provider : BaseSkySphereWidgetProvider()
class SkySphereWidget2x2Provider : BaseSkySphereWidgetProvider()
class SkySphereWidget4x2Provider : BaseSkySphereWidgetProvider()
class SkySphereWidget4x3Provider : BaseSkySphereWidgetProvider()
class SkySphereWidget4x4Provider : BaseSkySphereWidgetProvider()
