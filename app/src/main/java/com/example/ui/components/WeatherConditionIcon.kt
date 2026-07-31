package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import com.example.ui.icons.SkySphereIcons
import com.example.utils.WeatherTimeUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.dp

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
        effectiveIsNight && safeCondition == WeatherCondition.SUNNY -> SkySphereIcons.Moon
        effectiveIsNight && safeCondition == WeatherCondition.PARTLY_CLOUDY -> SkySphereIcons.MoonPartlyCloudy
        effectiveIsNight && safeCondition == WeatherCondition.CLOUDY -> SkySphereIcons.Cloud
        effectiveIsNight && safeCondition == WeatherCondition.RAINY -> SkySphereIcons.Rainy
        effectiveIsNight && safeCondition == WeatherCondition.STORM -> SkySphereIcons.Thunderstorm
        effectiveIsNight && safeCondition == WeatherCondition.SNOWY -> SkySphereIcons.Snowy
        effectiveIsNight && safeCondition == WeatherCondition.FOGGY -> SkySphereIcons.Foggy
        safeCondition == WeatherCondition.SUNNY -> SkySphereIcons.Sunny
        safeCondition == WeatherCondition.PARTLY_CLOUDY -> SkySphereIcons.SunPartlyCloudy
        safeCondition == WeatherCondition.CLOUDY -> SkySphereIcons.Cloud
        safeCondition == WeatherCondition.RAINY -> SkySphereIcons.Rainy
        safeCondition == WeatherCondition.STORM -> SkySphereIcons.Thunderstorm
        safeCondition == WeatherCondition.SNOWY -> SkySphereIcons.Snowy
        safeCondition == WeatherCondition.FOGGY -> SkySphereIcons.Foggy
        else -> SkySphereIcons.SunPartlyCloudy
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
    
    // 1. Sun rotates slowly
    val sunRotationAngle by if (safeCondition == WeatherCondition.SUNNY) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(25000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "SunRotation"
        )
    } else {
        remember { mutableStateOf(0f) }
    }

    // 2. Clouds drift gently
    val cloudDriftX by if (safeCondition == WeatherCondition.CLOUDY || safeCondition == WeatherCondition.PARTLY_CLOUDY) {
        infiniteTransition.animateFloat(
            initialValue = -4f,
            targetValue = 4f,
            animationSpec = infiniteRepeatable(
                animation = tween(3500, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "CloudDrift"
        )
    } else {
        remember { mutableStateOf(0f) }
    }

    // 3. Rain falls continuously
    val rainOffsetY by if (safeCondition == WeatherCondition.RAINY) {
        infiniteTransition.animateFloat(
            initialValue = -2f,
            targetValue = 3f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "RainOffset"
        )
    } else {
        remember { mutableStateOf(0f) }
    }

    // 4. Snow floats softly
    val snowSwayX by if (safeCondition == WeatherCondition.SNOWY) {
        infiniteTransition.animateFloat(
            initialValue = -3f,
            targetValue = 3f,
            animationSpec = infiniteRepeatable(
                animation = tween(2800, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "SnowSway"
        )
    } else {
        remember { mutableStateOf(0f) }
    }

    // 5. Lightning flashes briefly
    val flashScale by if (safeCondition == WeatherCondition.STORM) {
        infiniteTransition.animateFloat(
            initialValue = 0.95f,
            targetValue = 1.15f,
            animationSpec = infiniteRepeatable(
                animation = tween(1800, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "StormFlash"
        )
    } else {
        remember { mutableStateOf(1f) }
    }

    // 6. Fog moves slowly
    val fogDriftX by if (safeCondition == WeatherCondition.FOGGY) {
        infiniteTransition.animateFloat(
            initialValue = -5f,
            targetValue = 5f,
            animationSpec = infiniteRepeatable(
                animation = tween(4500, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "FogDrift"
        )
    } else {
        remember { mutableStateOf(0f) }
    }

    Icon(
        imageVector = icon,
        contentDescription = safeCondition.displayName,
        tint = tint ?: defaultTint,
        modifier = modifier
            .offset(x = (cloudDriftX + snowSwayX + fogDriftX).dp, y = rainOffsetY.dp)
            .rotate(sunRotationAngle)
            .scale(flashScale)
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
