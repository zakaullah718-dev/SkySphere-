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
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import com.example.data.models.WeatherCondition

@Composable
fun WeatherConditionIcon(
    condition: WeatherCondition?,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    isNight: Boolean = false
) {
    val safeCondition = condition ?: WeatherCondition.PARTLY_CLOUDY
    val icon = when {
        isNight && safeCondition == WeatherCondition.SUNNY -> Icons.Filled.Bedtime
        isNight && safeCondition == WeatherCondition.PARTLY_CLOUDY -> Icons.Filled.NightsStay
        isNight && safeCondition == WeatherCondition.CLOUDY -> Icons.Filled.Cloud
        isNight && safeCondition == WeatherCondition.RAINY -> Icons.Filled.WaterDrop
        isNight && safeCondition == WeatherCondition.STORM -> Icons.Filled.Thunderstorm
        isNight && safeCondition == WeatherCondition.SNOWY -> Icons.Filled.AcUnit
        safeCondition == WeatherCondition.SUNNY -> Icons.Filled.WbSunny
        safeCondition == WeatherCondition.PARTLY_CLOUDY -> Icons.Filled.CloudQueue
        safeCondition == WeatherCondition.CLOUDY -> Icons.Filled.Cloud
        safeCondition == WeatherCondition.RAINY -> Icons.Filled.WaterDrop
        safeCondition == WeatherCondition.STORM -> Icons.Filled.Thunderstorm
        safeCondition == WeatherCondition.SNOWY -> Icons.Filled.AcUnit
        else -> Icons.Filled.CloudQueue
    }

    val defaultTint = when {
        isNight && safeCondition == WeatherCondition.SUNNY -> Color(0xFFFFE082) // Soft Crescent Moon Amber
        isNight && safeCondition == WeatherCondition.PARTLY_CLOUDY -> Color(0xFF90CAF9) // Soft Night Cloud Blue
        isNight && safeCondition == WeatherCondition.CLOUDY -> Color(0xFF78909C) // Slate Night Cloud
        isNight && safeCondition == WeatherCondition.RAINY -> Color(0xFF80D8FF) // Night Rain Cyan
        isNight && safeCondition == WeatherCondition.STORM -> Color(0xFFD1C4E9) // Night Thunderstorm Lavender
        isNight && safeCondition == WeatherCondition.SNOWY -> Color(0xFFE0F7FA) // Night Snow Crystal White
        safeCondition == WeatherCondition.SUNNY -> Color(0xFFFFD54F) // Radiant Sun Amber
        safeCondition == WeatherCondition.PARTLY_CLOUDY -> Color(0xFF546E7A) // Dark steel slate
        safeCondition == WeatherCondition.CLOUDY -> Color(0xFF455A64) // Dark charcoal storm slate
        safeCondition == WeatherCondition.RAINY -> Color(0xFF4FC3F7) // Ocean rain blue
        safeCondition == WeatherCondition.STORM -> Color(0xFFB39DDB) // Cosmic storm lavender
        safeCondition == WeatherCondition.SNOWY -> Color(0xFF80DEEA) // Frozen glacial ice
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
