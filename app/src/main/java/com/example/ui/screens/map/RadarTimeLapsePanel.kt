package com.example.ui.screens.map

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.ui.icons.SkySphereIcons
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun RadarTimeLapsePanel(
    state: TimeLapseState,
    onTogglePlayPause: () -> Unit,
    onPreviousFrame: () -> Unit,
    onNextFrame: () -> Unit,
    onSeekToFrame: (Int) -> Unit,
    onCycleSpeed: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (state.activeLayer == MapWeatherLayer.NONE) return

    val haptic = LocalHapticFeedback.current
    var lastHapticIndex by remember { mutableIntStateOf(state.currentFrameIndex) }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse_radar")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    val glassBackground = Brush.verticalGradient(
        colors = listOf(
            Color(0xF212162A), // Translucent dark midnight slate
            Color(0xFA1A1F36)
        )
    )

    val currentFrame = state.currentFrame
    val isNowFrame = currentFrame?.isNow == true

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 0.dp)
            .shadow(8.dp, RoundedCornerShape(16.dp), clip = false)
            .background(glassBackground, RoundedCornerShape(16.dp))
            .border(
                width = 1.dp,
                brush = Brush.horizontalGradient(
                    listOf(
                        Color(0x554FD1C5),
                        Color(0x336366F1),
                        Color(0x554FD1C5)
                    )
                ),
                shape = RoundedCornerShape(16.dp)
            )
            .testTag("radar_timelapse_panel")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            // 1. COMPACT TOP HEADER
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Live Radar Pulse & Timestamp
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF00E676))
                            .alpha(pulseAlpha)
                    )
                    Spacer(modifier = Modifier.width(6.dp))

                    Text(
                        text = "LIVE MAP (OPTIMIZED)",
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = Color(0xFF00E676),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    )
                    if (currentFrame?.formattedClock != null) {
                        Text(
                            text = " • ${currentFrame.formattedClock}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color(0xCCFFFFFF),
                                fontWeight = FontWeight.Medium,
                                fontSize = 10.sp
                            )
                        )
                    }
                }

                // Speed Selector Pill Button (Disabled during optimization)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0x1AFFFFFF),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .testTag("timelapse_speed_button")
                ) {
                    Text(
                        text = "1x (Paused)",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = Color(0x88FFFFFF),
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        ),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // Optimization Notice Bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .background(Color(0x1F4FD1C5), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Icon(
                    imageVector = SkySphereIcons.Info,
                    contentDescription = null,
                    tint = Color(0xFF4FD1C5),
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Timelapse playback temporarily paused for radar optimization",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color(0xFF4FD1C5),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            }

            // 2. TIMELINE SLIDER (Disabled during optimization)
            val maxIndex = (state.frames.size - 1).coerceAtLeast(1)
            Slider(
                value = state.currentFrameIndex.toFloat().coerceIn(0f, maxIndex.toFloat()),
                onValueChange = { },
                enabled = false,
                valueRange = 0f..maxIndex.toFloat(),
                steps = (state.frames.size - 2).coerceAtLeast(0),
                colors = SliderDefaults.colors(
                    disabledThumbColor = Color(0xFF00E676),
                    disabledActiveTrackColor = Color(0xFF319795),
                    disabledInactiveTrackColor = Color(0x22FFFFFF),
                    disabledActiveTickColor = Color.Transparent,
                    disabledInactiveTickColor = Color.Transparent
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .testTag("timelapse_slider")
            )

            // 3. BOTTOM CONTROLS & STATUS ROW (Disabled during optimization)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 0.dp)
            ) {
                // Left: Live Status
                Text(
                    text = "Live Radar Active",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color(0xB3FFFFFF),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium
                    )
                )

                // Center: Disabled Playback Controls
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Previous Frame Button (Disabled)
                    IconButton(
                        onClick = { },
                        enabled = false,
                        modifier = Modifier
                            .size(28.dp)
                            .background(Color(0x0DFFFFFF), CircleShape)
                            .testTag("timelapse_prev_button")
                    ) {
                        Icon(
                            imageVector = SkySphereIcons.SkipPrevious,
                            contentDescription = "Previous Frame (Disabled)",
                            tint = Color(0x44FFFFFF),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Main Play / Pause Button (Disabled)
                    Surface(
                        shape = CircleShape,
                        color = Color(0x33319795),
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .testTag("timelapse_play_pause_button")
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(
                                imageVector = SkySphereIcons.Play,
                                contentDescription = "Play (Disabled for optimization)",
                                tint = Color(0x66FFFFFF),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Next Frame Button (Disabled)
                    IconButton(
                        onClick = { },
                        enabled = false,
                        modifier = Modifier
                            .size(28.dp)
                            .background(Color(0x0DFFFFFF), CircleShape)
                            .testTag("timelapse_next_button")
                    ) {
                        Icon(
                            imageVector = SkySphereIcons.SkipNext,
                            contentDescription = "Next Frame (Disabled)",
                            tint = Color(0x44FFFFFF),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Right: Badge Tag
                Surface(
                    shape = RoundedCornerShape(5.dp),
                    color = Color(0x3300E676),
                    modifier = Modifier.clip(RoundedCornerShape(5.dp))
                ) {
                    Text(
                        text = "LIVE",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color(0xFF00E676),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}
