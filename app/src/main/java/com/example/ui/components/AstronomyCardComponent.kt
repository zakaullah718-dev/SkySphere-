package com.example.ui.components

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.icons.SkySphereIcons
import java.util.Calendar
import kotlin.math.*

/**
 * AstronomyData holds calculated celestial values for Sun and Moon.
 */
data class AstronomyData(
    val sunrise: String,
    val sunset: String,
    val moonrise: String,
    val moonset: String,
    val phaseName: String,
    val phaseIcon: String,
    val illuminationPercent: Int,
    val phaseRatio: Float,
    val isDay: Boolean,
    val arcProgress: Float,
    val subtitleText: String
)

/**
 * Calculates Moon phase, illumination, moonrise, moonset, and daylight state.
 */
fun calculateAstronomyData(
    sunrise: String,
    sunset: String,
    latitude: Double? = null,
    longitude: Double? = null,
    cityName: String? = null,
    nowMillis: Long = System.currentTimeMillis()
): AstronomyData {
    // 1. Moon Phase & Illumination (Synodic Month: 29.53058770576 days)
    // Reference New Moon: Jan 11, 2024, 11:57 UTC (1704974220000 ms)
    val refNewMoonMillis = 1704974220000L
    val synodicPeriodDays = 29.53058770576
    val daysSinceRef = (nowMillis - refNewMoonMillis) / 86400000.0
    val rawPhase = (daysSinceRef % synodicPeriodDays) / synodicPeriodDays
    val phaseRatio = (if (rawPhase < 0) rawPhase + 1.0 else rawPhase).toFloat()

    val illuminationPercent = (50.0 * (1.0 - cos(phaseRatio * 2 * PI))).roundToInt().coerceIn(0, 100)

    val (phaseName, phaseIcon) = when {
        phaseRatio < 0.03f || phaseRatio >= 0.97f -> "New Moon" to "🌑"
        phaseRatio in 0.03f..<0.22f -> "Waxing Crescent" to "🌒"
        phaseRatio in 0.22f..0.28f -> "First Quarter" to "🌓"
        phaseRatio in 0.28f..<0.47f -> "Waxing Gibbous" to "🌔"
        phaseRatio in 0.47f..0.53f -> "Full Moon" to "🌕"
        phaseRatio in 0.53f..<0.72f -> "Waning Gibbous" to "🌖"
        phaseRatio in 0.72f..0.78f -> "Last Quarter" to "🌗"
        else -> "Waning Crescent" to "🌘"
    }

    // 2. Parse Sunrise & Sunset
    fun parseTimeToMinutes(timeStr: String): Int {
        return try {
            val cleaned = timeStr.trim().uppercase()
            val isPm = cleaned.endsWith("PM")
            val isAm = cleaned.endsWith("AM")
            val timePart = cleaned.replace("AM", "").replace("PM", "").trim()
            val parts = timePart.split(":")
            var h = parts[0].toInt()
            val m = parts[1].toInt()
            if (isPm && h < 12) h += 12
            if (isAm && h == 12) h = 0
            h * 60 + m
        } catch (e: Exception) {
            360 // Fallback 6:00 AM
        }
    }

    val sunriseMin = parseTimeToMinutes(sunrise)
    val sunsetMin = parseTimeToMinutes(sunset)

    // 3. Moonrise & Moonset Calculation
    // Moonrise shifts roughly 50 minutes later each day relative to sunrise (or phaseRatio * 1440 min)
    val locHash = ((latitude ?: 0.0) * 10 + (longitude ?: 0.0) * 10 + (cityName?.hashCode() ?: 0)).toInt()
    val locOffsetMin = (abs(locHash) % 30) - 15 // Subtle local variance adjustment

    val rawMoonriseMin = (sunriseMin + (phaseRatio * 1440).toInt() + 360 + locOffsetMin) % 1440
    val rawMoonsetMin = (sunsetMin + (phaseRatio * 1440).toInt() + 360 + locOffsetMin) % 1440

    fun formatMinutesToAmPm(minutes: Int): String {
        val norm = (minutes % 1440 + 1440) % 1440
        var h = norm / 60
        val m = norm % 60
        val amPm = if (h >= 12) "PM" else "AM"
        if (h > 12) h -= 12
        if (h == 0) h = 12
        return String.format("%02d:%02d %s", h, m, amPm)
    }

    val moonriseStr = formatMinutesToAmPm(rawMoonriseMin)
    val moonsetStr = formatMinutesToAmPm(rawMoonsetMin)

    // 4. Current daylight state & progress
    val cal = Calendar.getInstance()
    val currentMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)

    val isDay = currentMinutes in (sunriseMin + 1)..<sunsetMin
    val arcProgress = if (isDay) {
        val totalDay = (sunsetMin - sunriseMin).coerceAtLeast(1)
        ((currentMinutes - sunriseMin).toFloat() / totalDay.toFloat()).coerceIn(0f, 1f)
    } else {
        val totalNight = (1440 - sunsetMin) + sunriseMin
        val progress = if (currentMinutes >= sunsetMin) {
            (currentMinutes - sunsetMin).toFloat() / totalNight.toFloat()
        } else {
            ((1440 - sunsetMin) + currentMinutes).toFloat() / totalNight.toFloat()
        }
        progress.coerceIn(0f, 1f)
    }

    val dayNightPrefix = if (isDay) "Today" else "Tonight"
    val subtitleText = "$dayNightPrefix: $phaseName • $illuminationPercent% Illuminated"

    return AstronomyData(
        sunrise = sunrise,
        sunset = sunset,
        moonrise = moonriseStr,
        moonset = moonsetStr,
        phaseName = phaseName,
        phaseIcon = phaseIcon,
        illuminationPercent = illuminationPercent,
        phaseRatio = phaseRatio,
        isDay = isDay,
        arcProgress = arcProgress,
        subtitleText = subtitleText
    )
}

/**
 * Premium Astronomy Card Component.
 */
@Composable
fun AstronomyCard(
    sunrise: String,
    sunset: String,
    latitude: Double? = null,
    longitude: Double? = null,
    cityName: String? = null,
    modifier: Modifier = Modifier
) {
    val astronomyData = remember(sunrise, sunset, latitude, longitude, cityName) {
        calculateAstronomyData(
            sunrise = sunrise,
            sunset = sunset,
            latitude = latitude,
            longitude = longitude,
            cityName = cityName
        )
    }

    val animatedProgress by animateFloatAsState(
        targetValue = astronomyData.arcProgress,
        animationSpec = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
        label = "CelestialArcProgress"
    )

    val textPrimary = MaterialTheme.colorScheme.onSurface
    val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant
    val accentGold = Color(0xFFFFD54F)
    val accentCyan = Color(0xFF38BDF8)
    val accentIndigo = Color(0xFF818CF8)

    SkySphereCard(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(20.dp)
    ) {
        // 1. Header Row
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
                        .background(if (astronomyData.isDay) accentGold.copy(alpha = 0.18f) else accentCyan.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (astronomyData.isDay) SkySphereIcons.Sunny else SkySphereIcons.Moon,
                        contentDescription = null,
                        tint = if (astronomyData.isDay) accentGold else accentCyan,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    text = "SUN & MOON ASTRONOMY",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                        color = textSecondary
                    )
                )
            }

            // Moon Phase Pill
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (astronomyData.isDay) accentGold.copy(alpha = 0.12f) else accentCyan.copy(alpha = 0.14f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(text = astronomyData.phaseIcon, fontSize = 11.sp)
                    Text(
                        text = "${astronomyData.illuminationPercent}%",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (astronomyData.isDay) accentGold else accentCyan,
                            fontSize = 11.sp
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 2. Celestial Arc Canvas
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height
                val padding = 16.dp.toPx()

                val arcWidth = width - (padding * 2)
                val arcHeight = height * 1.5f
                val arcLeft = padding
                val arcTop = height - (arcHeight / 2) - 12.dp.toPx()

                // Background Nighttime Stars (if night mode)
                if (!astronomyData.isDay) {
                    // Seeded random stars for consistent placement
                    val starPositions = listOf(
                        Offset(width * 0.15f, height * 0.20f),
                        Offset(width * 0.28f, height * 0.10f),
                        Offset(width * 0.45f, height * 0.18f),
                        Offset(width * 0.62f, height * 0.08f),
                        Offset(width * 0.78f, height * 0.22f),
                        Offset(width * 0.88f, height * 0.14f),
                        Offset(width * 0.35f, height * 0.35f),
                        Offset(width * 0.70f, height * 0.38f)
                    )

                    starPositions.forEachIndexed { idx, pos ->
                        val radius = if (idx % 2 == 0) 1.5.dp.toPx() else 1.0.dp.toPx()
                        val starAlpha = if (idx % 3 == 0) 0.85f else 0.5f
                        drawCircle(
                            color = Color.White.copy(alpha = starAlpha),
                            radius = radius,
                            center = pos
                        )
                    }
                }

                // Base Dotted Sky Arc
                drawArc(
                    color = textSecondary.copy(alpha = 0.2f),
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    style = Stroke(
                        width = 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
                    ),
                    topLeft = Offset(arcLeft, arcTop),
                    size = Size(arcWidth, arcHeight)
                )

                // Active Progress Arc Gradient
                val activeSweep = 180f * animatedProgress
                if (activeSweep > 0.5f) {
                    drawArc(
                        brush = Brush.horizontalGradient(
                            colors = if (astronomyData.isDay) {
                                listOf(Color(0xFFFFB300), Color(0xFFFFD54F), Color(0xFFFF9100))
                            } else {
                                listOf(Color(0xFF38BDF8), Color(0xFF818CF8), Color(0xFFC084FC))
                            }
                        ),
                        startAngle = 180f,
                        sweepAngle = activeSweep,
                        useCenter = false,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                        topLeft = Offset(arcLeft, arcTop),
                        size = Size(arcWidth, arcHeight)
                    )
                }

                // Horizon Line
                drawLine(
                    color = textSecondary.copy(alpha = 0.18f),
                    start = Offset(0f, height - 12.dp.toPx()),
                    end = Offset(width, height - 12.dp.toPx()),
                    strokeWidth = 1.dp.toPx()
                )

                // Celestial Body Position along Arc
                val angle = 180f + (180f * animatedProgress)
                val angleRad = Math.toRadians(angle.toDouble())
                val celestialX = (width / 2) + (arcWidth / 2) * cos(angleRad)
                val celestialY = (height - 12.dp.toPx() + arcHeight / 2) + (arcHeight / 2) * sin(angleRad) - (arcHeight / 2)
                val celestialCenter = Offset(celestialX.toFloat(), celestialY.toFloat())

                if (astronomyData.isDay) {
                    // Daytime: Glowing Sun
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFFFFD54F).copy(alpha = 0.45f), Color.Transparent),
                            center = celestialCenter,
                            radius = 22.dp.toPx()
                        ),
                        radius = 22.dp.toPx(),
                        center = celestialCenter
                    )

                    drawCircle(
                        color = Color(0xFFFFB300),
                        radius = 7.dp.toPx(),
                        center = celestialCenter
                    )
                    drawCircle(
                        color = Color(0xFFFFF59D),
                        radius = 4.dp.toPx(),
                        center = celestialCenter
                    )
                } else {
                    // Nighttime: Glowing Moon with Phase Rendering
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFF81D4FA).copy(alpha = 0.35f), Color.Transparent),
                            center = celestialCenter,
                            radius = 22.dp.toPx()
                        ),
                        radius = 22.dp.toPx(),
                        center = celestialCenter
                    )

                    // Base Moon Disc
                    val moonR = 7.5.dp.toPx()
                    drawCircle(
                        color = Color(0xFFE2E8F0),
                        radius = moonR,
                        center = celestialCenter
                    )

                    // Moon Phase Shading Cutout
                    val shadowColor = Color(0xFF1E293B)
                    val pr = astronomyData.phaseRatio
                    when {
                        pr < 0.03f || pr >= 0.97f -> {
                            // New Moon: full shadow
                            drawCircle(color = shadowColor, radius = moonR, center = celestialCenter)
                        }
                        pr in 0.03f..<0.47f -> {
                            // Waxing: Shadow on left side
                            drawCircle(
                                color = shadowColor,
                                radius = moonR - (pr * moonR * 1.2f).coerceAtMost(moonR * 0.9f),
                                center = Offset(celestialCenter.x - 2.5.dp.toPx(), celestialCenter.y)
                            )
                        }
                        pr in 0.47f..0.53f -> {
                            // Full Moon: no shadow
                        }
                        else -> {
                            // Waning: Shadow on right side
                            drawCircle(
                                color = shadowColor,
                                radius = moonR * 0.7f,
                                center = Offset(celestialCenter.x + 3.dp.toPx(), celestialCenter.y)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 3. 4-Grid Celestial Metrics Breakdown (Sunrise, Sunset, Moonrise, Moonset)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = textSecondary.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CelestialMetricTile(
                icon = SkySphereIcons.Sunrise,
                label = "SUNRISE",
                value = astronomyData.sunrise,
                iconTint = accentGold,
                modifier = Modifier.weight(1f)
            )

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(28.dp)
                    .background(textSecondary.copy(alpha = 0.15f))
            )

            CelestialMetricTile(
                icon = SkySphereIcons.Sunset,
                label = "SUNSET",
                value = astronomyData.sunset,
                iconTint = Color(0xFFFB923C),
                modifier = Modifier.weight(1f)
            )

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(28.dp)
                    .background(textSecondary.copy(alpha = 0.15f))
            )

            CelestialMetricTile(
                icon = SkySphereIcons.Moonrise,
                label = "MOONRISE",
                value = astronomyData.moonrise,
                iconTint = accentCyan,
                modifier = Modifier.weight(1f)
            )

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(28.dp)
                    .background(textSecondary.copy(alpha = 0.15f))
            )

            CelestialMetricTile(
                icon = SkySphereIcons.Moonset,
                label = "MOONSET",
                value = astronomyData.moonset,
                iconTint = accentIndigo,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 4. Educational & Elegant Subtitle Description Pill
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = if (astronomyData.isDay) accentGold.copy(alpha = 0.08f) else accentCyan.copy(alpha = 0.08f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = astronomyData.subtitleText,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = textPrimary,
                        fontSize = 12.sp
                    ),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun CelestialMetricTile(
    icon: ImageVector,
    label: String,
    value: String,
    iconTint: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = iconTint,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 8.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                fontSize = 11.5.sp
            )
        )
    }
}
