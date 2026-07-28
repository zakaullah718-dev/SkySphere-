package com.example.ui.components

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.LuxuryCyan
import com.example.ui.theme.LuxurySkyBlue
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

@Composable
fun WindGaugeCard(
    windSpeedKmH: Double,
    windDirection: String,
    windUnit: String,
    modifier: Modifier = Modifier
) {
    var isInitialized by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        isInitialized = true
    }

    val targetDegrees = remember(windDirection) { parseWindDirectionDegrees(windDirection) }
    val animatedAngle by animateFloatAsState(
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

    val (beaufortLevel, beaufortDesc) = remember(windSpeedKmH) { getBeaufortInfo(windSpeedKmH) }

    val isDark = isSystemInDarkTheme()
    val cardBg = if (isDark) Color(0xFF1B2133) else Color(0xFFFFFFFF)
    val glassBorderBrush = Brush.linearGradient(
        colors = listOf(
            Color(0x48FFFFFF),
            Color(0x2038BDF8),
            Color(0x18FFFFFF)
        )
    )
    val cardShape = RoundedCornerShape(24.dp)
    val textPrimary = MaterialTheme.colorScheme.onBackground
    val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant
    val trackBg = if (isDark) Color(0xFF1E2742) else Color(0xFFE2E8F0)
    val accentCyan = LuxuryCyan
    val accentSky = LuxurySkyBlue

    val textMeasurer = rememberTextMeasurer()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 8.dp,
                shape = cardShape,
                clip = false,
                ambientColor = Color(0x35000000),
                spotColor = Color(0x50000000)
            )
            .border(BorderStroke(1.dp, glassBorderBrush), shape = cardShape)
            .graphicsLayer {
                alpha = cardAlpha
                scaleX = cardScale
                scaleY = cardScale
                translationY = cardTranslationY
            },
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = cardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
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
                            imageVector = Icons.Filled.Air,
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
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Gauge Body
            Box(
                modifier = Modifier
                    .size(210.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val radius = min(size.width, size.height) / 2f - 18.dp.toPx()
                    val strokeWidth = 8.dp.toPx()

                    // Background Track Arc (270 degrees arc from 135 deg to 405 deg)
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

                        val isMainCardinal = i % 4 == 0 // N, E, S, W
                        val isSubCardinal = i % 2 == 0  // NE, SE, SW, NW

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

                        // Draw Cardinal Text for N, E, S, W
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

                    // Rotating Arrow Pointer at Animated Direction Angle
                    val pointerAngleRad = Math.toRadians((animatedAngle - 90.0)).toDouble()
                    val pointerDistance = radius - 38.dp.toPx()
                    val pointerCenter = Offset(
                        x = (center.x + pointerDistance * cos(pointerAngleRad)).toFloat(),
                        y = (center.y + pointerDistance * sin(pointerAngleRad)).toFloat()
                    )

                    rotate(degrees = animatedAngle, pivot = pointerCenter) {
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

                // Center Information Display
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Wind Icon & Direction Pointer
                    Icon(
                        imageVector = Icons.Filled.Navigation,
                        contentDescription = "Wind Direction",
                        tint = accentCyan,
                        modifier = Modifier
                            .size(24.dp)
                            .rotate(animatedAngle)
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
}
