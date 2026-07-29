package com.example.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.R
import com.example.data.models.WeatherCondition
import com.example.data.repository.UserPreferencesRepository
import com.example.data.repository.WeatherRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

import com.example.utils.WeatherTimeUtils

fun WeatherCondition.getSingleEmoji(isNight: Boolean): String {
    return when (this) {
        WeatherCondition.SUNNY -> if (isNight) "🌙" else "☀️"
        WeatherCondition.PARTLY_CLOUDY -> if (isNight) "🌥" else "🌤"
        WeatherCondition.CLOUDY -> "☁️"
        WeatherCondition.RAINY -> "🌧"
        WeatherCondition.STORM -> "⛈"
        WeatherCondition.SNOWY -> "❄️"
        WeatherCondition.FOGGY -> "🌫"
    }
}

val WeatherCondition.emoji: String
    get() = getSingleEmoji(false)

object WeatherNotificationManager {

    const val CHANNEL_ID = "skysphere_weather_alerts"
    private const val CHANNEL_NAME = "SkySphere Weather Briefings & Alerts"
    private const val CHANNEL_DESC = "Periodic weather updates every 6 hours, severe weather alerts, and daily forecasts."
    const val NOTIFICATION_ID = 1001

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESC
                enableVibration(true)
                setShowBadge(true)
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            Log.d("WeatherNotificationManager", "Notification channel created: $CHANNEL_ID")
        }
    }

    fun formatDisplayLocation(cityName: String, region: String?, country: String?): String {
        val cleanCity = cityName.trim()
        val cleanRegion = region?.trim()
        val cleanCountry = country?.trim()

        val parts = mutableListOf<String>()
        if (cleanCity.isNotBlank() && cleanCity != "Loading...") {
            parts.add(cleanCity)
        }
        if (!cleanRegion.isNullOrBlank() && !cleanRegion.equals(cleanCity, ignoreCase = true) && cleanRegion != "Unknown") {
            parts.add(cleanRegion)
        }
        if (!cleanCountry.isNullOrBlank() && cleanCountry != "Unknown") {
            parts.add(cleanCountry)
        }
        return if (parts.isNotEmpty()) parts.joinToString(", ") else cleanCity
    }

    fun buildGreeting(name: String, hourOfDay: Int): String {
        val cleanName = name.trim()
        val hasName = cleanName.isNotBlank()

        return when (hourOfDay) {
            in 5..11 -> if (hasName) "Good Morning, $cleanName" else "Good Morning"
            in 12..16 -> if (hasName) "Hi, $cleanName" else "Hello"
            in 17..20 -> if (hasName) "Good Evening, $cleanName" else "Good Evening"
            else -> if (hasName) "Good Evening, $cleanName" else "Good Evening"
        }
    }

    fun formatNotificationTemp(tempF: Int, isCelsius: Boolean): String {
        return if (isCelsius) {
            val tempC = (tempF - 32) * 5 / 9
            "$tempC°C"
        } else {
            "$tempF°F"
        }
    }

    suspend fun sendInstantTestNotification(context: Context) = withContext(Dispatchers.IO) {
        createNotificationChannel(context)

        val userPrefs = UserPreferencesRepository.getInstance(context)
        val repository = WeatherRepository(context.applicationContext)

        val cityWeather = repository.refreshLiveWeatherForNotification(context)

        if (cityWeather == null || cityWeather.cityName.isBlank() || cityWeather.cityName == "Loading...") {
            Log.w("WeatherNotificationManager", "Could not fetch valid weather for notification.")
            return@withContext
        }

        val details = cityWeather.weatherDetails
        if (details.currentTemp == 0 && details.highTemp == 0 && details.humidity == 0) {
            Log.w("WeatherNotificationManager", "Weather details contain invalid placeholder zeroes.")
            return@withContext
        }

        val userName = userPrefs.getUserName()
        val isCelsius = repository.isCelsius.value

        val displayLocation = formatDisplayLocation(cityWeather.cityName, cityWeather.region, cityWeather.country)
        val currentTempStr = formatNotificationTemp(details.currentTemp, isCelsius)
        val highTempStr = formatNotificationTemp(details.highTemp, isCelsius)
        val lowTempStr = formatNotificationTemp(details.lowTemp, isCelsius)
        val condition = details.condition
        val humidity = details.humidity
        val windSpeedKmH = details.windSpeed.toInt()

        val isNight = WeatherTimeUtils.isNightForLocation(
            timeZoneId = cityWeather.timeZoneId,
            sunriseStr = details.sunrise,
            sunsetStr = details.sunset,
            localTimeStr = cityWeather.localTime
        )
        val weatherIcon = condition.getSingleEmoji(isNight)

        val calendar = Calendar.getInstance()
        val hourOfDay = calendar.get(Calendar.HOUR_OF_DAY)

        val greeting = buildGreeting(userName, hourOfDay)
        val title = "$greeting $weatherIcon"
        val subText = "$displayLocation • $currentTempStr • ${condition.displayName}"

        val bigTextBuilder = StringBuilder()
        bigTextBuilder.append("Current location: $displayLocation\n")
        bigTextBuilder.append("Weather: ${condition.displayName} $weatherIcon\n")
        bigTextBuilder.append("Temperature: $currentTempStr (High: $highTempStr / Low: $lowTempStr)\n")
        bigTextBuilder.append("Humidity: $humidity% • Wind: $windSpeedKmH km/h")

        sendWeatherNotification(
            context = context,
            title = title,
            subText = subText,
            bigText = bigTextBuilder.toString().trim(),
            isAlert = false,
            notificationId = (System.currentTimeMillis() % 10000).toInt() + 2000
        )
    }

    fun sendWeatherNotification(
        context: Context,
        title: String,
        subText: String,
        bigText: String,
        isAlert: Boolean = false,
        notificationId: Int = NOTIFICATION_ID
    ) {
        createNotificationChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("OPEN_WEATHER_NOTIF", true)
        }

        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val appIconBitmap = try {
            BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher)
        } catch (e: Exception) {
            null
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(subText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setPriority(if (isAlert) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        if (appIconBitmap != null) {
            builder.setLargeIcon(appIconBitmap)
        }

        try {
            val notificationManager = NotificationManagerCompat.from(context)
            if (notificationManager.areNotificationsEnabled()) {
                notificationManager.notify(notificationId, builder.build())
                Log.d("WeatherNotificationManager", "Posted notification ($notificationId): $title")
            } else {
                Log.w("WeatherNotificationManager", "Notifications disabled for app")
            }
        } catch (e: SecurityException) {
            Log.e("WeatherNotificationManager", "Permission missing for notification: ${e.message}")
        } catch (e: Exception) {
            Log.e("WeatherNotificationManager", "Error sending notification: ${e.message}")
        }
    }
}
