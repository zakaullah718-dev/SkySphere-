package com.example.ui.components

import com.example.utils.WeatherTimeUtils

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.InfiniteTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import com.example.data.models.WeatherCondition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A highly polished, luxury card for SkySphere that displays content
 * with a premium Glassmorphism effect: semi-transparent gradients,
 * high-contrast reflective borders, subtle elevation shadows, and rounded corners.
 * Includes smooth 60 FPS press lift & glow micro-interactions.
 */
@Composable
fun SkySphereCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    borderWidth: Dp = 1.dp,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val cardShape = RoundedCornerShape(24.dp)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "CardTouchScale"
    )

    val elevation by animateDpAsState(
        targetValue = if (isPressed) 14.dp else 8.dp,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "CardTouchElevation"
    )

    val glowAlpha by animateFloatAsState(
        targetValue = if (isPressed) 0.5f else 0.25f,
        animationSpec = tween(200),
        label = "CardGlowAlpha"
    )

    val surfaceColor = MaterialTheme.colorScheme.surface
    val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant
    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface

    // Glassmorphism background gradient based on active theme
    val bgBrush = Brush.verticalGradient(
        colors = listOf(
            surfaceColor,
            surfaceVariantColor
        )
    )

    // Glass reflective specular border gradient
    val glassBorderBrush = Brush.linearGradient(
        colors = listOf(
            onSurfaceColor.copy(alpha = glowAlpha),
            primaryColor.copy(alpha = glowAlpha + 0.15f),
            onSurfaceColor.copy(alpha = glowAlpha * 0.4f)
        )
    )

    val clickModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = elevation,
                shape = cardShape,
                clip = false,
                ambientColor = Color(0x35000000),
                spotColor = Color(0x50000000)
            )
            .clip(cardShape)
            .background(bgBrush)
            .border(borderWidth, glassBorderBrush, cardShape)
            .then(clickModifier)
            .padding(contentPadding)
    ) {
        Column {
            content()
        }
    }
}

/**
 * A premium pill-shaped gradient button with spring press motion.
 */
@Composable
fun SkySphereButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    testTag: String = "skysphere_button"
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "ButtonPressScale"
    )

    val gradient = Brush.linearGradient(
        colors = listOf(
            Color(0xFF2FA3FF), // Vibrant Sky Blue
            Color(0xFF00C6FF)  // Glowing Cyan
        )
    )

    Button(
        onClick = onClick,
        interactionSource = interactionSource,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent, // Managed via gradient modifier
            contentColor = Color.White
        ),
        contentPadding = PaddingValues(), // Disable padding so modifier handles it
        shape = CircleShape,
        modifier = modifier
            .height(54.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
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
 * A minimalist, modern icon button with soft scale press interaction.
 */
@Composable
fun SkySphereIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    testTag: String = "skysphere_icon_button"
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "IconButtonPressScale"
    )

    val borderColor = Color(0xFF374151) // Highly visible border
    
    IconButton(
        onClick = onClick,
        interactionSource = interactionSource,
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = Color(0xFF1E1E2E) // Solid accessible background
        ),
        modifier = modifier
            .size(48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
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
 * Shimmer gradient brush for skeleton placeholders.
 */
@Composable
fun ShimmerBrush(
    targetValue: Float = 1000f
): Brush {
    val shimmerColors = listOf(
        Color.White.copy(alpha = 0.05f),
        Color.White.copy(alpha = 0.20f),
        Color.White.copy(alpha = 0.05f),
    )

    val transition = rememberInfiniteTransition(label = "ShimmerTransition")
    val translateAnimation by transition.animateFloat(
        initialValue = 0f,
        targetValue = targetValue,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ShimmerTranslate"
    )

    return Brush.linearGradient(
        colors = shimmerColors,
        start = Offset.Zero,
        end = Offset(x = translateAnimation, y = translateAnimation)
    )
}

/**
 * Premium skeleton component for smooth shimmer loading states.
 */
@Composable
fun SkySphereSkeleton(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp)
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(Color.White.copy(alpha = 0.08f))
            .background(ShimmerBrush())
    )
}

/**
 * Skeleton loader representing a weather card.
 */
@Composable
fun SkySphereWeatherSkeleton(
    modifier: Modifier = Modifier
) {
    SkySphereCard(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    SkySphereSkeleton(modifier = Modifier.size(width = 140.dp, height = 24.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    SkySphereSkeleton(modifier = Modifier.size(width = 90.dp, height = 16.dp))
                }
                SkySphereSkeleton(
                    modifier = Modifier.size(54.dp),
                    shape = CircleShape
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                verticalAlignment = Alignment.Bottom
            ) {
                SkySphereSkeleton(modifier = Modifier.size(width = 100.dp, height = 56.dp))
                Spacer(modifier = Modifier.width(16.dp))
                SkySphereSkeleton(modifier = Modifier.size(width = 120.dp, height = 20.dp))
            }
            Spacer(modifier = Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
            ) {
                repeat(4) {
                    SkySphereSkeleton(modifier = Modifier.size(width = 65.dp, height = 40.dp))
                }
            }
        }
    }
}

/**
 * Animated count-up temperature text composable.
 */
@Composable
fun AnimatedTemperatureText(
    temperature: Int,
    unitSymbol: String = "°",
    style: TextStyle = MaterialTheme.typography.displayLarge,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified
) {
    val animatedTemp by animateIntAsState(
        targetValue = temperature,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "TempCountUpAnimation"
    )
    Text(
        text = "$animatedTemp$unitSymbol",
        style = style,
        color = color,
        modifier = modifier
    )
}

/**
 * Satisfying animated heart favorite toggle button.
 */
@Composable
fun AnimatedHeartButton(
    isFavorite: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = "animated_heart_button"
) {
    val scale by animateFloatAsState(
        targetValue = if (isFavorite) 1.25f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioHighBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "HeartScale"
    )

    IconButton(
        onClick = onToggle,
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .testTag(testTag)
    ) {
        Icon(
            imageVector = com.example.ui.icons.SkySphereIcons.Favorite,
            contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
            tint = if (isFavorite) Color(0xFFFF5252) else Color(0xFF94A3B8),
            modifier = Modifier.size(26.dp)
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
 * Parses sunrise and sunset string times to decide if it's currently day or night at location.
 */
fun isDayTime(
    sunrise: String,
    sunset: String,
    timeZoneId: String? = null,
    timestampEpochMillis: Long = 0L
): Boolean {
    return WeatherTimeUtils.isDayTimeForLocation(
        timestampEpochMillis = timestampEpochMillis,
        timeZoneId = timeZoneId,
        sunriseStr = sunrise,
        sunsetStr = sunset
    )
}

private data class WeatherGradientPalette(
    val c1: Color,
    val c2: Color,
    val c3: Color,
    val orb: Color
)

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

    val targetPalette = when (backgroundType) {
        "night" -> WeatherGradientPalette(
            c1 = Color(0xFF030712), // Obsidian night
            c2 = Color(0xFF0F172A), // Midnight slate
            c3 = Color(0xFF1E1B4B), // Deep indigo
            orb = Color(0xFF312E81)  // Starlight orb
        )
        "fog", "mist" -> WeatherGradientPalette(
            c1 = Color(0xFF1E293B),
            c2 = Color(0xFF334155),
            c3 = Color(0xFF0F172A),
            orb = Color(0xFF64748B)
        )
        "haze" -> WeatherGradientPalette(
            c1 = Color(0xFF27272A),
            c2 = Color(0xFF3F3F46),
            c3 = Color(0xFF18181B),
            orb = Color(0xFF71717A)
        )
        "heavy_rain" -> WeatherGradientPalette(
            c1 = Color(0xFF08101D), // Dark storm ocean
            c2 = Color(0xFF132A4A), // Deep rain navy
            c3 = Color(0xFF050B14), // Pitch night
            orb = Color(0xFF0369A1)  // Ocean storm orb
        )
        "rain" -> WeatherGradientPalette(
            c1 = Color(0xFF0B192C), // Abyssal rain blue
            c2 = Color(0xFF1E3E62), // Rainstorm slate
            c3 = Color(0xFF08121E), // Deep night
            orb = Color(0xFF0284C7)  // Cerulean rain orb
        )
        "thunderstorm" -> WeatherGradientPalette(
            c1 = Color(0xFF190628), // Electric night violet
            c2 = Color(0xFF0F172A), // Storm navy
            c3 = Color(0xFF0B0A1A), // Midnight ink
            orb = Color(0xFF7C3AED)  // Lightning purple orb
        )
        "snow" -> WeatherGradientPalette(
            c1 = Color(0xFF0F1C2E), // Frost navy
            c2 = Color(0xFF1E293B), // Glacial slate
            c3 = Color(0xFF0B132B), // Arctic night
            orb = Color(0xFF38BDF8)  // Icy blue orb
        )
        "cloudy" -> WeatherGradientPalette(
            c1 = Color(0xFF1E293B), // Slate dusk
            c2 = Color(0xFF334155), // Overcast steel
            c3 = Color(0xFF0F172A), // Deep gray
            orb = Color(0xFF64748B)  // Cloud orb
        )
        "partly_cloudy" -> WeatherGradientPalette(
            c1 = Color(0xFF0B2545), // Sky deep blue
            c2 = Color(0xFF134074), // Ocean slate
            c3 = Color(0xFF0B132B), // Midnight sky
            orb = Color(0xFF38BDF8)  // Azure sky orb
        )
        "sunny" -> WeatherGradientPalette(
            c1 = Color(0xFF2C1500), // Warm amber sunset
            c2 = Color(0xFF1E293B), // Slate blue transition
            c3 = Color(0xFF0F172A), // Deep twilight steel
            orb = Color(0xFFD97706)  // Golden sunburst orb
        )
        else -> WeatherGradientPalette(
            c1 = Color(0xFF0F2C40),
            c2 = Color(0xFF152238),
            c3 = Color(0xFF0B1220),
            orb = Color(0xFF0EA5E9)
        )
    }

    // Smooth color animation when switching weather conditions
    val animC1 by animateColorAsState(
        targetValue = targetPalette.c1,
        animationSpec = tween(1500, easing = LinearOutSlowInEasing),
        label = "BgColor1Anim"
    )
    val animC2 by animateColorAsState(
        targetValue = targetPalette.c2,
        animationSpec = tween(1500, easing = LinearOutSlowInEasing),
        label = "BgColor2Anim"
    )
    val animC3 by animateColorAsState(
        targetValue = targetPalette.c3,
        animationSpec = tween(1500, easing = LinearOutSlowInEasing),
        label = "BgColor3Anim"
    )
    val animOrb by animateColorAsState(
        targetValue = targetPalette.orb,
        animationSpec = tween(1500, easing = LinearOutSlowInEasing),
        label = "BgOrbAnim"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "BackgroundAnimation")
    
    // Continuous fluid motion angle
    val timeProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * kotlin.math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(22000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "SkyMeshRotation"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                val sinVal = kotlin.math.sin(timeProgress.toDouble()).toFloat()
                val cosVal = kotlin.math.cos(timeProgress.toDouble()).toFloat()

                // Calculate shifting linear gradient coordinates
                val startX = size.width * (0.2f + 0.25f * sinVal)
                val startY = size.height * (0.1f + 0.15f * cosVal)
                val endX = size.width * (0.8f - 0.25f * sinVal)
                val endY = size.height * (0.9f - 0.15f * cosVal)

                val baseBrush = Brush.linearGradient(
                    colors = listOf(animC1, animC2, animC3),
                    start = Offset(startX, startY),
                    end = Offset(endX, endY)
                )
                drawRect(brush = baseBrush)

                // Draw floating dynamic atmospheric glowing orb for depth
                val orbCenterX = size.width * (0.55f + 0.3f * cosVal)
                val orbCenterY = size.height * (0.22f + 0.15f * sinVal)
                val orbRadius = size.width * (0.75f + 0.1f * sinVal)

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            animOrb.copy(alpha = 0.35f),
                            animOrb.copy(alpha = 0.10f),
                            Color.Transparent
                        ),
                        center = Offset(orbCenterX, orbCenterY),
                        radius = orbRadius
                    ),
                    center = Offset(orbCenterX, orbCenterY),
                    radius = orbRadius
                )
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
        targetValue = 0.15f,
        animationSpec = infiniteRepeatable(tween(2500, easing = LinearEasing), RepeatMode.Reverse),
        label = "StarAlpha2"
    )
    val shootingStarProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(7000, easing = LinearEasing), RepeatMode.Restart),
        label = "ShootingStar"
    )

    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        if (width > 0 && height > 0) {
            val stars1 = listOf(
                Pair(0.12f, 0.15f), Pair(0.35f, 0.08f), Pair(0.72f, 0.25f), 
                Pair(0.88f, 0.12f), Pair(0.48f, 0.35f), Pair(0.62f, 0.05f),
                Pair(0.22f, 0.45f), Pair(0.95f, 0.38f), Pair(0.15f, 0.55f)
            )
            val stars2 = listOf(
                Pair(0.25f, 0.28f), Pair(0.55f, 0.18f), Pair(0.82f, 0.32f), 
                Pair(0.92f, 0.06f), Pair(0.38f, 0.48f), Pair(0.68f, 0.22f),
                Pair(0.08f, 0.32f), Pair(0.50f, 0.02f), Pair(0.80f, 0.48f)
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

            // Occasional shooting star streak
            if (shootingStarProgress in 0.65f..0.82f) {
                val t = (shootingStarProgress - 0.65f) / 0.17f
                val startX = width * (0.85f - t * 0.45f)
                val startY = height * (0.05f + t * 0.25f)
                val trailLen = 80.dp.toPx()
                drawLine(
                    brush = Brush.linearGradient(
                        colors = listOf(Color.White.copy(alpha = 0.9f), Color.White.copy(alpha = 0f)),
                        start = Offset(startX, startY),
                        end = Offset(startX + trailLen * 0.8f, startY - trailLen * 0.4f)
                    ),
                    start = Offset(startX, startY),
                    end = Offset(startX + trailLen * 0.8f, startY - trailLen * 0.4f),
                    strokeWidth = 2.dp.toPx()
                )
            }

            val moonX = width * 0.82f
            val moonY = 140.dp.toPx()
            val moonR = 32.dp.toPx()

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFFFDE7).copy(alpha = 0.28f), Color.Transparent),
                    center = Offset(moonX, moonY),
                    radius = moonR * 2.5f
                ),
                radius = moonR * 2.5f,
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
    val rayRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(35000, easing = LinearEasing), RepeatMode.Restart),
        label = "SunnyRayRotation"
    )
    val particleYOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -300f,
        animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Restart),
        label = "SunParticleY"
    )

    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        if (width > 0 && height > 0) {
            val sunX = width * 0.85f
            val sunY = 140.dp.toPx()

            // Soft golden sunburst aura
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFFDE047).copy(alpha = 0.35f),
                        Color(0xFFF59E0B).copy(alpha = 0.15f),
                        Color.Transparent
                    ),
                    center = Offset(sunX, sunY),
                    radius = 260.dp.toPx()
                ),
                radius = 260.dp.toPx(),
                center = Offset(sunX, sunY)
            )

            // Rotating sun ray wedges
            val numRays = 8
            val rayRadius = 380.dp.toPx()
            for (i in 0 until numRays) {
                val angleDeg = (i * (360f / numRays)) + rayRotation
                val angleRad = Math.toRadians(angleDeg.toDouble())
                val endX = sunX + (rayRadius * Math.cos(angleRad)).toFloat()
                val endY = sunY + (rayRadius * Math.sin(angleRad)).toFloat()
                drawLine(
                    color = Color(0xFFFEF08A).copy(alpha = 0.08f),
                    start = Offset(sunX, sunY),
                    end = Offset(endX, endY),
                    strokeWidth = 24.dp.toPx()
                )
            }

            // Floating golden light particles
            for (i in 0 until 12) {
                val baseX = (i * 73) % width.toInt()
                val baseY = (i * 127) % (height * 0.7f).toInt()
                val currY = (baseY + particleYOffset) % (height * 0.8f)
                val currX = baseX + 15f * kotlin.math.sin((currY / 40f).toDouble()).toFloat()
                drawCircle(
                    color = Color(0xFFFEF08A).copy(alpha = 0.35f),
                    radius = (2 + (i % 3)).dp.toPx(),
                    center = Offset(currX, currY)
                )
            }
        }
    }
}

@Composable
fun RainFallOverlay(infiniteTransition: InfiniteTransition) {
    val rainYOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart),
        label = "RainYOffset"
    )
    val rippleProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart),
        label = "RippleProgress"
    )

    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        if (width > 0 && height > 0) {
            val rainLinesCount = 40
            for (i in 0 until rainLinesCount) {
                val startX = (i * 37) % width.toInt()
                val startYBase = (i * 97) % height.toInt()
                val currentY = (startYBase + rainYOffset) % height
                drawLine(
                    color = Color.White.copy(alpha = 0.28f),
                    start = Offset(startX.toFloat(), currentY),
                    end = Offset(startX.toFloat() - 6.dp.toPx(), currentY + 16.dp.toPx()),
                    strokeWidth = 1.4.dp.toPx()
                )
            }

            // Ground water ripples
            val minR = 2.dp.toPx()
            val maxR = 16.dp.toPx()
            val rippleRadius = minR + (maxR - minR) * rippleProgress
            val rippleCenters = listOf(
                Offset(width * 0.2f, height * 0.90f),
                Offset(width * 0.5f, height * 0.93f),
                Offset(width * 0.8f, height * 0.88f)
            )
            rippleCenters.forEach { center ->
                drawOval(
                    color = Color.White.copy(alpha = (1f - rippleProgress).coerceIn(0f, 1f) * 0.3f),
                    topLeft = Offset(center.x - rippleRadius, center.y - rippleRadius * 0.4f),
                    size = Size(rippleRadius * 2f, rippleRadius * 0.8f),
                    style = Stroke(width = 1.dp.toPx())
                )
            }
        }
    }
}

@Composable
fun HeavyRainOverlay(infiniteTransition: InfiniteTransition) {
    val rainYOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1300f,
        animationSpec = infiniteRepeatable(tween(750, easing = LinearEasing), RepeatMode.Restart),
        label = "HeavyRainY"
    )
    val splashProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(750, easing = LinearEasing), RepeatMode.Restart),
        label = "SplashProgress"
    )

    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        if (width > 0 && height > 0) {
            val rainLinesCount = 65
            for (i in 0 until rainLinesCount) {
                val startX = (i * 29) % width.toInt()
                val startYBase = (i * 131) % height.toInt()
                val currentY = (startYBase + rainYOffset) % height
                val slant = 12.dp.toPx()
                drawLine(
                    color = Color(0xFFBAE6FD).copy(alpha = 0.38f),
                    start = Offset(startX.toFloat(), currentY),
                    end = Offset(startX.toFloat() - slant, currentY + 24.dp.toPx()),
                    strokeWidth = 2.dp.toPx()
                )
            }

            val minR = 2.dp.toPx()
            val maxR = 16.dp.toPx()
            val splashRadius = minR + (maxR - minR) * splashProgress
            val splashPoints = listOf(
                Pair(0.15f, 0.92f), Pair(0.35f, 0.95f), Pair(0.55f, 0.91f), 
                Pair(0.75f, 0.94f), Pair(0.9f, 0.93f), Pair(0.25f, 0.96f)
            )
            splashPoints.forEach { (x, y) ->
                drawOval(
                    color = Color.White.copy(alpha = (1f - splashProgress).coerceIn(0f, 1f) * 0.45f),
                    topLeft = Offset(x * width - splashRadius, y * height - splashRadius / 3f),
                    size = Size(splashRadius * 2f, splashRadius * 0.6f),
                    style = Stroke(width = 1.2.dp.toPx())
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
        animationSpec = infiniteRepeatable(tween(5500, easing = LinearEasing), RepeatMode.Restart),
        label = "LightningFlash"
    )
    val isFlashActive = (lightningFlash in 0.38f..0.41f) || (lightningFlash in 0.43f..0.45f)
    val flashAlpha = if (lightningFlash in 0.38f..0.41f) 0.75f else if (lightningFlash in 0.43f..0.45f) 0.9f else 0f

    Box(modifier = Modifier.fillMaxSize()) {
        HeavyRainOverlay(infiniteTransition = infiniteTransition)
        if (isFlashActive) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFE0F2FE).copy(alpha = flashAlpha * 0.25f))
            )
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val boltPath = Path().apply {
                    moveTo(w * 0.6f, 0f)
                    lineTo(w * 0.52f, h * 0.22f)
                    lineTo(w * 0.58f, h * 0.25f)
                    lineTo(w * 0.45f, h * 0.50f)
                    lineTo(w * 0.52f, h * 0.52f)
                    lineTo(w * 0.40f, h * 0.75f)
                }
                drawPath(
                    path = boltPath,
                    color = Color.White.copy(alpha = flashAlpha),
                    style = Stroke(width = 3.dp.toPx())
                )
            }
        }
    }
}

@Composable
fun SnowDriftOverlay(infiniteTransition: InfiniteTransition) {
    val snowYOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 700f,
        animationSpec = infiniteRepeatable(tween(4500, easing = LinearEasing), RepeatMode.Restart),
        label = "SnowYOffset"
    )
    val snowXDrift by infiniteTransition.animateFloat(
        initialValue = -35f,
        targetValue = 35f,
        animationSpec = infiniteRepeatable(tween(2800, easing = LinearEasing), RepeatMode.Reverse),
        label = "SnowXDrift"
    )
    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        if (width > 0 && height > 0) {
            val flakeCount = 35
            for (i in 0 until flakeCount) {
                val startX = (i * 59) % width.toInt()
                val startYBase = (i * 101) % height.toInt()
                val currentY = (startYBase + snowYOffset) % height
                val currentX = (startX + snowXDrift + 15f * kotlin.math.sin((currentY / 60f).toDouble()).toFloat()) % width
                val radiusDp = when (i % 3) {
                    0 -> 2.dp
                    1 -> 3.5.dp
                    else -> 5.dp
                }
                drawCircle(
                    color = Color.White.copy(alpha = 0.55f),
                    radius = radiusDp.toPx(),
                    center = Offset(currentX, currentY)
                )
            }
        }
    }
}

@Composable
fun CloudDriftOverlay(infiniteTransition: InfiniteTransition, condition: WeatherCondition) {
    val cloudOffset1 by infiniteTransition.animateFloat(
        initialValue = -180f,
        targetValue = 480f,
        animationSpec = infiniteRepeatable(tween(26000, easing = LinearEasing), RepeatMode.Restart),
        label = "CloudDrift1"
    )
    val cloudOffset2 by infiniteTransition.animateFloat(
        initialValue = -100f,
        targetValue = 520f,
        animationSpec = infiniteRepeatable(tween(18000, easing = LinearEasing), RepeatMode.Restart),
        label = "CloudDrift2"
    )

    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        if (width > 0) {
            val cloudColorBg = Color(0xFF1E293B).copy(alpha = if (condition == WeatherCondition.CLOUDY) 0.35f else 0.20f)
            val cloudColorFg = Color(0xFF334155).copy(alpha = if (condition == WeatherCondition.CLOUDY) 0.28f else 0.16f)

            // Background cloud layer
            drawCircle(
                color = cloudColorBg,
                radius = 160.dp.toPx(),
                center = Offset(cloudOffset1.dp.toPx(), 160.dp.toPx())
            )
            drawCircle(
                color = cloudColorBg,
                radius = 120.dp.toPx(),
                center = Offset((cloudOffset1 + 130f).dp.toPx(), 200.dp.toPx())
            )

            // Foreground cloud layer
            drawCircle(
                color = cloudColorFg,
                radius = 140.dp.toPx(),
                center = Offset(cloudOffset2.dp.toPx(), 220.dp.toPx())
            )
        }
    }
}

@Composable
fun DenseCloudOverlay(infiniteTransition: InfiniteTransition) {
    val cloudOffset1 by infiniteTransition.animateFloat(
        initialValue = -250f,
        targetValue = 480f,
        animationSpec = infiniteRepeatable(tween(38000, easing = LinearEasing), RepeatMode.Restart),
        label = "DenseCloud1"
    )
    val cloudOffset2 by infiniteTransition.animateFloat(
        initialValue = -150f,
        targetValue = 580f,
        animationSpec = infiniteRepeatable(tween(50000, easing = LinearEasing), RepeatMode.Restart),
        label = "DenseCloud2"
    )

    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        if (width > 0) {
            val darkStormColor = Color(0xFF0F172A).copy(alpha = 0.38f)
            val slateCloudColor = Color(0xFF334155).copy(alpha = 0.32f)
            drawCircle(
                color = darkStormColor,
                radius = 190.dp.toPx(),
                center = Offset(cloudOffset1.dp.toPx(), 130.dp.toPx())
            )
            drawCircle(
                color = slateCloudColor,
                radius = 150.dp.toPx(),
                center = Offset((cloudOffset1 + 140f).dp.toPx(), 170.dp.toPx())
            )
            drawCircle(
                color = darkStormColor,
                radius = 210.dp.toPx(),
                center = Offset(cloudOffset2.dp.toPx(), 210.dp.toPx())
            )
            drawCircle(
                color = slateCloudColor,
                radius = 160.dp.toPx(),
                center = Offset((cloudOffset2 - 130f).dp.toPx(), 190.dp.toPx())
            )
        }
    }
}

@Composable
fun FogOverlay(infiniteTransition: InfiniteTransition) {
    val fogOffset1 by infiniteTransition.animateFloat(
        initialValue = -200f,
        targetValue = 600f,
        animationSpec = infiniteRepeatable(tween(32000, easing = LinearEasing), RepeatMode.Restart),
        label = "FogOffset1"
    )
    val fogOffset2 by infiniteTransition.animateFloat(
        initialValue = 600f,
        targetValue = -200f,
        animationSpec = infiniteRepeatable(tween(42000, easing = LinearEasing), RepeatMode.Restart),
        label = "FogOffset2"
    )
    val fogAlpha by infiniteTransition.animateFloat(
        initialValue = 0.32f,
        targetValue = 0.52f,
        animationSpec = infiniteRepeatable(tween(4500, easing = LinearEasing), RepeatMode.Reverse),
        label = "FogAlpha"
    )

    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        if (width > 0 && height > 0) {
            drawOval(
                color = Color.White.copy(alpha = fogAlpha * 0.42f),
                topLeft = Offset(fogOffset1.dp.toPx(), height * 0.35f),
                size = Size(width * 0.85f, height * 0.30f)
            )
            drawOval(
                color = Color.White.copy(alpha = fogAlpha * 0.38f),
                topLeft = Offset(fogOffset2.dp.toPx(), height * 0.55f),
                size = Size(width * 0.95f, height * 0.35f)
            )
        }
    }
}

@Composable
fun MistOverlay(infiniteTransition: InfiniteTransition) {
    val driftX by infiniteTransition.animateFloat(
        initialValue = -50f,
        targetValue = 150f,
        animationSpec = infiniteRepeatable(tween(11000, easing = LinearEasing), RepeatMode.Restart),
        label = "MistDriftX"
    )
    val driftY by infiniteTransition.animateFloat(
        initialValue = -20f,
        targetValue = 20f,
        animationSpec = infiniteRepeatable(tween(3800, easing = LinearEasing), RepeatMode.Reverse),
        label = "MistDriftY"
    )

    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        if (width > 0 && height > 0) {
            val mistParticleCount = 22
            for (i in 0 until mistParticleCount) {
                val startX = (i * 113) % width.toInt()
                val startY = (i * 73) % (height * 0.7f).toInt()
                val currentX = (startX + driftX) % width
                val currentY = startY + driftY
                drawCircle(
                    color = Color.White.copy(alpha = 0.18f),
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
        targetValue = 0.28f,
        animationSpec = infiniteRepeatable(tween(5500, easing = LinearEasing), RepeatMode.Reverse),
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
