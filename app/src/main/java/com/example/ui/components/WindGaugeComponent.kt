package com.example.ui.components

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.icons.SkySphereIcons
import kotlin.math.*

fun parseWindDirectionDegrees(direction: String): Float {
    val digits = direction.filter { it.isDigit() || it == '.' }
    if (digits.isNotEmpty()) {
        val parsed = digits.toFloatOrNull()
        if (parsed != null && parsed >= 0f) return parsed % 360f
    }

    val upper = direction.trim().uppercase()
    return when {
        upper.contains("NNE") -> 22.5f
        upper.contains("ENE") -> 67.5f
        upper.contains("ESE") -> 112.5f
        upper.contains("SSE") -> 157.5f
        upper.contains("SSW") -> 202.5f
        upper.contains("WSW") -> 247.5f
        upper.contains("WNW") -> 292.5f
        upper.contains("NNW") -> 337.5f
        upper.contains("NE") -> 45f
        upper.contains("SE") -> 135f
        upper.contains("SW") -> 225f
        upper.contains("NW") -> 315f
        upper.contains("N") -> 0f
        upper.contains("E") -> 90f
        upper.contains("S") -> 180f
        upper.contains("W") -> 270f
        else -> 0f
    }
}

fun getBeaufortInfo(speedKmH: Double): Pair<Int, String> {
    return when {
        speedKmH < 2 -> 0 to "Calm"
        speedKmH < 6 -> 1 to "Light Air"
        speedKmH < 12 -> 2 to "Light Breeze"
        speedKmH < 20 -> 3 to "Gentle Breeze"
        speedKmH < 29 -> 4 to "Moderate Breeze"
        speedKmH < 39 -> 5 to "Fresh Breeze"
        speedKmH < 50 -> 6 to "Strong Breeze"
        speedKmH < 62 -> 7 to "High Wind"
        speedKmH < 75 -> 8 to "Gale"
        speedKmH < 89 -> 9 to "Strong Gale"
        speedKmH < 103 -> 10 to "Storm"
        speedKmH < 118 -> 11 to "Violent Storm"
        else -> 12 to "Hurricane Force"
    }
}

fun formatWindValue(speedKmH: Double, unit: String): String {
    return when (unit.lowercase()) {
        "mph" -> "${(speedKmH * 0.621371).roundToInt()} mph"
        "m/s" -> "${(speedKmH / 3.6).roundToInt()} m/s"
        "knots", "kts" -> "${(speedKmH * 0.539957).roundToInt()} kts"
        else -> "${speedKmH.roundToInt()} km/h"
    }
}

fun getDirectionName(degrees: Float): String {
    val norm = (degrees % 360f + 360f) % 360f
    return when {
        norm >= 337.5f || norm < 22.5f -> "North"
        norm >= 22.5f && norm < 67.5f -> "North-East"
        norm >= 67.5f && norm < 112.5f -> "East"
        norm >= 112.5f && norm < 157.5f -> "South-East"
        norm >= 157.5f && norm < 202.5f -> "South"
        norm >= 202.5f && norm < 247.5f -> "South-West"
        norm >= 247.5f && norm < 292.5f -> "West"
        norm >= 292.5f && norm < 337.5f -> "North-West"
        else -> "North"
    }
}

fun getShortDirectionName(degrees: Float): String {
    val norm = (degrees % 360f + 360f) % 360f
    return when {
        norm >= 337.5f || norm < 22.5f -> "N"
        norm >= 22.5f && norm < 67.5f -> "NE"
        norm >= 67.5f && norm < 112.5f -> "E"
        norm >= 112.5f && norm < 157.5f -> "SE"
        norm >= 157.5f && norm < 202.5f -> "S"
        norm >= 202.5f && norm < 247.5f -> "SW"
        norm >= 247.5f && norm < 292.5f -> "W"
        norm >= 292.5f && norm < 337.5f -> "NW"
        else -> "N"
    }
}

@Composable
fun WindGaugeCard(
    windSpeedKmH: Double,
    windDirection: String,
    windUnit: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    var isInitialized by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        isInitialized = true
    }

    var isCompassMode by rememberSaveable { mutableStateOf(false) }

    // 3D Morph/Flip Animation
    val flipAngle by animateFloatAsState(
        targetValue = if (isCompassMode) 180f else 0f,
        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label = "WindGauge3DFlip"
    )

    val targetDegrees = remember(windDirection) { parseWindDirectionDegrees(windDirection) }

    val animatedWindAngle by animateFloatAsState(
        targetValue = if (isInitialized) targetDegrees else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "WindGaugeAngle"
    )

    val maxSpeedKmH = 80.0
    val progressFraction = (windSpeedKmH / maxSpeedKmH).coerceIn(0.0, 1.0).toFloat()
    val animatedProgress by animateFloatAsState(
        targetValue = if (isInitialized) progressFraction else 0f,
        animationSpec = tween(
            durationMillis = 1100,
            easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
        ),
        label = "WindGaugeProgress"
    )

    val cardAlpha by animateFloatAsState(
        targetValue = if (isInitialized) 1f else 0f,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "WindGaugeAlpha"
    )

    val cardScale by animateFloatAsState(
        targetValue = if (isInitialized) 1f else 0.92f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "WindGaugeScale"
    )

    val cardTranslationY by animateFloatAsState(
        targetValue = if (isInitialized) 0f else 40f,
        animationSpec = tween(durationMillis = 600, easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)),
        label = "WindGaugeTranslationY"
    )

    // Sensor Manager Setup for Real-time Compass
    val sensorManager = remember { context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager }
    val isCompassAvailable = remember(sensorManager) {
        sensorManager != null && (
            sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null ||
            (sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null && sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) != null)
        )
    }

    var filteredHeadingDegrees by remember { mutableFloatStateOf(0f) }

    val animatedCompassHeading by animateFloatAsState(
        targetValue = filteredHeadingDegrees,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy),
        label = "AnimatedCompassHeading"
    )

    // Register Sensor Listener when in Compass Mode
    DisposableEffect(isCompassMode, sensorManager, isCompassAvailable) {
        if (!isCompassMode || sensorManager == null || !isCompassAvailable) return@DisposableEffect onDispose {}

        var currentFiltered = filteredHeadingDegrees

        val rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val magSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        val rotationMatrix = FloatArray(9)
        val orientationAngles = FloatArray(3)
        val lastAccel = FloatArray(3)
        val lastMag = FloatArray(3)
        var hasAccel = false
        var hasMag = false

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null) return
                var azimuthDeg: Float? = null

                if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
                    SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                    SensorManager.getOrientation(rotationMatrix, orientationAngles)
                    var deg = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
                    if (deg < 0) deg += 360f
                    azimuthDeg = deg
                } else {
                    if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                        System.arraycopy(event.values, 0, lastAccel, 0, 3)
                        hasAccel = true
                    } else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
                        System.arraycopy(event.values, 0, lastMag, 0, 3)
                        hasMag = true
                    }
                    if (hasAccel && hasMag) {
                        if (SensorManager.getRotationMatrix(rotationMatrix, null, lastAccel, lastMag)) {
                            SensorManager.getOrientation(rotationMatrix, orientationAngles)
                            var deg = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
                            if (deg < 0) deg += 360f
                            azimuthDeg = deg
                        }
                    }
                }

                azimuthDeg?.let { target ->
                    var diff = (target - currentFiltered) % 360f
                    if (diff < -180f) diff += 360f
                    if (diff > 180f) diff -= 360f
                    currentFiltered = (currentFiltered + 0.18f * diff + 360f) % 360f
                    filteredHeadingDegrees = currentFiltered
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        if (rotationVectorSensor != null) {
            sensorManager.registerListener(listener, rotationVectorSensor, SensorManager.SENSOR_DELAY_GAME)
        } else if (accelSensor != null && magSensor != null) {
            sensorManager.registerListener(listener, accelSensor, SensorManager.SENSOR_DELAY_GAME)
            sensorManager.registerListener(listener, magSensor, SensorManager.SENSOR_DELAY_GAME)
        }

        onDispose {
            sensorManager.unregisterListener(listener)
        }
    }

    // Haptic feedback pulse on North alignment (within 3.5 degrees)
    var wasNearNorth by remember { mutableStateOf(false) }
    val isNearNorth = (filteredHeadingDegrees <= 3.5f || filteredHeadingDegrees >= 356.5f)
    LaunchedEffect(isNearNorth, isCompassMode) {
        if (isCompassMode && isNearNorth && !wasNearNorth) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        wasNearNorth = isNearNorth
    }

    val (beaufortLevel, beaufortDesc) = remember(windSpeedKmH) { getBeaufortInfo(windSpeedKmH) }

    val textPrimary = MaterialTheme.colorScheme.onSurface
    val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant
    val trackBg = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val accentCyan = MaterialTheme.colorScheme.primary
    val accentSky = MaterialTheme.colorScheme.secondary

    val textMeasurer = rememberTextMeasurer()

    SkySphereCard(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = cardAlpha
                scaleX = cardScale
                scaleY = cardScale
                translationY = cardTranslationY
                rotationY = flipAngle
                cameraDistance = 12f * density
            },
        contentPadding = PaddingValues(20.dp)
    ) {
        val showCompassView = flipAngle >= 90f

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    if (showCompassView) {
                        rotationY = 180f
                    }
                }
        ) {
            if (!showCompassView) {
                // WIND GAUGE MODE
                WindGaugeModeContent(
                    windSpeedKmH = windSpeedKmH,
                    windDirection = windDirection,
                    windUnit = windUnit,
                    targetDegrees = targetDegrees,
                    animatedWindAngle = animatedWindAngle,
                    animatedProgress = animatedProgress,
                    beaufortLevel = beaufortLevel,
                    beaufortDesc = beaufortDesc,
                    onSwitchToCompass = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        isCompassMode = true
                    },
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    trackBg = trackBg,
                    accentCyan = accentCyan,
                    accentSky = accentSky,
                    textMeasurer = textMeasurer
                )
            } else {
                // DIGITAL COMPASS MODE
                CompassModeContent(
                    isCompassAvailable = isCompassAvailable,
                    deviceHeading = animatedCompassHeading,
                    windSpeedKmH = windSpeedKmH,
                    windDirection = windDirection,
                    windUnit = windUnit,
                    windDegrees = targetDegrees,
                    onSwitchToGauge = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        isCompassMode = false
                    },
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    trackBg = trackBg,
                    accentCyan = accentCyan,
                    accentSky = accentSky,
                    textMeasurer = textMeasurer
                )
            }
        }
    }
}

@Composable
private fun WindGaugeModeContent(
    windSpeedKmH: Double,
    windDirection: String,
    windUnit: String,
    targetDegrees: Float,
    animatedWindAngle: Float,
    animatedProgress: Float,
    beaufortLevel: Int,
    beaufortDesc: String,
    onSwitchToCompass: () -> Unit,
    textPrimary: Color,
    textSecondary: Color,
    trackBg: Color,
    accentCyan: Color,
    accentSky: Color,
    textMeasurer: TextMeasurer
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(accentCyan.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = SkySphereIcons.Wind,
                        contentDescription = null,
                        tint = accentCyan,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    text = "WIND GAUGE",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                        color = textSecondary
                    )
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = accentSky.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "Bft $beaufortLevel • $beaufortDesc",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = accentSky,
                            fontSize = 11.sp
                        ),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                // Compass Mode Switch Button
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSwitchToCompass() },
                    shape = RoundedCornerShape(12.dp),
                    color = accentCyan.copy(alpha = 0.2f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = SkySphereIcons.Compass,
                            contentDescription = "Switch to Compass",
                            tint = accentCyan,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "COMPASS",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = accentCyan,
                                fontSize = 11.sp
                            )
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Gauge Body
        Box(
            modifier = Modifier.size(210.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = min(size.width, size.height) / 2f - 18.dp.toPx()
                val strokeWidth = 8.dp.toPx()

                // Background Track Arc
                drawArc(
                    color = trackBg,
                    startAngle = 135f,
                    sweepAngle = 270f,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )

                // Active Speed Progress Arc
                val activeSweep = 270f * animatedProgress
                if (activeSweep > 0.5f) {
                    drawArc(
                        brush = Brush.sweepGradient(
                            0.0f to accentCyan,
                            0.75f to accentSky,
                            1.0f to accentCyan,
                            center = center
                        ),
                        startAngle = 135f,
                        sweepAngle = activeSweep,
                        useCenter = false,
                        topLeft = Offset(center.x - radius, center.y - radius),
                        size = Size(radius * 2, radius * 2),
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }

                // Compass Ticks & Labels
                val compassLabels = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
                for (i in 0 until 16) {
                    val angleDeg = i * 22.5f
                    val angleRad = Math.toRadians((angleDeg - 90.0)).toFloat()

                    val isMainCardinal = i % 4 == 0
                    val isSubCardinal = i % 2 == 0

                    val innerR = radius - (if (isMainCardinal) 14.dp.toPx() else if (isSubCardinal) 10.dp.toPx() else 6.dp.toPx())
                    val outerR = radius - 2.dp.toPx()

                    val start = Offset(
                        x = center.x + innerR * cos(angleRad),
                        y = center.y + innerR * sin(angleRad)
                    )
                    val end = Offset(
                        x = center.x + outerR * cos(angleRad),
                        y = center.y + outerR * sin(angleRad)
                    )

                    val tickColor = when {
                        isMainCardinal -> accentSky
                        isSubCardinal -> textSecondary.copy(alpha = 0.7f)
                        else -> textSecondary.copy(alpha = 0.3f)
                    }

                    drawLine(
                        color = tickColor,
                        start = start,
                        end = end,
                        strokeWidth = if (isMainCardinal) 2.5.dp.toPx() else 1.5.dp.toPx(),
                        cap = StrokeCap.Round
                    )

                    if (isMainCardinal) {
                        val cardinalIndex = i / 2
                        val label = compassLabels.getOrNull(cardinalIndex) ?: ""
                        val labelR = radius - 26.dp.toPx()
                        val labelX = center.x + labelR * cos(angleRad)
                        val labelY = center.y + labelR * sin(angleRad)

                        val style = TextStyle(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (label == "N") accentCyan else textSecondary
                        )
                        val textLayout = textMeasurer.measure(label, style)
                        drawText(
                            textLayoutResult = textLayout,
                            topLeft = Offset(
                                x = labelX - textLayout.size.width / 2f,
                                y = labelY - textLayout.size.height / 2f
                            )
                        )
                    }
                }

                // Rotating Arrow Pointer
                val pointerAngleRad = Math.toRadians((animatedWindAngle - 90.0)).toDouble()
                val pointerDistance = radius - 38.dp.toPx()
                val pointerCenter = Offset(
                    x = (center.x + pointerDistance * cos(pointerAngleRad)).toFloat(),
                    y = (center.y + pointerDistance * sin(pointerAngleRad)).toFloat()
                )

                rotate(degrees = animatedWindAngle, pivot = pointerCenter) {
                    val path = Path().apply {
                        moveTo(pointerCenter.x, pointerCenter.y - 12.dp.toPx())
                        lineTo(pointerCenter.x - 7.dp.toPx(), pointerCenter.y + 8.dp.toPx())
                        lineTo(pointerCenter.x, pointerCenter.y + 4.dp.toPx())
                        lineTo(pointerCenter.x + 7.dp.toPx(), pointerCenter.y + 8.dp.toPx())
                        close()
                    }
                    drawPath(
                        path = path,
                        brush = Brush.verticalGradient(
                            colors = listOf(accentCyan, accentSky),
                            startY = pointerCenter.y - 12.dp.toPx(),
                            endY = pointerCenter.y + 8.dp.toPx()
                        )
                    )
                }
            }

            // Center Display
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = SkySphereIcons.Wind,
                    contentDescription = "Wind Direction",
                    tint = accentCyan,
                    modifier = Modifier
                        .size(24.dp)
                        .rotate(animatedWindAngle)
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = formatWindValue(windSpeedKmH, windUnit),
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = textPrimary,
                        letterSpacing = (-0.5).sp
                    ),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "$windDirection (${targetDegrees.toInt()}°)",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = textSecondary
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Footer Metrics Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = trackBg.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "GUSTS",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = textSecondary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = formatWindValue(windSpeedKmH * 1.35, windUnit),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(24.dp)
                    .background(textSecondary.copy(alpha = 0.2f))
            )

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "DIRECTION",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = textSecondary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "$windDirection (${targetDegrees.toInt()}°)",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(24.dp)
                    .background(textSecondary.copy(alpha = 0.2f))
            )

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "BEAUFORT",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = textSecondary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Scale $beaufortLevel",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = accentCyan,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }
}

@Composable
private fun CompassModeContent(
    isCompassAvailable: Boolean,
    deviceHeading: Float,
    windSpeedKmH: Double,
    windDirection: String,
    windUnit: String,
    windDegrees: Float,
    onSwitchToGauge: () -> Unit,
    textPrimary: Color,
    textSecondary: Color,
    trackBg: Color,
    accentCyan: Color,
    accentSky: Color,
    textMeasurer: TextMeasurer
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(accentSky.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = SkySphereIcons.Compass,
                        contentDescription = null,
                        tint = accentSky,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    text = "DIGITAL COMPASS",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                        color = textSecondary
                    )
                )
            }

            // Return to Wind Gauge Button
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onSwitchToGauge() },
                shape = RoundedCornerShape(12.dp),
                color = accentCyan.copy(alpha = 0.2f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = SkySphereIcons.Wind,
                        contentDescription = "Switch to Wind Gauge",
                        tint = accentCyan,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "GAUGE",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = accentCyan,
                            fontSize = 11.sp
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (!isCompassAvailable) {
            // Fallback when device lacks compass hardware
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .background(trackBg.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = SkySphereIcons.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Compass is not available on this device.",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = textPrimary
                        ),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Hardware orientation sensors were not detected.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = textSecondary
                        ),
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            // Real-Time Compass Dial
            Box(
                modifier = Modifier.size(210.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val radius = min(size.width, size.height) / 2f - 12.dp.toPx()

                    // Outer Metallic Ring Gradient
                    drawCircle(
                        brush = Brush.sweepGradient(
                            listOf(
                                accentCyan.copy(alpha = 0.6f),
                                Color.White.copy(alpha = 0.8f),
                                accentSky.copy(alpha = 0.6f),
                                Color.White.copy(alpha = 0.3f),
                                accentCyan.copy(alpha = 0.6f)
                            )
                        ),
                        radius = radius,
                        center = center,
                        style = Stroke(width = 3.dp.toPx())
                    )

                    // Inner soft blue glow disc
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                accentCyan.copy(alpha = 0.12f),
                                accentSky.copy(alpha = 0.03f),
                                Color.Transparent
                            ),
                            center = center,
                            radius = radius
                        ),
                        radius = radius - 4.dp.toPx(),
                        center = center
                    )

                    // Rotating Compass Rose (rotated by -deviceHeading)
                    rotate(degrees = -deviceHeading, pivot = center) {
                        val compassLabels = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")

                        for (i in 0 until 16) {
                            val angleDeg = i * 22.5f
                            val angleRad = Math.toRadians((angleDeg - 90.0)).toFloat()

                            val isMainCardinal = i % 4 == 0
                            val isSubCardinal = i % 2 == 0

                            val innerR = radius - (if (isMainCardinal) 12.dp.toPx() else if (isSubCardinal) 8.dp.toPx() else 5.dp.toPx())
                            val outerR = radius - 4.dp.toPx()

                            val start = Offset(
                                x = center.x + innerR * cos(angleRad),
                                y = center.y + innerR * sin(angleRad)
                            )
                            val end = Offset(
                                x = center.x + outerR * cos(angleRad),
                                y = center.y + outerR * sin(angleRad)
                            )

                            val tickColor = when {
                                isMainCardinal -> if (i == 0) accentCyan else accentSky
                                isSubCardinal -> textSecondary.copy(alpha = 0.8f)
                                else -> textSecondary.copy(alpha = 0.35f)
                            }

                            drawLine(
                                color = tickColor,
                                start = start,
                                end = end,
                                strokeWidth = if (isMainCardinal) 3.dp.toPx() else 1.5.dp.toPx(),
                                cap = StrokeCap.Round
                            )

                            // Labels
                            if (isMainCardinal || isSubCardinal) {
                                val labelIndex = i / 2
                                val label = compassLabels.getOrNull(labelIndex) ?: ""
                                val labelR = radius - 22.dp.toPx()
                                val labelX = center.x + labelR * cos(angleRad)
                                val labelY = center.y + labelR * sin(angleRad)

                                val isNorth = (label == "N")
                                val style = TextStyle(
                                    fontSize = if (isNorth) 13.sp else if (isMainCardinal) 11.sp else 9.sp,
                                    fontWeight = if (isNorth || isMainCardinal) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isNorth) accentCyan else if (isMainCardinal) textPrimary else textSecondary
                                )
                                val textLayout = textMeasurer.measure(label, style)

                                // Keep text upright relative to rotating rose or text center
                                rotate(degrees = deviceHeading, pivot = Offset(labelX, labelY)) {
                                    drawText(
                                        textLayoutResult = textLayout,
                                        topLeft = Offset(
                                            x = labelX - textLayout.size.width / 2f,
                                            y = labelY - textLayout.size.height / 2f
                                        )
                                    )
                                }
                            }
                        }

                        // Glowing North Pointer Diamond on the Rose
                        val northRad = Math.toRadians(-90.0).toDouble()
                        val northTipR = radius - 2.dp.toPx()
                        val northBaseR = radius - 26.dp.toPx()
                        val northCenterX = (center.x + northBaseR * cos(northRad)).toFloat()
                        val northCenterY = (center.y + northBaseR * sin(northRad)).toFloat()

                        val northPath = Path().apply {
                            moveTo(center.x, center.y - northTipR)
                            lineTo(center.x - 6.dp.toPx(), northCenterY)
                            lineTo(center.x + 6.dp.toPx(), northCenterY)
                            close()
                        }
                        drawPath(
                            path = northPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(accentCyan, accentCyan.copy(alpha = 0.3f)),
                                startY = center.y - northTipR,
                                endY = northCenterY
                            )
                        )

                        // Wind Marker Dot on Compass Ring
                        val windRad = Math.toRadians((windDegrees - 90.0).toDouble())
                        val windDotR = radius - 10.dp.toPx()
                        val windDotX = (center.x + windDotR * cos(windRad)).toFloat()
                        val windDotY = (center.y + windDotR * sin(windRad)).toFloat()

                        drawCircle(
                            color = Color(0xFFFFB74D),
                            radius = 6.dp.toPx(),
                            center = Offset(windDotX, windDotY)
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 3.dp.toPx(),
                            center = Offset(windDotX, windDotY)
                        )
                    }

                    // Static Device Heading Needle (Top Indicator pointing directly forward)
                    val topNeedlePath = Path().apply {
                        moveTo(center.x, center.y - radius + 4.dp.toPx())
                        lineTo(center.x - 8.dp.toPx(), center.y - radius - 10.dp.toPx())
                        lineTo(center.x + 8.dp.toPx(), center.y - radius - 10.dp.toPx())
                        close()
                    }
                    drawPath(
                        path = topNeedlePath,
                        color = accentCyan
                    )
                }

                // Center Compass Info
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "${deviceHeading.toInt()}°",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = textPrimary,
                            letterSpacing = (-0.5).sp
                        ),
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = getShortDirectionName(deviceHeading),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = accentCyan
                        )
                    )

                    Text(
                        text = "HEADING",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = textSecondary,
                            letterSpacing = 1.sp
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Combined Information Box & Banner
        val facingName = getDirectionName(deviceHeading)
        val windName = getDirectionName(windDegrees)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = trackBg.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Interactive Comparative Banner
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = accentCyan.copy(alpha = 0.12f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "You are facing $facingName. Wind is coming from $windName.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = textPrimary,
                        fontSize = 11.5.sp
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Footer Metrics Breakdown
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "FACING",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = textSecondary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${getShortDirectionName(deviceHeading)} (${deviceHeading.toInt()}°)",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = accentCyan,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(22.dp)
                        .background(textSecondary.copy(alpha = 0.2f))
                )

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "WIND FROM",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = textSecondary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${getShortDirectionName(windDegrees)} (${windDegrees.toInt()}°)",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = textPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(22.dp)
                        .background(textSecondary.copy(alpha = 0.2f))
                )

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "WIND SPEED",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = textSecondary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = formatWindValue(windSpeedKmH, windUnit),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = textPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }
    }
}
