package com.example.widget

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SkySphereWidgetUpdateWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.d("SkySphereWidgetWorker", "Updating SkySphere widgets in background...")
        try {
            SkySphereWidgetManager.updateAllWidgets(applicationContext)
            Result.success()
        } catch (e: Exception) {
            Log.e("SkySphereWidgetWorker", "Error updating widgets", e)
            if (runAttemptCount >= 2) Result.failure() else Result.retry()
        }
    }
}
