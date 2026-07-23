package com.example.worker

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object WeatherWorkerScheduler {

    const val PERIODIC_WORK_NAME = "SkySpherePeriodicWeatherWorker"
    const val IMMEDIATE_WORK_NAME = "SkySphereImmediateWeatherWorker"

    fun schedulePeriodicWeatherUpdates(context: Context, intervalHours: Long = 1) {
        try {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            // Minimum interval for periodic work in WorkManager is 15 minutes
            val repeatIntervalMinutes = Math.max(15L, intervalHours * 60L)

            val periodicWorkRequest = PeriodicWorkRequestBuilder<WeatherUpdateWorker>(
                repeatIntervalMinutes,
                TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                periodicWorkRequest
            )

            Log.d("WeatherWorkerScheduler", "Periodic weather update worker scheduled ($repeatIntervalMinutes mins interval)")
        } catch (e: Exception) {
            Log.e("WeatherWorkerScheduler", "Failed to schedule periodic weather worker", e)
        }
    }

    fun triggerImmediateWeatherUpdate(context: Context) {
        try {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val immediateWorkRequest = OneTimeWorkRequestBuilder<WeatherUpdateWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                IMMEDIATE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                immediateWorkRequest
            )

            Log.d("WeatherWorkerScheduler", "Immediate weather update worker enqueued")
        } catch (e: Exception) {
            Log.e("WeatherWorkerScheduler", "Failed to enqueue immediate weather worker", e)
        }
    }

    fun cancelPeriodicWeatherUpdates(context: Context) {
        try {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK_NAME)
            Log.d("WeatherWorkerScheduler", "Periodic weather update worker cancelled")
        } catch (e: Exception) {
            Log.e("WeatherWorkerScheduler", "Failed to cancel periodic weather worker", e)
        }
    }
}
