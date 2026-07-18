package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudQueue
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
    condition: WeatherCondition,
    modifier: Modifier = Modifier,
    tint: Color? = null
) {
    val icon = when (condition) {
        WeatherCondition.SUNNY -> Icons.Filled.WbSunny
        WeatherCondition.PARTLY_CLOUDY -> Icons.Filled.CloudQueue
        WeatherCondition.CLOUDY -> Icons.Filled.Cloud
        WeatherCondition.RAINY -> Icons.Filled.WaterDrop
        WeatherCondition.STORM -> Icons.Filled.Thunderstorm
        WeatherCondition.SNOWY -> Icons.Filled.AcUnit
    }

    val defaultTint = when (condition) {
        WeatherCondition.SUNNY -> Color(0xFFFFD54F) // Radiant Sun Amber
        WeatherCondition.PARTLY_CLOUDY -> Color(0xFF90CAF9) // Atmospheric soft blue
        WeatherCondition.CLOUDY -> Color(0xFFCFD8DC) // Misty silver-grey
        WeatherCondition.RAINY -> Color(0xFF4FC3F7) // Ocean rain blue
        WeatherCondition.STORM -> Color(0xFFB39DDB) // Cosmic storm lavender
        WeatherCondition.SNOWY -> Color(0xFF80DEEA) // Frozen glacial ice
    }

    val infiniteTransition = rememberInfiniteTransition(label = "IconAnimation")
    
    // Slow rotation for Sunny and Snowy
    val rotationAngle by if (condition == WeatherCondition.SUNNY || condition == WeatherCondition.SNOWY) {
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
    val pulseScale by if (condition == WeatherCondition.RAINY || condition == WeatherCondition.STORM || condition == WeatherCondition.PARTLY_CLOUDY) {
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
        contentDescription = condition.displayName,
        tint = tint ?: defaultTint,
        modifier = modifier
            .rotate(rotationAngle)
            .scale(pulseScale)
    )
}
