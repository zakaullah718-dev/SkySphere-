package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.models.ForecastHour
import com.example.data.models.WeatherDetails
import com.example.ui.icons.SkySphereIcons
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Data model for an individual hourly humidity point in the 24-hour chart.
 */
data class HourlyHumidityData(
    val hourIndex: Int,
    val timeLabel: String,
    val humidityPercent: Int,
    val estimatedDewPointC: Int,
    val comfortLevel: String,
    val isCurrentHour: Boolean = false
)

/**
 * Calculates 24 hours of realistic humidity trend data based on current weather details and hourly forecast.
 */
fun generate24HourHumidityData(
    details: WeatherDetails,
    isCelsius: Boolean = true
): List<HourlyHumidityData> {
    val currentHumidity = details.humidity.coerceIn(10, 100)
    val currentTemp = details.currentTemp
    val hourlyList = details.hourlyForecast

    val result = mutableListOf<HourlyHumidityData>()

    // Generate 24 hourly data points
    for (i in 0 until 24) {
        val hourItem = hourlyList.getOrNull(i % hourlyList.size)
        val hourLabel = when {
            i == 0 -> "Now"
            hourItem != null -> hourItem.time
            else -> {
                val h = (i) % 24
                val amPm = if (h >= 12) "PM" else "AM"
                val displayH = if (h % 12 == 0) 12 else h % 12
                "$displayH $amPm"
            }
        }

        val tempForHour = hourItem?.temperature ?: (currentTemp + ((i - 12) * 0.5).roundToInt())
        val precipChance = hourItem?.precipitationChance ?: 0

        // Inverse diurnal relationship between temperature and relative humidity + rain factor
        val tempDiff = tempForHour - currentTemp
        val calculatedHumidity = (currentHumidity - (tempDiff * 1.5) + (precipChance * 0.2))
            .roundToInt()
            .coerceIn(15, 98)

        // Dew point estimation (Markus Magnusson formula approximation: Td ≈ T - ((100 - RH)/5))
        val dewPointC = tempForHour - ((100 - calculatedHumidity) / 5)

        val comfort = when {
            calculatedHumidity < 30 -> "Crisp & Dry"
            calculatedHumidity in 30..55 -> "Ideal Comfort"
            calculatedHumidity in 56..75 -> "Moderate Moisture"
            calculatedHumidity in 76..88 -> "Humid"
            else -> "Muggy & Tropical"
        }

        result.add(
            HourlyHumidityData(
                hourIndex = i,
                timeLabel = hourLabel,
                humidityPercent = calculatedHumidity,
                estimatedDewPointC = dewPointC,
                comfortLevel = comfort,
                isCurrentHour = (i == 0)
            )
        )
    }

    return result
}

/**
 * Flagship Interactive 24-Hour Spline Humidity Chart Component.
 */
@Composable
fun HumidityChartComponent(
    details: WeatherDetails,
    isCelsius: Boolean = true,
    modifier: Modifier = Modifier
) {
    val dataPoints = remember(details) { generate24HourHumidityData(details, isCelsius) }

    val currentHumidity = details.humidity
    val minHumidity = remember(dataPoints) { dataPoints.minOf { it.humidityPercent } }
    val maxHumidity = remember(dataPoints) { dataPoints.maxOf { it.humidityPercent } }
    val avgHumidity = remember(dataPoints) { dataPoints.map { it.humidityPercent }.average().roundToInt() }

    // Selected touch index (default to index 0 - "Now")
    var selectedIndex by remember { mutableStateOf(0) }
    var isTouching by remember { mutableStateOf(false) }

    val activePoint = dataPoints.getOrElse(selectedIndex.coerceIn(0, dataPoints.lastIndex)) { dataPoints[0] }

    // Color Palette
    val textPrimary = MaterialTheme.colorScheme.onSurface
    val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant
    val accentCyan = Color(0xFF38BDF8)
    val accentSky = Color(0xFF0288D1)
    val accentTeal = Color(0xFF2DD4BF)
    val accentIndigo = Color(0xFF818CF8)

    val textMeasurer = rememberTextMeasurer()

    SkySphereCard(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(20.dp)
    ) {
        // 1. Header Section
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(accentCyan.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = SkySphereIcons.Humidity,
                        contentDescription = "Humidity Icon",
                        tint = accentCyan,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column {
                    Text(
                        text = "24-HOUR HUMIDITY TREND",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp,
                            color = textSecondary
                        )
                    )
                    Text(
                        text = "Interactive Spline Curve",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = textPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        )
                    )
                }
            }

            // Current Humidity Pill
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = accentCyan.copy(alpha = 0.14f),
                border = androidx.compose.foundation.BorderStroke(1.dp, accentCyan.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "$currentHumidity%",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = accentCyan
                        )
                    )
                    Text(
                        text = "RH",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = accentCyan.copy(alpha = 0.8f),
                            fontSize = 10.sp
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 2. Interactive Floating Tooltip Bar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            border = androidx.compose.foundation.BorderStroke(1.dp, accentCyan.copy(alpha = 0.25f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Hour & Status
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = activePoint.timeLabel,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = accentCyan
                            )
                        )
                        if (activePoint.isCurrentHour) {
                            Surface(
                                shape = CircleShape,
                                color = accentCyan
                            ) {
                                Text(
                                    text = "NOW",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = Color.Black,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 8.sp
                                    ),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = activePoint.comfortLevel,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = textSecondary,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }

                // Humidity Value & Dew Point
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "HUMIDITY",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = textSecondary,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            text = "${activePoint.humidityPercent}%",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = textPrimary
                            )
                        )
                    }

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(24.dp)
                            .background(textSecondary.copy(alpha = 0.2f))
                    )

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "DEW POINT",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = textSecondary,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            text = "${activePoint.estimatedDewPointC}°${if (isCelsius) "C" else "F"}",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = accentTeal
                            )
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 3. Interactive Spline Canvas Chart
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp)
                .testTag("humidity_spline_canvas_box")
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(dataPoints) {
                        detectTapGestures { offset ->
                            val paddingLeft = 32.dp.toPx()
                            val paddingRight = 16.dp.toPx()
                            val chartWidth = size.width - paddingLeft - paddingRight
                            val stepX = chartWidth / (dataPoints.size - 1)

                            val touchX = (offset.x - paddingLeft).coerceIn(0f, chartWidth)
                            val index = (touchX / stepX).roundToInt().coerceIn(0, dataPoints.lastIndex)
                            selectedIndex = index
                            isTouching = true
                        }
                    }
                    .pointerInput(dataPoints) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                isTouching = true
                            },
                            onDragEnd = {
                                isTouching = false
                            },
                            onDragCancel = {
                                isTouching = false
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                val paddingLeft = 32.dp.toPx()
                                val paddingRight = 16.dp.toPx()
                                val chartWidth = size.width - paddingLeft - paddingRight
                                val stepX = chartWidth / (dataPoints.size - 1)

                                val touchX = (change.position.x - paddingLeft).coerceIn(0f, chartWidth)
                                val index = (touchX / stepX).roundToInt().coerceIn(0, dataPoints.lastIndex)
                                selectedIndex = index
                            }
                        )
                    }
            ) {
                val width = size.width
                val height = size.height

                val paddingLeft = 36.dp.toPx()
                val paddingRight = 16.dp.toPx()
                val paddingTop = 20.dp.toPx()
                val paddingBottom = 32.dp.toPx()

                val chartWidth = width - paddingLeft - paddingRight
                val chartHeight = height - paddingTop - paddingBottom

                // Grid Lines at 0%, 25%, 50%, 75%, 100%
                val gridLevels = listOf(100, 75, 50, 25, 0)
                gridLevels.forEach { level ->
                    val y = paddingTop + chartHeight * (1f - level / 100f)

                    // Line
                    drawLine(
                        color = textSecondary.copy(alpha = 0.12f),
                        start = Offset(paddingLeft, y),
                        end = Offset(width - paddingRight, y),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                    )

                    // Y-Axis Percentage Label
                    val textStyle = TextStyle(
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium,
                        color = textSecondary.copy(alpha = 0.65f)
                    )
                    val labelResult = textMeasurer.measure("$level%", textStyle)
                    drawText(
                        textLayoutResult = labelResult,
                        topLeft = Offset(paddingLeft - labelResult.size.width - 6.dp.toPx(), y - (labelResult.size.height / 2f))
                    )
                }

                // Data Point Coordinates Mapping
                val stepX = chartWidth / (dataPoints.size - 1)
                val points = dataPoints.mapIndexed { idx, item ->
                    val x = paddingLeft + idx * stepX
                    // Scale Y between min and max with padding for visual clarity
                    val yNorm = (item.humidityPercent - 0f) / 100f // 0 to 100% full scale
                    val y = paddingTop + chartHeight * (1f - yNorm.coerceIn(0f, 1f))
                    Offset(x, y)
                }

                if (points.size >= 2) {
                    // Smooth Monotone Cubic Bezier Spline Path
                    val splinePath = Path()
                    val fillPath = Path()

                    splinePath.moveTo(points[0].x, points[0].y)
                    fillPath.moveTo(points[0].x, points[0].y)

                    for (i in 0 until points.size - 1) {
                        val p0 = points[if (i > 0) i - 1 else i]
                        val p1 = points[i]
                        val p2 = points[i + 1]
                        val p3 = points[if (i + 2 < points.size) i + 2 else i + 1]

                        val cp1X = p1.x + (p2.x - p0.x) * 0.18f
                        val cp1Y = p1.y + (p2.y - p0.y) * 0.18f

                        val cp2X = p2.x - (p3.x - p1.x) * 0.18f
                        val cp2Y = p2.y - (p3.y - p1.y) * 0.18f

                        splinePath.cubicTo(cp1X, cp1Y, cp2X, cp2Y, p2.x, p2.y)
                        fillPath.cubicTo(cp1X, cp1Y, cp2X, cp2Y, p2.x, p2.y)
                    }

                    // Complete Area Fill Path down to baseline
                    fillPath.lineTo(points.last().x, paddingTop + chartHeight)
                    fillPath.lineTo(points.first().x, paddingTop + chartHeight)
                    fillPath.close()

                    // Draw Gradient Fill under Spline
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                accentCyan.copy(alpha = 0.38f),
                                accentTeal.copy(alpha = 0.18f),
                                Color.Transparent
                            ),
                            startY = paddingTop,
                            endY = paddingTop + chartHeight
                        )
                    )

                    // Draw Glowing Spline Curve Line
                    drawPath(
                        path = splinePath,
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                accentCyan,
                                accentTeal,
                                accentIndigo,
                                accentCyan
                            )
                        ),
                        style = Stroke(
                            width = 3.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )

                    // Draw Subtle Spline Node Dots for Key Hours
                    points.forEachIndexed { idx, pt ->
                        if (idx % 3 == 0 || idx == selectedIndex) {
                            val isSelected = (idx == selectedIndex)
                            drawCircle(
                                color = if (isSelected) Color.White else accentCyan,
                                radius = if (isSelected) 5.dp.toPx() else 2.5.dp.toPx(),
                                center = pt
                            )
                            if (isSelected) {
                                drawCircle(
                                    color = accentCyan,
                                    radius = 8.dp.toPx(),
                                    center = pt,
                                    style = Stroke(width = 2.dp.toPx())
                                )
                            }
                        }
                    }

                    // Interactive Crosshair & Highlight for Selected Point
                    val activePt = points[selectedIndex.coerceIn(0, points.lastIndex)]

                    // Vertical Dotted Scrub Line
                    drawLine(
                        color = accentCyan.copy(alpha = 0.85f),
                        start = Offset(activePt.x, paddingTop),
                        end = Offset(activePt.x, paddingTop + chartHeight),
                        strokeWidth = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
                    )

                    // Glowing Pulsing Touch Node
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(accentCyan.copy(alpha = 0.6f), Color.Transparent),
                            center = activePt,
                            radius = 18.dp.toPx()
                        ),
                        radius = 18.dp.toPx(),
                        center = activePt
                    )

                    drawCircle(
                        color = Color.White,
                        radius = 6.dp.toPx(),
                        center = activePt
                    )

                    drawCircle(
                        color = accentCyan,
                        radius = 3.5.dp.toPx(),
                        center = activePt
                    )

                    // Draw X-Axis Time Labels (Every 3 hours)
                    for (i in dataPoints.indices step 3) {
                        val pt = points[i]
                        val label = dataPoints[i].timeLabel
                        val labelStyle = TextStyle(
                            fontSize = 9.5.sp,
                            fontWeight = if (i == selectedIndex) FontWeight.Bold else FontWeight.Medium,
                            color = if (i == selectedIndex) accentCyan else textSecondary
                        )
                        val textLayout = textMeasurer.measure(label, labelStyle)
                        drawText(
                            textLayoutResult = textLayout,
                            topLeft = Offset(pt.x - (textLayout.size.width / 2f), paddingTop + chartHeight + 8.dp.toPx())
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 4. Summary Metrics Footer Bar (Min, Max, Avg 24h Humidity)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = textSecondary.copy(alpha = 0.06f),
                    shape = RoundedCornerShape(14.dp)
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            HumidityStatPill(
                label = "24H LOW",
                value = "$minHumidity%",
                color = accentTeal,
                modifier = Modifier.weight(1f)
            )

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(22.dp)
                    .background(textSecondary.copy(alpha = 0.15f))
            )

            HumidityStatPill(
                label = "24H AVERAGE",
                value = "$avgHumidity%",
                color = accentCyan,
                modifier = Modifier.weight(1f)
            )

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(22.dp)
                    .background(textSecondary.copy(alpha = 0.15f))
            )

            HumidityStatPill(
                label = "24H PEAK",
                value = "$maxHumidity%",
                color = Color(0xFFFB923C),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun HumidityStatPill(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
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
                color = color,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        )
    }
}
