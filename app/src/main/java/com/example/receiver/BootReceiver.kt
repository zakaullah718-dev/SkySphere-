package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.worker.WeatherNotificationManager
import com.example.worker.WeatherWorkerScheduler

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            Log.d("BootReceiver", "System broadcast received ($action). Re-enforcing periodic weather notification worker.")
            WeatherNotificationManager.createNotificationChannel(context)
            WeatherWorkerScheduler.schedulePeriodicWeatherUpdates(context, intervalHours = 6)
        }
    }
}
