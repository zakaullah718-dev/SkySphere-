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
                            .background(if (state.isPlaying) Color(0xFF00E676) else Color(0xFFFFB74D))
                            .alpha(pulseAlpha)
                    )
                    Spacer(modifier = Modifier.width(6.dp))

                    Text(
                        text = currentFrame?.displayLabel ?: "LIVE MAP",
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = if (isNowFrame) Color(0xFF00E676) else Color.White,
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

                // Speed Selector Pill Button
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0x33FFFFFF),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onCycleSpeed()
                        }
                        .testTag("timelapse_speed_button")
                ) {
                    Text(
                        text = "${state.playbackSpeed}x",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            // Buffer / Preload Banner
            if (state.isBuffering && state.bufferProgress < 1.0f) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .background(Color(0x1F4FD1C5), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    CircularProgressIndicator(
                        color = Color(0xFF4FD1C5),
                        strokeWidth = 1.5.dp,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Preloading time-lapse... ${(state.bufferProgress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color(0xFF4FD1C5),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }

            // 2. TIMELINE SLIDER
            val maxIndex = (state.frames.size - 1).coerceAtLeast(1)
            Slider(
                value = state.currentFrameIndex.toFloat().coerceIn(0f, maxIndex.toFloat()),
                onValueChange = { newVal ->
                    val newIdx = newVal.toInt().coerceIn(0, maxIndex)
                    if (newIdx != lastHapticIndex) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        lastHapticIndex = newIdx
                    }
                    onSeekToFrame(newIdx)
                },
                enabled = state.frames.isNotEmpty(),
                valueRange = 0f..maxIndex.toFloat(),
                steps = (state.frames.size - 2).coerceAtLeast(0),
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF00E676),
                    activeTrackColor = Color(0xFF319795),
                    inactiveTrackColor = Color(0x33FFFFFF),
                    activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(18.dp)
                    .testTag("timelapse_slider")
            )

            // 3. BOTTOM CONTROLS & STATUS ROW
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 0.dp)
            ) {
                // Left: Active Layer Name
                Text(
                    text = "${state.activeLayer.displayName} Timelapse",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color(0xB3FFFFFF),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                )

                // Center: Active Playback Controls
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Previous Frame Button
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onPreviousFrame()
                        },
                        enabled = state.frames.isNotEmpty(),
                        modifier = Modifier
                            .size(32.dp)
                            .background(Color(0x22FFFFFF), CircleShape)
                            .testTag("timelapse_prev_button")
                    ) {
                        Icon(
                            imageVector = SkySphereIcons.SkipPrevious,
                            contentDescription = "Previous Frame",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Main Play / Pause Button
                    val isPlayDisabled = !state.isReadyToPlay || state.isBuffering
                    Surface(
                        shape = CircleShape,
                        color = if (isPlayDisabled && !state.isPlaying) Color(0x66319795) else if (state.isPlaying) Color(0xFFE53935) else Color(0xFF319795),
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .clickable(enabled = !isPlayDisabled || state.isPlaying) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onTogglePlayPause()
                            }
                            .testTag("timelapse_play_pause_button")
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            if (state.isBuffering) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(20.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = if (state.isPlaying) SkySphereIcons.Pause else SkySphereIcons.Play,
                                    contentDescription = if (state.isPlaying) "Pause Timelapse" else "Play Timelapse",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }

                    // Next Frame Button
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onNextFrame()
                        },
                        enabled = state.frames.isNotEmpty(),
                        modifier = Modifier
                            .size(32.dp)
                            .background(Color(0x22FFFFFF), CircleShape)
                            .testTag("timelapse_next_button")
                    ) {
                        Icon(
                            imageVector = SkySphereIcons.SkipNext,
                            contentDescription = "Next Frame",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Right: Badge Tag
                Surface(
                    shape = RoundedCornerShape(5.dp),
                    color = if (isNowFrame) Color(0x3300E676) else Color(0x336366F1),
                    modifier = Modifier.clip(RoundedCornerShape(5.dp))
                ) {
                    Text(
                        text = if (isNowFrame) "LIVE" else "HISTORICAL",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = if (isNowFrame) Color(0xFF00E676) else Color(0xFF818CF8),
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
