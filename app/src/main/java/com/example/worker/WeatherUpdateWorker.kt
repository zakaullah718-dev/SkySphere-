package com.example.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.models.CityWeather
import com.example.data.models.WeatherCondition
import com.example.data.models.WeatherDetails
import com.example.data.repository.UserPreferencesRepository
import com.example.data.repository.WeatherRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

class WeatherUpdateWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.d("WeatherUpdateWorker", "Executing scheduled weather notification worker (attempt $runAttemptCount)...")

        val userPrefs = UserPreferencesRepository.getInstance(applicationContext)

        // Check if notifications are enabled
        if (!userPrefs.isNotificationsEnabled()) {
            Log.d("WeatherUpdateWorker", "Notifications disabled by user. Skipping work.")
            return@withContext Result.success()
        }

        try {
            val repository = WeatherRepository(applicationContext)

            // Force refresh active city weather data from network/API
            val refreshSuccess = try {
                repository.forceRefreshActiveCity()
                true
            } catch (e: Exception) {
                Log.w("WeatherUpdateWorker", "Failed to reach weather API: ${e.message}")
                false
            }

            // CRITICAL REQUIREMENT: If the weather API cannot be reached, skip the notification
            // instead of sending outdated information.
            if (!refreshSuccess) {
                Log.w("WeatherUpdateWorker", "Weather API could not be reached. Skipping notification per policy.")
                return@withContext Result.success()
            }

            val activeCity: CityWeather = repository.selectedCity.value

            if (activeCity.cityName == "Loading..." || activeCity.cityName.isBlank()) {
                Log.w("WeatherUpdateWorker", "Active city data is not loaded yet. Skipping notification.")
                return@withContext Result.success()
            }

            val details: WeatherDetails = activeCity.weatherDetails
            val cityName = activeCity.cityName
            val userName = userPrefs.getUserName()
            val isCelsius = repository.isCelsius.value

            fun formatTemp(celsius: Int): String {
                return if (isCelsius) "$celsius°C" else "${(celsius * 9 / 5) + 32}°F"
            }

            val currentTempStr = formatTemp(details.currentTemp)
            val highTempStr = formatTemp(details.highTemp)
            val lowTempStr = formatTemp(details.lowTemp)
            val condition = details.condition
            val humidity = details.humidity
            val windSpeedKmH = details.windSpeed.toInt()

            val calendar = Calendar.getInstance()
            val hourOfDay = calendar.get(Calendar.HOUR_OF_DAY)

            val greeting = WeatherNotificationManager.buildGreeting(userName, hourOfDay)
            val title = buildNotificationTitle(greeting, condition, hourOfDay)
            val subText = "Current weather in $cityName is $currentTempStr."
            val smartAdvice = buildSmartAdvice(details, hourOfDay, isCelsius)

            val bigTextBuilder = StringBuilder()
            bigTextBuilder.append("Current weather in $cityName is $currentTempStr.\n")
            bigTextBuilder.append("${condition.displayName} with a high of $highTempStr today.\n")
            bigTextBuilder.append("Humidity: $humidity%\n")
            bigTextBuilder.append("Wind: $windSpeedKmH km/h\n")
            if (smartAdvice.isNotBlank()) {
                bigTextBuilder.append(smartAdvice)
            }

            val bigText = bigTextBuilder.toString().trim()

            // Duplicate prevention check
            val contentHash = "$cityName-$currentTempStr-${condition.name}-$hourOfDay"
            if (userPrefs.isDuplicateNotification(contentHash)) {
                Log.d("WeatherUpdateWorker", "Duplicate notification detected for $contentHash. Skipping.")
                return@withContext Result.success()
            }

            // Post Material 3 styled notification
            WeatherNotificationManager.sendWeatherNotification(
                context = applicationContext,
                title = title,
                subText = subText,
                bigText = bigText,
                isAlert = isSevereCondition(condition, details.currentTemp, details.uvIndex)
            )

            userPrefs.recordNotificationSent(contentHash)

            Log.d("WeatherUpdateWorker", "Notification sent successfully for $cityName to ${userName.ifBlank { "User" }}")
            Result.success()
        } catch (e: Exception) {
            Log.e("WeatherUpdateWorker", "Error in WeatherUpdateWorker: ${e.message}", e)
            Result.success() // Return success to avoid unnecessary battery drain on background error
        }
    }

    private fun buildGreeting(name: String, hourOfDay: Int): String {
        val cleanName = name.trim()
        val hasName = cleanName.isNotBlank()

        return when (hourOfDay) {
            in 5..11 -> if (hasName) "Good Morning $cleanName ☀️" else "Good Morning ☀️"
            in 12..16 -> if (hasName) "Hi $cleanName 👋" else "Hello 👋"
            in 17..20 -> if (hasName) "Good Evening $cleanName 🌙" else "Good Evening 🌙"
            else -> if (hasName) "Good Evening $cleanName 🌙" else "Good Evening 🌙"
        }
    }

    private fun buildNotificationTitle(greeting: String, condition: WeatherCondition, hourOfDay: Int): String {
        return greeting
    }

    private fun buildSmartAdvice(details: WeatherDetails, hourOfDay: Int, isCelsius: Boolean): String {
        val maxPrecipChance = details.hourlyForecast.take(12).maxOfOrNull { it.precipitationChance } ?: 0
        val isRainy = details.condition == WeatherCondition.RAINY || details.condition == WeatherCondition.STORM || maxPrecipChance >= 40

        if (isRainy) {
            return if (hourOfDay >= 18) {
                "Rain is possible tonight after 9 PM.\nDon't forget your umbrella."
            } else {
                "Rain showers expected today.\nDon't forget your umbrella."
            }
        }

        if (details.uvIndex >= 6 && hourOfDay in 10..16) {
            return "UV Index is High this afternoon."
        }

        if (details.currentTemp >= 35) {
            return "Extreme heat today. Stay hydrated and seek shade."
        }

        if (details.currentTemp <= 0) {
            return "Freezing conditions outside. Dress warmly."
        }

        if (details.windSpeed >= 35) {
            return "Breezy conditions with strong gusts expected today."
        }

        return if (hourOfDay < 17) "Have a great day!" else "Enjoy your evening!"
    }

    private fun isSevereCondition(
        condition: WeatherCondition,
        currentTemp: Int,
        uvIndex: Int
    ): Boolean {
        return when (condition) {
            WeatherCondition.STORM,
            WeatherCondition.SNOWY -> true
            else -> currentTemp >= 38 || currentTemp <= -10 || uvIndex >= 8
        }
    }
}
