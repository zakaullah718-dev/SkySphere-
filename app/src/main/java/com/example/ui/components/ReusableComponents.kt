package com.example.ui.components

import androidx.compose.animation.core.InfiniteTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import com.example.data.models.WeatherCondition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A highly polished, luxury card for SkySphere that displays content
 * with a premium Glassmorphism effect: semi-transparent gradients,
 * high-contrast reflective borders, and rounded corners.
 */
@Composable
fun SkySphereCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    borderWidth: Dp = 1.dp,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val isDark = MaterialTheme.colorScheme.background.value == 0xFF070913.toULong()
    
    // Premium translucent Glassmorphism background brushes
    val bgBrush = if (isDark) {
        Brush.verticalGradient(
            colors = listOf(
                Color(0x3D1E254C), // Translucent deep indigo
                Color(0x24111530)  // Translucent dark obsidian
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xD9FFFFFF), // Bright reflective glass
                Color(0xB3F1F5F9)  // Soft mist bottom
            )
        )
    }

    // High-contrast, micro-reflective border stroke
    val borderColor = if (isDark) {
        Color(0x2BFFFFFF) // White reflection
    } else {
        Color(0x1B000000) // Deep drop shadow outline
    }

    val clickModifier = if (onClick != null) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .background(bgBrush)
            .border(borderWidth, borderColor, MaterialTheme.shapes.large)
            .then(clickModifier)
            .padding(contentPadding)
    ) {
        Column {
            content()
        }
    }
}

/**
 * A premium pill-shaped gradient button.
 */
@Composable
fun SkySphereButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    testTag: String = "skysphere_button"
) {
    val gradient = Brush.linearGradient(
        colors = listOf(
            Color(0xFF2FA3FF), // Vibrant Sky Blue
            Color(0xFF00C6FF)  // Glowing Cyan
        )
    )

    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent, // Managed via gradient modifier
            contentColor = Color.White
        ),
        contentPadding = PaddingValues(), // Disable padding so modifier handles it
        shape = CircleShape,
        modifier = modifier
            .height(54.dp)
            .testTag(testTag)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(gradient, CircleShape)
                .padding(horizontal = 28.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

/**
 * A minimalist, modern icon button with an elegant circle outline.
 */
@Composable
fun SkySphereIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    testTag: String = "skysphere_icon_button"
) {
    val isDark = MaterialTheme.colorScheme.background.value == 0xFF070913.toULong()
    val borderColor = if (isDark) Color(0xFF1E254C) else Color(0xFFE2E8F0)
    val tint = if (isDark) Color(0xFFF1F5F9) else Color(0xFF0F172A)
    
    IconButton(
        onClick = onClick,
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = if (isDark) Color(0x1A13172E) else Color(0x0D0F172A)
        ),
        modifier = modifier
            .size(48.dp)
            .border(1.dp, borderColor, CircleShape)
            .testTag(testTag)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
    }
}

/**
 * SkySphere animated orbital loading screen indicator.
 * Represents a central "SkySphere" globe with an elegant cosmic orbit line rotating around it.
 */
@Composable
fun SkySphereLoadingAnimation(
    modifier: Modifier = Modifier,
    size: Dp = 80.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "SkySphere Loading")
    
    // Rotating orbit lines
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Orbit Rotation"
    )

    // Pulsing core (Sphere)
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Core Pulsing"
    )

    val coreColor = Color(0xFF2FA3FF)
    val ringColor = Color(0xFF00E5FF)

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        // Celestial central orb (sphere)
        Box(
            modifier = Modifier
                .size(size * 0.45f * pulseScale)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(coreColor, coreColor.copy(alpha = 0.4f), Color.Transparent)
                    ),
                    shape = CircleShape
                )
        )

        // Celestial orbiting ring
        Box(
            modifier = Modifier
                .size(size * 0.9f)
                .rotate(rotationAngle)
                .drawBehind {
                    drawArc(
                        color = ringColor,
                        startAngle = -45f,
                        sweepAngle = 90f,
                        useCenter = false,
                        style = Stroke(width = 3.dp.toPx())
                    )
                    drawArc(
                        color = ringColor.copy(alpha = 0.2f),
                        startAngle = 45f,
                        sweepAngle = 270f,
                        useCenter = false,
                        style = Stroke(width = 1.dp.toPx())
                    )
                }
        )
    }
}

/**
 * Parses sunrise and sunset string times to decide if it's currently day or night.
 */
fun isDayTime(sunrise: String, sunset: String): Boolean {
    try {
        val now = java.util.Calendar.getInstance()
        val hour = now.get(java.util.Calendar.HOUR_OF_DAY)
        val minute = now.get(java.util.Calendar.MINUTE)
        val currentMinutes = hour * 60 + minute

        fun parseTimeToMinutes(timeStr: String): Int {
            val cleaned = timeStr.trim().uppercase()
            val isPm = cleaned.endsWith("PM")
            val isAm = cleaned.endsWith("AM")
            val timePart = cleaned.replace("AM", "").replace("PM", "").trim()
            val parts = timePart.split(":")
            var h = parts[0].toInt()
            val m = parts[1].toInt()
            if (isPm && h < 12) h += 12
            if (isAm && h == 12) h = 0
            return h * 60 + m
        }

        val sunriseMin = parseTimeToMinutes(sunrise)
        val sunsetMin = parseTimeToMinutes(sunset)
        return currentMinutes in sunriseMin..sunsetMin
    } catch (e: Exception) {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return hour in 6..19
    }
}

/**
 * A highly premium animated backdrop that shifts colors dynamically based on
 * the weather condition and current day/night cycles, complete with micro-rendered particle systems.
 */
@Composable
fun WeatherAnimatedBackground(
    condition: WeatherCondition,
    sunrise: String,
    sunset: String,
    visibilityKm: Double = 10.0,
    windSpeed: Double = 0.0,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val isDay = isDayTime(sunrise, sunset)
    
    val backgroundType = when {
        !isDay -> "night"
        visibilityKm <= 1.0 -> "fog"
        visibilityKm <= 3.0 -> "mist"
        visibilityKm <= 5.0 -> "haze"
        condition == WeatherCondition.RAINY && windSpeed >= 20.0 -> "heavy_rain"
        condition == WeatherCondition.RAINY -> "rain"
        condition == WeatherCondition.STORM -> "thunderstorm"
        condition == WeatherCondition.SNOWY -> "snow"
        condition == WeatherCondition.CLOUDY -> "cloudy"
        condition == WeatherCondition.PARTLY_CLOUDY -> "partly_cloudy"
        condition == WeatherCondition.SUNNY -> "sunny"
        else -> "sunny"
    }

    val gradientColors = when (backgroundType) {
        "night" -> listOf(Color(0xFF02040A), Color(0xFF0F172A), Color(0xFF1E1B4B))
        "fog" -> listOf(Color(0xFFCFD8DC), Color(0xFFECEFF1), Color(0xFFB0BEC5))
        "mist" -> listOf(Color(0xFFE2E8F0), Color(0xFFECEFF1), Color(0xFFCFD8DC))
        "haze" -> listOf(Color(0xFFF5F5F5), Color(0xFFE0E0E0), Color(0xFFD7CCC8))
        "heavy_rain" -> listOf(Color(0xFF1A237E), Color(0xFF37474F), Color(0xFF263238))
        "rain" -> listOf(Color(0xFF37474F), Color(0xFF546E7A), Color(0xFF90A4AE))
        "thunderstorm" -> listOf(Color(0xFF212121), Color(0xFF311B92), Color(0xFF1A237E))
        "snow" -> listOf(Color(0xFFE0F7FA), Color(0xFFB2EBF2), Color(0xFF80DEEA))
        "cloudy" -> listOf(Color(0xFF78909C), Color(0xFF90A4AE), Color(0xFFCFD8DC))
        "partly_cloudy" -> listOf(Color(0xFF4FC3F7), Color(0xFF81D4FA), Color(0xFFE1F5FE))
        "sunny" -> listOf(Color(0xFFFFB300), Color(0xFFFF8F00), Color(0xFFFF7043))
        else -> listOf(Color(0xFF4FC3F7), Color(0xFFE1F5FE))
    }

    val infiniteTransition = rememberInfiniteTransition(label = "BackgroundAnimation")
    
    val animatedOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(25000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "SkyGradientShift"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                val brush = Brush.linearGradient(
                    colors = gradientColors,
                    start = Offset(0f, animatedOffset / 2),
                    end = Offset(size.width, size.height + animatedOffset)
                )
                drawRect(brush = brush)
            }
    ) {
        when (backgroundType) {
            "night" -> NightStarsOverlay(infiniteTransition)
            "fog" -> FogOverlay(infiniteTransition)
            "mist" -> MistOverlay(infiniteTransition)
            "haze" -> HazeOverlay(infiniteTransition)
            "heavy_rain" -> HeavyRainOverlay(infiniteTransition)
            "rain" -> RainFallOverlay(infiniteTransition)
            "thunderstorm" -> StormLightningOverlay(infiniteTransition)
            "snow" -> SnowDriftOverlay(infiniteTransition)
            "cloudy" -> DenseCloudOverlay(infiniteTransition)
            "partly_cloudy" -> CloudDriftOverlay(infiniteTransition, WeatherCondition.PARTLY_CLOUDY)
            "sunny" -> SunnyRaysOverlay(infiniteTransition)
        }
        content()
    }
}

@Composable
fun NightStarsOverlay(infiniteTransition: InfiniteTransition) {
    val alpha1 by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Reverse),
        label = "StarAlpha1"
    )
    val alpha2 by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 0.1f,
        animationSpec = infiniteRepeatable(tween(2500, easing = LinearEasing), RepeatMode.Reverse),
        label = "StarAlpha2"
    )

    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        if (width > 0 && height > 0) {
            val stars1 = listOf(
                Pair(0.12f, 0.15f), Pair(0.35f, 0.08f), Pair(0.72f, 0.25f), 
                Pair(0.88f, 0.12f), Pair(0.48f, 0.35f), Pair(0.62f, 0.05f),
                Pair(0.22f, 0.45f), Pair(0.95f, 0.38f)
            )
            val stars2 = listOf(
                Pair(0.25f, 0.28f), Pair(0.55f, 0.18f), Pair(0.82f, 0.32f), 
                Pair(0.92f, 0.06f), Pair(0.38f, 0.48f), Pair(0.68f, 0.22f),
                Pair(0.08f, 0.32f), Pair(0.50f, 0.02f)
            )
            stars1.forEach { (x, y) ->
                drawCircle(
                    color = Color.White.copy(alpha = alpha1),
                    radius = 2.dp.toPx(),
                    center = Offset(x * width, y * height)
                )
            }
            stars2.forEach { (x, y) ->
                drawCircle(
                    color = Color.White.copy(alpha = alpha2),
                    radius = 1.5.dp.toPx(),
                    center = Offset(x * width, y * height)
                )
            }

            val moonX = width * 0.82f
            val moonY = 150.dp.toPx()
            val moonR = 30.dp.toPx()

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFFFDE7).copy(alpha = 0.25f), Color.Transparent),
                    center = Offset(moonX, moonY),
                    radius = moonR * 2.2f
                ),
                radius = moonR * 2.2f,
                center = Offset(moonX, moonY)
            )

            val moonPath = Path().apply {
                moveTo(moonX + moonR * 0.5f, moonY - moonR)
                cubicTo(
                    moonX - moonR * 0.8f, moonY - moonR * 0.8f,
                    moonX - moonR * 0.8f, moonY + moonR * 0.8f,
                    moonX + moonR * 0.5f, moonY + moonR
                )
                cubicTo(
                    moonX - moonR * 0.1f, moonY + moonR * 0.6f,
                    moonX - moonR * 0.1f, moonY - moonR * 0.6f,
                    moonX + moonR * 0.5f, moonY - moonR
                )
                close()
            }
            drawPath(
                path = moonPath,
                color = Color(0xFFFFFDE7)
            )
        }
    }
}

@Composable
fun SunnyRaysOverlay(infiniteTransition: InfiniteTransition) {
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Reverse),
        label = "SunnyRayPulse"
    )
    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        if (width > 0) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFFF9C4).copy(alpha = 0.35f * (2f - scale)), Color.Transparent),
                    center = Offset(width * 0.85f, 150.dp.toPx()),
                    radius = 220.dp.toPx() * scale
                ),
                radius = 220.dp.toPx() * scale,
                center = Offset(width * 0.85f, 150.dp.toPx())
            )
        }
    }
}

@Composable
fun RainFallOverlay(infiniteTransition: InfiniteTransition) {
    val rainYOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
        label = "RainYOffset"
    )
    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        if (width > 0 && height > 0) {
            val rainLinesCount = 35
            for (i in 0 until rainLinesCount) {
                val startX = (i * 37) % width.toInt()
                val startYBase = (i * 97) % height.toInt()
                val currentY = (startYBase + rainYOffset) % height
                drawLine(
                    color = Color.White.copy(alpha = 0.25f),
                    start = Offset(startX.toFloat(), currentY),
                    end = Offset(startX.toFloat() - 5.dp.toPx(), currentY + 15.dp.toPx()),
                    strokeWidth = 1.2.dp.toPx()
                )
            }
        }
    }
}

@Composable
fun HeavyRainOverlay(infiniteTransition: InfiniteTransition) {
    val rainYOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Restart),
        label = "HeavyRainY"
    )
    val density = androidx.compose.ui.platform.LocalDensity.current
    val splashRadius by infiniteTransition.animateFloat(
        initialValue = with(density) { 2.dp.toPx() },
        targetValue = with(density) { 12.dp.toPx() },
        animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Restart),
        label = "SplashRadius"
    )

    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        if (width > 0 && height > 0) {
            val rainLinesCount = 60
            for (i in 0 until rainLinesCount) {
                val startX = (i * 29) % width.toInt()
                val startYBase = (i * 131) % height.toInt()
                val currentY = (startYBase + rainYOffset) % height
                val slant = 10.dp.toPx()
                drawLine(
                    color = Color(0xFFA5D6A7).copy(alpha = 0.35f),
                    start = Offset(startX.toFloat(), currentY),
                    end = Offset(startX.toFloat() - slant, currentY + 22.dp.toPx()),
                    strokeWidth = 2.dp.toPx()
                )
            }

            val splashPoints = listOf(
                Pair(0.15f, 0.92f), Pair(0.35f, 0.95f), Pair(0.55f, 0.91f), 
                Pair(0.75f, 0.94f), Pair(0.9f, 0.93f), Pair(0.25f, 0.96f)
            )
            splashPoints.forEach { (x, y) ->
                drawOval(
                    color = Color.White.copy(alpha = (1f - (splashRadius / 12.dp.toPx())).coerceIn(0f, 1f) * 0.4f),
                    topLeft = Offset(x * width - splashRadius, y * height - splashRadius / 3f),
                    size = Size(splashRadius * 2f, splashRadius * 0.6f),
                    style = Stroke(width = 1.dp.toPx())
                )
            }
        }
    }
}

@Composable
fun StormLightningOverlay(infiniteTransition: InfiniteTransition) {
    val lightningFlash by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Restart),
        label = "LightningFlash"
    )
    val flashAlpha = if (lightningFlash > 0.4f && lightningFlash < 0.42f) 0.6f 
                     else if (lightningFlash > 0.44f && lightningFlash < 0.46f) 0.8f 
                     else 0f

    Box(modifier = Modifier.fillMaxSize()) {
        HeavyRainOverlay(infiniteTransition = infiniteTransition)
        if (flashAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = flashAlpha))
            )
        }
    }
}

@Composable
fun SnowDriftOverlay(infiniteTransition: InfiniteTransition) {
    val snowYOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 600f,
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Restart),
        label = "SnowYOffset"
    )
    val snowXDrift by infiniteTransition.animateFloat(
        initialValue = -30f,
        targetValue = 30f,
        animationSpec = infiniteRepeatable(tween(2500, easing = LinearEasing), RepeatMode.Reverse),
        label = "SnowXDrift"
    )
    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        if (width > 0 && height > 0) {
            val flakeCount = 25
            for (i in 0 until flakeCount) {
                val startX = (i * 59) % width.toInt()
                val startYBase = (i * 101) % height.toInt()
                val currentY = (startYBase + snowYOffset) % height
                val currentX = (startX + snowXDrift) % width
                drawCircle(
                    color = Color.White.copy(alpha = 0.5f),
                    radius = (2f + (i % 3)).dp.toPx(),
                    center = Offset(currentX, currentY)
                )
            }
        }
    }
}

@Composable
fun CloudDriftOverlay(infiniteTransition: InfiniteTransition, condition: WeatherCondition) {
    val cloudOffset by infiniteTransition.animateFloat(
        initialValue = -150f,
        targetValue = 450f,
        animationSpec = infiniteRepeatable(tween(25000, easing = LinearEasing), RepeatMode.Restart),
        label = "CloudDrift"
    )
    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        if (width > 0) {
            val alpha = if (condition == WeatherCondition.CLOUDY) 0.15f else 0.08f
            drawCircle(
                color = Color.White.copy(alpha = alpha),
                radius = 160.dp.toPx(),
                center = Offset(cloudOffset.dp.toPx(), 180.dp.toPx())
            )
            drawCircle(
                color = Color.White.copy(alpha = alpha),
                radius = 120.dp.toPx(),
                center = Offset((cloudOffset + 120f).dp.toPx(), 220.dp.toPx())
            )
        }
    }
}

@Composable
fun DenseCloudOverlay(infiniteTransition: InfiniteTransition) {
    val cloudOffset1 by infiniteTransition.animateFloat(
        initialValue = -250f,
        targetValue = 450f,
        animationSpec = infiniteRepeatable(tween(40000, easing = LinearEasing), RepeatMode.Restart),
        label = "DenseCloud1"
    )
    val cloudOffset2 by infiniteTransition.animateFloat(
        initialValue = -150f,
        targetValue = 550f,
        animationSpec = infiniteRepeatable(tween(55000, easing = LinearEasing), RepeatMode.Restart),
        label = "DenseCloud2"
    )

    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        if (width > 0) {
            val cloudColor = Color.White.copy(alpha = 0.12f)
            drawCircle(
                color = cloudColor,
                radius = 180.dp.toPx(),
                center = Offset(cloudOffset1.dp.toPx(), 140.dp.toPx())
            )
            drawCircle(
                color = cloudColor,
                radius = 140.dp.toPx(),
                center = Offset((cloudOffset1 + 130f).dp.toPx(), 180.dp.toPx())
            )
            drawCircle(
                color = cloudColor,
                radius = 200.dp.toPx(),
                center = Offset(cloudOffset2.dp.toPx(), 220.dp.toPx())
            )
            drawCircle(
                color = cloudColor,
                radius = 150.dp.toPx(),
                center = Offset((cloudOffset2 - 120f).dp.toPx(), 200.dp.toPx())
            )
        }
    }
}

@Composable
fun FogOverlay(infiniteTransition: InfiniteTransition) {
    val fogOffset1 by infiniteTransition.animateFloat(
        initialValue = -200f,
        targetValue = 600f,
        animationSpec = infiniteRepeatable(tween(35000, easing = LinearEasing), RepeatMode.Restart),
        label = "FogOffset1"
    )
    val fogOffset2 by infiniteTransition.animateFloat(
        initialValue = 600f,
        targetValue = -200f,
        animationSpec = infiniteRepeatable(tween(45000, easing = LinearEasing), RepeatMode.Restart),
        label = "FogOffset2"
    )
    val fogAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(tween(5000, easing = LinearEasing), RepeatMode.Reverse),
        label = "FogAlpha"
    )

    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        if (width > 0 && height > 0) {
            drawOval(
                color = Color.White.copy(alpha = fogAlpha * 0.4f),
                topLeft = Offset(fogOffset1.dp.toPx(), height * 0.4f),
                size = Size(width * 0.8f, height * 0.3f)
            )
            drawOval(
                color = Color.White.copy(alpha = fogAlpha * 0.35f),
                topLeft = Offset(fogOffset2.dp.toPx(), height * 0.6f),
                size = Size(width * 0.9f, height * 0.35f)
            )
        }
    }
}

@Composable
fun MistOverlay(infiniteTransition: InfiniteTransition) {
    val driftX by infiniteTransition.animateFloat(
        initialValue = -50f,
        targetValue = 150f,
        animationSpec = infiniteRepeatable(tween(12000, easing = LinearEasing), RepeatMode.Restart),
        label = "MistDriftX"
    )
    val driftY by infiniteTransition.animateFloat(
        initialValue = -20f,
        targetValue = 20f,
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Reverse),
        label = "MistDriftY"
    )

    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        if (width > 0 && height > 0) {
            val mistParticleCount = 20
            for (i in 0 until mistParticleCount) {
                val startX = (i * 113) % width.toInt()
                val startY = (i * 73) % (height * 0.7f).toInt()
                val currentX = (startX + driftX) % width
                val currentY = startY + driftY
                drawCircle(
                    color = Color.White.copy(alpha = 0.15f),
                    radius = (10 + (i % 8)).dp.toPx(),
                    center = Offset(currentX, currentY)
                )
            }
        }
    }
}

@Composable
fun HazeOverlay(infiniteTransition: InfiniteTransition) {
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Reverse),
        label = "HazeAlpha"
    )

    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        if (width > 0 && height > 0) {
            val hazeColor = Color(0xFFD7CCC8)
            drawRect(
                color = hazeColor.copy(alpha = alpha * 0.5f),
                topLeft = Offset(0f, height * 0.2f),
                size = Size(width, 40.dp.toPx())
            )
            drawRect(
                color = hazeColor.copy(alpha = alpha * 0.7f),
                topLeft = Offset(0f, height * 0.45f),
                size = Size(width, 60.dp.toPx())
            )
            drawRect(
                color = hazeColor.copy(alpha = alpha * 0.4f),
                topLeft = Offset(0f, height * 0.7f),
                size = Size(width, 50.dp.toPx())
            )
        }
    }
}
