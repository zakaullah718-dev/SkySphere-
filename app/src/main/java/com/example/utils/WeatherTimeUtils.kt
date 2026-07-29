package com.example.utils

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

object WeatherTimeUtils {

    fun parseTimeToMinutes(timeStr: String): Int {
        return try {
            val cleaned = timeStr.trim().uppercase(Locale.US)
            val isPm = cleaned.endsWith("PM")
            val isAm = cleaned.endsWith("AM")
            val timePart = cleaned.replace("AM", "").replace("PM", "").trim()
            val parts = timePart.split(":")
            var h = parts[0].toInt()
            val m = parts.getOrNull(1)?.toInt() ?: 0
            if (isPm && h < 12) h += 12
            if (isAm && h == 12) h = 0
            h * 60 + m
        } catch (e: Exception) {
            360 // Default 6:00 AM
        }
    }

    /**
     * Determines whether a given timestamp (or location current time if 0) is nighttime
     * based on the target location's timezone and local sunrise/sunset times, NOT relying solely on the device clock.
     */
    fun isNightForLocation(
        timestampEpochMillis: Long = 0L,
        timeZoneId: String? = null,
        sunriseStr: String = "06:00 AM",
        sunsetStr: String = "07:00 PM",
        localTimeStr: String? = null,
        latitude: Double? = null,
        longitude: Double? = null
    ): Boolean {
        val sunriseMin = parseTimeToMinutes(sunriseStr).coerceIn(0, 1439)
        val sunsetMin = parseTimeToMinutes(sunsetStr).coerceIn(0, 1439)

        val currentMinutes = if (!localTimeStr.isNullOrBlank() && timestampEpochMillis == 0L) {
            parseTimeToMinutes(localTimeStr).coerceIn(0, 1439)
        } else {
            val tz = resolveTimeZone(timeZoneId, longitude)
            val cal = Calendar.getInstance(tz).apply {
                timeInMillis = if (timestampEpochMillis > 0L) timestampEpochMillis else System.currentTimeMillis()
            }
            cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        }

        return if (sunsetMin >= sunriseMin) {
            currentMinutes < sunriseMin || currentMinutes >= sunsetMin
        } else {
            // Edge case crossing midnight
            currentMinutes in sunsetMin..<sunriseMin
        }
    }

    fun getCurrentMinutesForLocation(
        localTimeStr: String? = null,
        timeZoneId: String? = null,
        longitude: Double? = null
    ): Int {
        return if (!localTimeStr.isNullOrBlank()) {
            parseTimeToMinutes(localTimeStr).coerceIn(0, 1439)
        } else {
            val tz = resolveTimeZone(timeZoneId, longitude)
            val cal = Calendar.getInstance(tz)
            cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        }
    }

    private fun resolveTimeZone(timeZoneId: String?, longitude: Double?): TimeZone {
        if (!timeZoneId.isNullOrBlank()) {
            try {
                val tz = TimeZone.getTimeZone(timeZoneId)
                if (tz.id != "GMT" || timeZoneId.equals("GMT", ignoreCase = true) || timeZoneId.startsWith("GMT")) {
                    return tz
                }
            } catch (_: Exception) {}
        }
        if (longitude != null) {
            val offsetHours = Math.round(longitude / 15.0).toInt()
            val customGmt = "GMT${if (offsetHours >= 0) "+" else ""}$offsetHours"
            return TimeZone.getTimeZone(customGmt)
        }
        return TimeZone.getDefault()
    }

    /**
     * Helper to check if it is daytime at location.
     */
    fun isDayTimeForLocation(
        timestampEpochMillis: Long = 0L,
        timeZoneId: String? = null,
        sunriseStr: String = "06:00 AM",
        sunsetStr: String = "07:00 PM",
        localTimeStr: String? = null,
        latitude: Double? = null,
        longitude: Double? = null
    ): Boolean {
        return !isNightForLocation(
            timestampEpochMillis = timestampEpochMillis,
            timeZoneId = timeZoneId,
            sunriseStr = sunriseStr,
            sunsetStr = sunsetStr,
            localTimeStr = localTimeStr,
            latitude = latitude,
            longitude = longitude
        )
    }
}
