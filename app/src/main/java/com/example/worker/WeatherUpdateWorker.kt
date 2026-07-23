package com.example.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.models.WeatherCondition
import com.example.data.repository.WeatherRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WeatherUpdateWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.d("WeatherUpdateWorker", "Starting periodic weather update execution...")

        try {
            val repository = WeatherRepository(applicationContext)

            // Force refresh active city weather data from repository
            val result = repository.forceRefreshActiveCity()

            val activeCity = repository.selectedCity.value

            if (activeCity.cityName == "Loading..." || activeCity.cityName.isBlank()) {
                Log.w("WeatherUpdateWorker", "Active city not loaded yet.")
                return@withContext Result.retry()
            }

            val details = activeCity.weatherDetails
            val cityName = activeCity.cityName
            val currentTemp = details.currentTemp
            val highTemp = details.highTemp
            val lowTemp = details.lowTemp
            val condition = details.condition
            val uvIndex = details.uvIndex

            Log.d("WeatherUpdateWorker", "Fetched weather for $cityName: $currentTemp°C, $condition")

            // Determine if there is a severe weather condition or trigger daily summary
            val isSevereAlert = isSevereCondition(condition, currentTemp, uvIndex)

            val title: String
            val message: String

            if (isSevereAlert) {
                title = "⚡ Severe Weather Alert: $cityName"
                message = buildSevereAlertText(cityName, condition, currentTemp, highTemp, uvIndex)
            } else {
                title = "🌤️ Daily Weather Summary: $cityName"
                message = "Currently $currentTemp°C (${condition.displayName}). Today's High: $highTemp°C, Low: $lowTemp°C. Air Quality: ${details.airQuality.description}."
            }

            WeatherNotificationManager.sendWeatherNotification(
                context = applicationContext,
                title = title,
                message = message,
                isAlert = isSevereAlert
            )

            Log.d("WeatherUpdateWorker", "WeatherUpdateWorker completed successfully for $cityName")
            Result.success()
        } catch (e: Exception) {
            Log.e("WeatherUpdateWorker", "Error executing weather update worker", e)
            Result.retry()
        }
    }

    private fun isSevereCondition(
        condition: WeatherCondition,
        currentTemp: Int,
        uvIndex: Int
    ): Boolean {
        return when (condition) {
            WeatherCondition.STORM,
            WeatherCondition.RAINY,
            WeatherCondition.SNOWY -> true
            else -> currentTemp >= 38 || currentTemp <= -10 || uvIndex >= 8
        }
    }

    private fun buildSevereAlertText(
        cityName: String,
        condition: WeatherCondition,
        currentTemp: Int,
        highTemp: Int,
        uvIndex: Int
    ): String {
        return when (condition) {
            WeatherCondition.STORM -> "Storm warning active in $cityName! Current temp: $currentTemp°C. High: $highTemp°C. Stay safe."
            WeatherCondition.RAINY -> "Rainy conditions in $cityName. Current temp: $currentTemp°C. High: $highTemp°C."
            WeatherCondition.SNOWY -> "Snowy weather in $cityName ($currentTemp°C). High: $highTemp°C. Drive carefully."
            else -> {
                if (currentTemp >= 38) {
                    "Extreme Heat Warning in $cityName! Current temperature: $currentTemp°C. Stay hydrated."
                } else if (currentTemp <= -10) {
                    "Extreme Cold Warning in $cityName! Current temperature: $currentTemp°C. Dress warmly."
                } else {
                    "High UV Alert in $cityName! UV Index is $uvIndex. Wear sun protection."
                }
            }
        }
    }
}
