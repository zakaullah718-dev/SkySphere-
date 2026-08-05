package com.example

import android.app.Application
import android.util.Log
import com.example.widget.SkySphereWidgetManager
import com.example.worker.WeatherNotificationManager
import com.example.worker.WeatherWorkerScheduler
import org.osmdroid.config.Configuration

class SkySphereApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Configuration.getInstance().userAgentValue = packageName
        Log.d("SkySphereApplication", "Application started with package: $packageName")
        WeatherNotificationManager.createNotificationChannel(this)
        WeatherWorkerScheduler.schedulePeriodicWeatherUpdates(this, intervalHours = 6)
        SkySphereWidgetManager.updateAllWidgets(this)
        com.example.ui.screens.map.RadarWarmUpEngine.start(this)
    }
}
