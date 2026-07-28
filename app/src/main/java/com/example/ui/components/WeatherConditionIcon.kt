package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import com.example.data.models.CityWeather
import com.example.data.models.WeatherCondition
import com.example.data.models.WeatherDetails
import com.example.utils.WeatherTimeUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun WeatherConditionIcon(
    condition: WeatherCondition?,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    isNight: Boolean? = null,
    sunrise: String? = null,
    sunset: String? = null,
    timeZoneId: String? = null,
    timestampEpochMillis: Long = 0L,
    localTimeStr: String? = null,
    latitude: Double? = null,
    longitude: Double? = null
) {
    // Dynamic Day/Night state with automatic live updates
    var effectiveIsNight by remember(condition, isNight, sunrise, sunset, timeZoneId, timestampEpochMillis, localTimeStr, latitude, longitude) {
        mutableStateOf(
            isNight ?: if (!sunrise.isNullOrBlank() && !sunset.isNullOrBlank()) {
                WeatherTimeUtils.isNightForLocation(
                    timestampEpochMillis = timestampEpochMillis,
                    timeZoneId = timeZoneId,
                    sunriseStr = sunrise,
                    sunsetStr = sunset,
                    localTimeStr = localTimeStr,
                    latitude = latitude,
                    longitude = longitude
                )
            } else false
        )
    }

    // Ticker to automatically update icon when sunrise or sunset occurs while app is open
    if (isNight == null && !sunrise.isNullOrBlank() && !sunset.isNullOrBlank()) {
        LaunchedEffect(sunrise, sunset, timeZoneId, timestampEpochMillis, localTimeStr, latitude, longitude) {
            while (isActive) {
                val updated = WeatherTimeUtils.isNightForLocation(
                    timestampEpochMillis = timestampEpochMillis,
                    timeZoneId = timeZoneId,
                    sunriseStr = sunrise,
                    sunsetStr = sunset,
                    localTimeStr = localTimeStr,
                    latitude = latitude,
                    longitude = longitude
                )
                if (updated != effectiveIsNight) {
                    effectiveIsNight = updated
                }
                delay(10_000L) // Re-evaluate every 10 seconds
            }
        }
    }

    val safeCondition = condition ?: WeatherCondition.PARTLY_CLOUDY
    val icon = when {
        effectiveIsNight && safeCondition == WeatherCondition.SUNNY -> Icons.Filled.Bedtime
        effectiveIsNight && safeCondition == WeatherCondition.PARTLY_CLOUDY -> Icons.Filled.NightsStay
        effectiveIsNight && safeCondition == WeatherCondition.CLOUDY -> Icons.Filled.Cloud
        effectiveIsNight && safeCondition == WeatherCondition.RAINY -> Icons.Filled.WaterDrop
        effectiveIsNight && safeCondition == WeatherCondition.STORM -> Icons.Filled.Thunderstorm
        effectiveIsNight && safeCondition == WeatherCondition.SNOWY -> Icons.Filled.AcUnit
        effectiveIsNight && safeCondition == WeatherCondition.FOGGY -> Icons.Filled.BlurOn
        safeCondition == WeatherCondition.SUNNY -> Icons.Filled.WbSunny
        safeCondition == WeatherCondition.PARTLY_CLOUDY -> Icons.Filled.CloudQueue
        safeCondition == WeatherCondition.CLOUDY -> Icons.Filled.Cloud
        safeCondition == WeatherCondition.RAINY -> Icons.Filled.WaterDrop
        safeCondition == WeatherCondition.STORM -> Icons.Filled.Thunderstorm
        safeCondition == WeatherCondition.SNOWY -> Icons.Filled.AcUnit
        safeCondition == WeatherCondition.FOGGY -> Icons.Filled.Grain
        else -> Icons.Filled.CloudQueue
    }

    val defaultTint = when {
        effectiveIsNight && safeCondition == WeatherCondition.SUNNY -> Color(0xFFFFE082) // Soft Crescent Moon Amber
        effectiveIsNight && safeCondition == WeatherCondition.PARTLY_CLOUDY -> Color(0xFF90CAF9) // Soft Night Cloud Blue
        effectiveIsNight && safeCondition == WeatherCondition.CLOUDY -> Color(0xFF78909C) // Slate Night Cloud
        effectiveIsNight && safeCondition == WeatherCondition.RAINY -> Color(0xFF80D8FF) // Night Rain Cyan
        effectiveIsNight && safeCondition == WeatherCondition.STORM -> Color(0xFFD1C4E9) // Night Thunderstorm Lavender
        effectiveIsNight && safeCondition == WeatherCondition.SNOWY -> Color(0xFFE0F7FA) // Night Snow Crystal White
        effectiveIsNight && safeCondition == WeatherCondition.FOGGY -> Color(0xFF90A4AE) // Night Foggy Blue-Grey
        safeCondition == WeatherCondition.SUNNY -> Color(0xFFFFD54F) // Radiant Sun Amber
        safeCondition == WeatherCondition.PARTLY_CLOUDY -> Color(0xFF546E7A) // Dark steel slate
        safeCondition == WeatherCondition.CLOUDY -> Color(0xFF455A64) // Dark charcoal storm slate
        safeCondition == WeatherCondition.RAINY -> Color(0xFF4FC3F7) // Ocean rain blue
        safeCondition == WeatherCondition.STORM -> Color(0xFFB39DDB) // Cosmic storm lavender
        safeCondition == WeatherCondition.SNOWY -> Color(0xFF80DEEA) // Frozen glacial ice
        safeCondition == WeatherCondition.FOGGY -> Color(0xFF78909C) // Mist Grey
        else -> Color(0xFF546E7A)
    }

    val infiniteTransition = rememberInfiniteTransition(label = "IconAnimation")
    
    // Slow rotation for Sunny and Snowy
    val rotationAngle by if (safeCondition == WeatherCondition.SUNNY || safeCondition == WeatherCondition.SNOWY) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(25000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "IconRotation"
        )
    } else {
        androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(0f) }
    }

    // Micro-pulsation for Rain/Storm/Partly Cloudy
    val pulseScale by if (safeCondition == WeatherCondition.RAINY || safeCondition == WeatherCondition.STORM || safeCondition == WeatherCondition.PARTLY_CLOUDY) {
        infiniteTransition.animateFloat(
            initialValue = 0.95f,
            targetValue = 1.05f,
            animationSpec = infiniteRepeatable(
                animation = tween(2500, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "IconPulse"
        )
    } else {
        androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(1f) }
    }

    Icon(
        imageVector = icon,
        contentDescription = safeCondition.displayName,
        tint = tint ?: defaultTint,
        modifier = modifier
            .rotate(rotationAngle)
            .scale(pulseScale)
    )
}

@Composable
fun WeatherConditionIcon(
    weatherDetails: WeatherDetails,
    modifier: Modifier = Modifier,
    tint: Color? = null
) {
    WeatherConditionIcon(
        condition = weatherDetails.condition,
        modifier = modifier,
        tint = tint,
        isNight = weatherDetails.isNight,
        sunrise = weatherDetails.sunrise,
        sunset = weatherDetails.sunset,
        timeZoneId = weatherDetails.timeZoneId
    )
}

@Composable
fun WeatherConditionIcon(
    cityWeather: CityWeather,
    modifier: Modifier = Modifier,
    tint: Color? = null
) {
    WeatherConditionIcon(
        condition = cityWeather.weatherDetails.condition,
        modifier = modifier,
        tint = tint,
        isNight = cityWeather.isNight,
        sunrise = cityWeather.weatherDetails.sunrise,
        sunset = cityWeather.weatherDetails.sunset,
        timeZoneId = cityWeather.timeZoneId ?: cityWeather.weatherDetails.timeZoneId,
        localTimeStr = cityWeather.localTime,
        latitude = cityWeather.latitude,
        longitude = cityWeather.longitude
    )
}
