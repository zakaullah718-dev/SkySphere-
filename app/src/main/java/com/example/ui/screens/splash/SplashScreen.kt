package com.example.ui.screens.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.SkySphereLoadingAnimation
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onNavigateToHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Entrance animations state
    val scale = remember { Animatable(0.7f) }
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(key1 = true) {
        // Animate scale and alpha
        alpha.animateTo(1f, animationSpec = tween(1200))
        scale.animateTo(1f, animationSpec = tween(1000))
        
        // Hold on screen, then transition
        delay(1500)
        onNavigateToHome()
    }

    // Dynamic background gradient based on theme mode
    val isDark = true
    val bgBrush = if (isDark) {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFF04060E),
                Color(0xFF0C0F22),
                Color(0xFF04060E)
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFFE2EAF4),
                Color(0xFFF1F5F9),
                Color(0xFFE2EAF4)
            )
        )
    }

    val primaryTextColor = if (isDark) Color.White else Color(0xFF0F172A)
    val secondaryTextColor = if (isDark) Color(0xFF2FA3FF) else Color(0xFF1E88E5)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgBrush)
            .testTag("splash_screen_container"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            // Celestial Glowing Symbol
            Box(
                modifier = Modifier
                    .scale(scale.value)
                    .alpha(alpha.value),
                contentAlignment = Alignment.Center
            ) {
                SkySphereLoadingAnimation(size = 140.dp)
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Brand Name
            Text(
                text = "SKYSPHERE",
                style = MaterialTheme.typography.displayMedium.copy(
                    fontWeight = FontWeight.Light,
                    fontSize = 42.sp,
                    letterSpacing = 8.sp,
                    color = primaryTextColor
                ),
                modifier = Modifier
                    .alpha(alpha.value)
                    .scale(scale.value)
                    .testTag("splash_title")
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Slogan
            Text(
                text = "ATMOSPHERIC PRECISION",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 4.sp,
                    color = secondaryTextColor
                ),
                modifier = Modifier
                    .alpha(alpha.value)
                    .testTag("splash_subtitle")
            )
        }
    }
}
