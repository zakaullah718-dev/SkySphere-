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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .shadow(12.dp, RoundedCornerShape(24.dp), clip = false)
            .background(glassBackground, RoundedCornerShape(24.dp))
            .border(
                width = 1.dp,
                brush = Brush.horizontalGradient(
                    listOf(
                        Color(0x554FD1C5),
                        Color(0x336366F1),
                        Color(0x554FD1C5)
                    )
                ),
                shape = RoundedCornerShape(24.dp)
            )
            .testTag("radar_timelapse_panel")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // 1. TOP HEADER: Live Indicator, Current Timestamp, Speed Selector
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
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(
                                if (state.isPlaying) Color(0xFF00E676) else Color(0xFF4FD1C5)
                            )
                            .alpha(if (state.isPlaying) pulseAlpha else 0.8f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))

                    Column {
                        Text(
                            text = "TIME-LAPSE • ${state.activeLayer.displayName.uppercase()}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color(0xFF4FD1C5),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp
                            )
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = currentFrame?.displayLabel ?: "NOW",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 15.sp
                                )
                            )
                            if (currentFrame?.formattedClock != null) {
                                Text(
                                    text = "  •  ${currentFrame.formattedClock}",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = Color(0xCCFFFFFF),
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 12.sp
                                    )
                                )
                            }
                        }
                    }
                }

                // Speed Selector Pill Button
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0x33FFFFFF),
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onCycleSpeed() }
                        .testTag("timelapse_speed_button")
                ) {
                    Text(
                        text = "${state.playbackSpeed}x",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = Color(0xFF4FD1C5),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        ),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 2. TIMELINE SLIDER & TRACK
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp)
            ) {
                Text(
                    text = "Past 6 Hours",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color(0x99FFFFFF),
                        fontSize = 10.sp
                    )
                )
                Text(
                    text = if (currentFrame?.isNow == true) "NOW (LIVE)" else if (currentFrame?.isForecast == true) "Forecast" else "Live Radar",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = if (currentFrame?.isNow == true) Color(0xFF00E676) else Color(0x99FFFFFF),
                        fontSize = 10.sp,
                        fontWeight = if (currentFrame?.isNow == true) FontWeight.Bold else FontWeight.Normal
                    )
                )
            }

            val maxIndex = (state.frames.size - 1).coerceAtLeast(1)
            Slider(
                value = state.currentFrameIndex.toFloat().coerceIn(0f, maxIndex.toFloat()),
                onValueChange = { newValue ->
                    onSeekToFrame(newValue.toInt())
                },
                valueRange = 0f..maxIndex.toFloat(),
                steps = (state.frames.size - 2).coerceAtLeast(0),
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF4FD1C5),
                    activeTrackColor = Color(0xFF319795),
                    inactiveTrackColor = Color(0x33FFFFFF),
                    activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .testTag("timelapse_slider")
            )

            // 3. PLAYBACK CONTROLS ROW
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp)
            ) {
                // Previous Frame Button
                IconButton(
                    onClick = onPreviousFrame,
                    modifier = Modifier.size(40.dp).testTag("timelapse_prev_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous Frame",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Main Play / Pause Button
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF319795),
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable { onTogglePlayPause() }
                        .testTag("timelapse_play_pause_button")
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(8.dp)
                    ) {
                        if (state.isLoading) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(22.dp)
                            )
                        } else {
                            Icon(
                                imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (state.isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }

                // Next Frame Button
                IconButton(
                    onClick = onNextFrame,
                    modifier = Modifier.size(40.dp).testTag("timelapse_next_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next Frame",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}
