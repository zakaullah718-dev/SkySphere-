package com.example.ui.components

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
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

    // 3D Morph/Flip Animation (350ms duration)
    val flipAngle by animateFloatAsState(
        targetValue = if (isCompassMode) 180f else 0f,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
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
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "WindGaugeAlpha"
    )

    val cardScale by animateFloatAsState(
        targetValue = if (isInitialized) 1f else 0.95f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "WindGaugeScale"
    )

    val cardTranslationY by animateFloatAsState(
        targetValue = if (isInitialized) 0f else 30f,
        animationSpec = tween(durationMillis = 500, easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)),
        label = "WindGaugeTranslationY"
    )

    // Sensor Manager Setup for Professional Compass Filtering & Calibration
    val sensorManager = remember { context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager }
    val isCompassAvailable = remember(sensorManager) {
        sensorManager != null && (
            sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null ||
            (sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null && sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) != null)
        )
    }

    var filteredHeadingDegrees by remember { mutableFloatStateOf(0f) }
    var sensorAccuracy by remember { mutableIntStateOf(SensorManager.SENSOR_STATUS_ACCURACY_HIGH) }

    // Smooth Spring Animation for Compass Heading with Inertia
    val animatedCompassHeading by animateFloatAsState(
        targetValue = filteredHeadingDegrees,
        animationSpec = spring(
            stiffness = Spring.StiffnessMediumLow,
            dampingRatio = Spring.DampingRatioNoBouncy
        ),
        label = "AnimatedCompassHeading"
    )

    // Sensor Listener lifecycle: paused when Compass Mode is closed or off-screen
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
                    // Shortest-path angle delta calculation to avoid 360 -> 0 jump glitch
                    var diff = (target - currentFiltered) % 360f
                    if (diff < -180f) diff += 360f
                    if (diff > 180f) diff -= 360f

                    // Deadband threshold: ignore minor noise < 0.3 degrees when stationary to prevent jitter
                    if (abs(diff) > 0.3f) {
                        // Dynamic Low-Pass Filter factor: fast reaction for large turns, extra smooth for small drifts
                        val filterAlpha = if (abs(diff) > 15f) 0.35f else 0.12f
                        currentFiltered = (currentFiltered + filterAlpha * diff + 360f) % 360f
                        filteredHeadingDegrees = currentFiltered
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                if (sensor?.type == Sensor.TYPE_MAGNETIC_FIELD || sensor?.type == Sensor.TYPE_ROTATION_VECTOR) {
                    sensorAccuracy = accuracy
                }
            }
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

    // Light Haptic pulse when compass aligns near North (within 3.0 degrees)
    var wasNearNorth by remember { mutableStateOf(false) }
    val isNearNorth = (filteredHeadingDegrees <= 3.0f || filteredHeadingDegrees >= 357.0f)
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
                    sensorAccuracy = sensorAccuracy,
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

                // Sleek Header Compass Icon Button
                Surface(
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { onSwitchToCompass() },
                    shape = CircleShape,
                    color = accentCyan.copy(alpha = 0.18f)
                ) {
                    Box(
                        modifier = Modifier.padding(6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = SkySphereIcons.Compass,
                            contentDescription = "Switch to Compass",
                            tint = accentCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Gauge Body
        Box(
            modifier = Modifier.size(220.dp),
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

                // Rotating Wind Pointer
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
    sensorAccuracy: Int,
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

            // Sleek Header Wind Icon Button
            Surface(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { onSwitchToGauge() },
                shape = CircleShape,
                color = accentCyan.copy(alpha = 0.18f)
            ) {
                Box(
                    modifier = Modifier.padding(6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = SkySphereIcons.Wind,
                        contentDescription = "Switch to Wind Gauge",
                        tint = accentCyan,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Non-intrusive Calibration Banner if low accuracy is detected
        val needsCalibration = isCompassAvailable && (
            sensorAccuracy == SensorManager.SENSOR_STATUS_ACCURACY_LOW ||
            sensorAccuracy == SensorManager.SENSOR_STATUS_UNRELIABLE
        )
        AnimatedVisibility(
            visible = needsCalibration,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "∞",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    )
                    Text(
                        text = "Move your phone in a figure-eight motion to improve compass accuracy.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            fontSize = 11.sp
                        )
                    )
                }
            }
        }

        if (!isCompassAvailable) {
            // Fallback when device lacks compass hardware
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
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
            // Flagship Aviation/Marine Digital Navigation Compass Dial
            val surfaceColor = MaterialTheme.colorScheme.surface
            Box(
                modifier = Modifier.size(240.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val outerRadius = min(size.width, size.height) / 2f - 10.dp.toPx()

                    // 1. Metallic Glass Outer Ring with Brushed Sweep Reflections
                    drawCircle(
                        brush = Brush.sweepGradient(
                            listOf(
                                accentCyan.copy(alpha = 0.85f),
                                Color.White.copy(alpha = 0.95f),
                                accentSky.copy(alpha = 0.7f),
                                Color.White.copy(alpha = 0.35f),
                                accentCyan.copy(alpha = 0.85f)
                            )
                        ),
                        radius = outerRadius,
                        center = center,
                        style = Stroke(width = 3.dp.toPx())
                    )

                    // Inner Precision Glass Bezel
                    drawCircle(
                        color = Color.White.copy(alpha = 0.15f),
                        radius = outerRadius - 4.dp.toPx(),
                        center = center,
                        style = Stroke(width = 1.dp.toPx())
                    )

                    // Soft Ambient Blue Radial Backlight
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                accentCyan.copy(alpha = 0.18f),
                                accentSky.copy(alpha = 0.05f),
                                Color.Transparent
                            ),
                            center = center,
                            radius = outerRadius
                        ),
                        radius = outerRadius - 5.dp.toPx(),
                        center = center
                    )

                    // 2. Rotating Compass Rose Dial (Rotated by -deviceHeading)
                    rotate(degrees = -deviceHeading, pivot = center) {
                        // Detailed 360° Tick Marks: Major every 30°, Fine ticks every 5°
                        for (angleDeg in 0 until 360 step 5) {
                            val isMainCardinal = (angleDeg % 90 == 0)
                            val isSubCardinal = (angleDeg % 45 == 0 && !isMainCardinal)
                            val isMajor30 = (angleDeg % 30 == 0)
                            val isMedium15 = (angleDeg % 15 == 0 && !isMajor30)

                            val angleRad = Math.toRadians((angleDeg - 90.0)).toFloat()

                            val tickLength = when {
                                isMainCardinal -> 14.dp.toPx()
                                isMajor30 -> 10.dp.toPx()
                                isMedium15 -> 7.dp.toPx()
                                else -> 4.dp.toPx() // 5 degree fine tick
                            }

                            val tickOuterR = outerRadius - 6.dp.toPx()
                            val tickInnerR = tickOuterR - tickLength

                            val startPos = Offset(
                                x = center.x + tickInnerR * cos(angleRad),
                                y = center.y + tickInnerR * sin(angleRad)
                            )
                            val endPos = Offset(
                                x = center.x + tickOuterR * cos(angleRad),
                                y = center.y + tickOuterR * sin(angleRad)
                            )

                            // North Halo / Glowing Accent at 0° North
                            if (angleDeg == 0) {
                                val northHaloCenter = Offset(
                                    x = center.x + (outerRadius - 16.dp.toPx()) * cos(angleRad),
                                    y = center.y + (outerRadius - 16.dp.toPx()) * sin(angleRad)
                                )
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(accentCyan.copy(alpha = 0.55f), Color.Transparent),
                                        center = northHaloCenter,
                                        radius = 20.dp.toPx()
                                    ),
                                    radius = 20.dp.toPx(),
                                    center = northHaloCenter
                                )
                            }

                            val tickColor = when {
                                angleDeg == 0 -> accentCyan
                                isMainCardinal -> accentSky
                                isMajor30 -> textSecondary.copy(alpha = 0.8f)
                                isMedium15 -> textSecondary.copy(alpha = 0.5f)
                                else -> textSecondary.copy(alpha = 0.25f)
                            }

                            val strokeW = when {
                                isMainCardinal -> 2.5.dp.toPx()
                                isMajor30 -> 1.8.dp.toPx()
                                isMedium15 -> 1.2.dp.toPx()
                                else -> 0.8.dp.toPx()
                            }

                            drawLine(
                                color = tickColor,
                                start = startPos,
                                end = endPos,
                                strokeWidth = strokeW,
                                cap = StrokeCap.Round
                            )

                            // 3. Dial Degree Numerals & Compass Direction Labels
                            if (isMainCardinal || isSubCardinal || isMajor30) {
                                val labelText = when {
                                    angleDeg == 0 -> "N"
                                    angleDeg == 45 -> "NE"
                                    angleDeg == 90 -> "E"
                                    angleDeg == 135 -> "SE"
                                    angleDeg == 180 -> "S"
                                    angleDeg == 225 -> "SW"
                                    angleDeg == 270 -> "W"
                                    angleDeg == 315 -> "NW"
                                    else -> "$angleDeg" // 30, 60, 120, 150, 210, 240, 300, 330
                                }

                                val isNorth = (labelText == "N")
                                val isCardinalText = isMainCardinal || isSubCardinal

                                val labelR = outerRadius - (if (isCardinalText) 26.dp.toPx() else 24.dp.toPx())
                                val labelX = center.x + labelR * cos(angleRad)
                                val labelY = center.y + labelR * sin(angleRad)

                                val style = TextStyle(
                                    fontSize = when {
                                        isNorth -> 15.sp
                                        isMainCardinal -> 12.sp
                                        isSubCardinal -> 10.sp
                                        else -> 8.sp // Major 30 degree numbers
                                    },
                                    fontWeight = when {
                                        isNorth || isMainCardinal -> FontWeight.ExtraBold
                                        isSubCardinal -> FontWeight.Bold
                                        else -> FontWeight.Medium
                                    },
                                    color = when {
                                        isNorth -> accentCyan
                                        isMainCardinal -> textPrimary
                                        isSubCardinal -> textSecondary
                                        else -> textSecondary.copy(alpha = 0.65f)
                                    }
                                )
                                val textLayout = textMeasurer.measure(labelText, style)

                                // Rotate text upright relative to device view orientation
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

                        // 4. Premium Precision Navigation Needle (North pointer with soft glow & depth)
                        val northRad = Math.toRadians(-90.0).toDouble()
                        val northTipR = outerRadius - 3.dp.toPx()
                        val northBaseR = outerRadius - 32.dp.toPx()
                        val northBaseY = (center.y + northBaseR * sin(northRad)).toFloat()

                        // Soft Blue Halo Glow at North Tip
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(accentCyan.copy(alpha = 0.7f), Color.Transparent),
                                center = Offset(center.x, center.y - northTipR),
                                radius = 12.dp.toPx()
                            ),
                            radius = 12.dp.toPx(),
                            center = Offset(center.x, center.y - northTipR)
                        )

                        // North Needle Arrow Shape (Dual-Tone 3D Facet)
                        val northLeftPath = Path().apply {
                            moveTo(center.x, center.y - northTipR)
                            lineTo(center.x - 7.dp.toPx(), northBaseY)
                            lineTo(center.x, northBaseY + 4.dp.toPx())
                            close()
                        }
                        drawPath(
                            path = northLeftPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(accentCyan, accentCyan.copy(alpha = 0.7f)),
                                startY = center.y - northTipR,
                                endY = northBaseY
                            )
                        )

                        val northRightPath = Path().apply {
                            moveTo(center.x, center.y - northTipR)
                            lineTo(center.x + 7.dp.toPx(), northBaseY)
                            lineTo(center.x, northBaseY + 4.dp.toPx())
                            close()
                        }
                        drawPath(
                            path = northRightPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(Color.White, accentCyan.copy(alpha = 0.85f)),
                                startY = center.y - northTipR,
                                endY = northBaseY
                            )
                        )

                        // South Needle Arrow Tail (Muted Depth Balance)
                        val southTipR = outerRadius - 3.dp.toPx()
                        val southBaseR = outerRadius - 32.dp.toPx()
                        val southBaseY = center.y + southBaseR

                        val southPath = Path().apply {
                            moveTo(center.x, center.y + southTipR)
                            lineTo(center.x - 5.dp.toPx(), southBaseY)
                            lineTo(center.x + 5.dp.toPx(), southBaseY)
                            close()
                        }
                        drawPath(
                            path = southPath,
                            color = textSecondary.copy(alpha = 0.35f)
                        )

                        // 5. Weather Wind Direction Dot on Rim
                        val windRad = Math.toRadians((windDegrees - 90.0).toDouble())
                        val windDotR = outerRadius - 8.dp.toPx()
                        val windDotX = (center.x + windDotR * cos(windRad)).toFloat()
                        val windDotY = (center.y + windDotR * sin(windRad)).toFloat()

                        drawCircle(
                            color = Color(0xFFFFB74D),
                            radius = 5.5.dp.toPx(),
                            center = Offset(windDotX, windDotY)
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 2.5.dp.toPx(),
                            center = Offset(windDotX, windDotY)
                        )
                    }

                    // 6. Static Forward Heading Top Pointer Bezel Notch
                    val topBezelPath = Path().apply {
                        moveTo(center.x, center.y - outerRadius + 2.dp.toPx())
                        lineTo(center.x - 7.dp.toPx(), center.y - outerRadius - 10.dp.toPx())
                        lineTo(center.x + 7.dp.toPx(), center.y - outerRadius - 10.dp.toPx())
                        close()
                    }
                    drawPath(
                        path = topBezelPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(accentCyan, accentCyan.copy(alpha = 0.6f)),
                            startY = center.y - outerRadius - 10.dp.toPx(),
                            endY = center.y - outerRadius + 2.dp.toPx()
                        )
                    )

                    // Top Pointer Glow Notch Dot
                    drawCircle(
                        color = Color.White,
                        radius = 2.5.dp.toPx(),
                        center = Offset(center.x, center.y - outerRadius - 4.dp.toPx())
                    )

                    // 7. Center Hub Pivot Cap with Chrome Layers
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color.White, accentCyan, textSecondary),
                            center = center,
                            radius = 12.dp.toPx()
                        ),
                        radius = 12.dp.toPx(),
                        center = center
                    )
                    drawCircle(
                        color = surfaceColor,
                        radius = 6.dp.toPx(),
                        center = center
                    )
                    drawCircle(
                        color = accentCyan,
                        radius = 3.dp.toPx(),
                        center = center
                    )
                }

                // Center Heading & Direction Display (Precision Typography)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "${deviceHeading.roundToInt()}°",
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = textPrimary,
                            letterSpacing = (-1).sp
                        ),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = getDirectionName(deviceHeading),
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = accentCyan,
                            fontSize = 13.sp
                        )
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "HEADING",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = textSecondary,
                            letterSpacing = 1.8.sp
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Premium Glassmorphism Bottom Panel
        val facingName = getDirectionName(deviceHeading)
        val windName = getDirectionName(windDegrees)
        val (bftLevel, bftDesc) = remember(windSpeedKmH) { getBeaufortInfo(windSpeedKmH) }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Heading & Wind Facing Glass Banner Pill
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = accentCyan.copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = SkySphereIcons.Compass,
                            contentDescription = null,
                            tint = accentCyan,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Facing $facingName • Wind coming from $windName",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = textPrimary,
                                fontSize = 11.5.sp
                            ),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 3 Glassmorphic Metrics Breakdown: Wind Speed, Wind From, Beaufort Scale
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Wind Speed Column
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = SkySphereIcons.Wind,
                                contentDescription = null,
                                tint = accentCyan,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "WIND SPEED",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = textSecondary,
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = formatWindValue(windSpeedKmH, windUnit),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = textPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        )
                    }

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(26.dp)
                            .background(textSecondary.copy(alpha = 0.2f))
                    )

                    // Wind From Column
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = SkySphereIcons.Compass,
                                contentDescription = null,
                                tint = accentSky,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "WIND FROM",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = textSecondary,
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "${getShortDirectionName(windDegrees)} (${windDegrees.toInt()}°)",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = accentCyan,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        )
                    }

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(26.dp)
                            .background(textSecondary.copy(alpha = 0.2f))
                    )

                    // Beaufort Scale Column
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = SkySphereIcons.Thermostat,
                                contentDescription = null,
                                tint = Color(0xFFFFB74D),
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "BEAUFORT",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = textSecondary,
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "Bft $bftLevel • $bftDesc",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = textPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        )
                    }
                }
            }
        }
    }
}
