package com.example

import android.app.Application
import com.example.widget.SkySphereWidgetManager
import com.example.worker.WeatherNotificationManager
import com.example.worker.WeatherWorkerScheduler

class SkySphereApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        WeatherNotificationManager.createNotificationChannel(this)
        WeatherWorkerScheduler.schedulePeriodicWeatherUpdates(this, intervalHours = 6)
        SkySphereWidgetManager.updateAllWidgets(this)
    }
}
