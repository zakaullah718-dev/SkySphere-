package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

enum class AppThemePreset(
    val id: String,
    val displayName: String,
    val description: String,
    val primaryColor: Color,
    val secondaryColor: Color,
    val surfaceColor: Color,
    val backgroundColor: Color,
    val isDark: Boolean
) {
    MIDNIGHT_BLUE(
        id = "MIDNIGHT_BLUE",
        displayName = "Midnight Ocean",
        description = "Deep navy & starlight blue theme with golden highlights",
        primaryColor = Color(0xFF38BDF8),
        secondaryColor = Color(0xFF0284C7),
        surfaceColor = Color(0xFF1E293B),
        backgroundColor = Color(0xFF0F172A),
        isDark = true
    ),
    OBSIDIAN_DARK(
        id = "OBSIDIAN_DARK",
        displayName = "Space Obsidian",
        description = "Pure space black & glowing atmospheric cyan",
        primaryColor = Color(0xFF2FA3FF),
        secondaryColor = Color(0xFF00E5FF),
        surfaceColor = Color(0xFF1E1E2E),
        backgroundColor = Color(0xFF070913),
        isDark = true
    ),
    SKY_LIGHT(
        id = "SKY_LIGHT",
        displayName = "Sky Light",
        description = "Crisp daylight light mode with clean soft surfaces",
        primaryColor = Color(0xFF0284C7),
        secondaryColor = Color(0xFF0EA5E9),
        surfaceColor = Color(0xFFFFFFFF),
        backgroundColor = Color(0xFFF1F5F9),
        isDark = false
    ),
    SUNSET_GOLD(
        id = "SUNSET_GOLD",
        displayName = "Sunset Amber",
        description = "Warm twilight gradient with amber & gold highlights",
        primaryColor = Color(0xFFF59E0B),
        secondaryColor = Color(0xFFD97706),
        surfaceColor = Color(0xFF2A1B12),
        backgroundColor = Color(0xFF180E08),
        isDark = true
    ),
    AURORA_TEAL(
        id = "AURORA_TEAL",
        displayName = "Aurora Emerald",
        description = "Northern lights emerald teal & cyan glow",
        primaryColor = Color(0xFF10B981),
        secondaryColor = Color(0xFF059669),
        surfaceColor = Color(0xFF0F2D27),
        backgroundColor = Color(0xFF061B17),
        isDark = true
    ),
    VIOLET_NIGHT(
        id = "VIOLET_NIGHT",
        displayName = "Celestial Violet",
        description = "Deep galaxy violet & luminous purple glow",
        primaryColor = Color(0xFFA855F7),
        secondaryColor = Color(0xFF9333EA),
        surfaceColor = Color(0xFF23153C),
        backgroundColor = Color(0xFF0F081D),
        isDark = true
    );

    companion object {
        fun fromId(id: String): AppThemePreset {
            return values().find { it.id.equals(id, ignoreCase = true) } ?: MIDNIGHT_BLUE
        }
    }
}

private fun buildColorSchemeForPreset(preset: AppThemePreset): ColorScheme {
    return if (preset.isDark) {
        darkColorScheme(
            primary = preset.primaryColor,
            secondary = preset.secondaryColor,
            tertiary = LuxuryGold,
            background = preset.backgroundColor,
            surface = preset.surfaceColor,
            onPrimary = preset.backgroundColor,
            onSecondary = preset.backgroundColor,
            onTertiary = preset.backgroundColor,
            onBackground = Color.White,
            onSurface = Color.White,
            surfaceVariant = preset.surfaceColor.copy(alpha = 0.8f),
            onSurfaceVariant = Color(0xFFD1D5DB),
            outline = Color(0xFF334155)
        )
    } else {
        lightColorScheme(
            primary = preset.primaryColor,
            secondary = preset.secondaryColor,
            tertiary = LuxuryGold,
            background = preset.backgroundColor,
            surface = preset.surfaceColor,
            onPrimary = Color.White,
            onSecondary = Color.White,
            onTertiary = Color.White,
            onBackground = Color(0xFF0F172A),
            onSurface = Color(0xFF0F172A),
            surfaceVariant = Color(0xFFE2E8F0),
            onSurfaceVariant = Color(0xFF475569),
            outline = Color(0xFFCBD5E1)
        )
    }
}

@Composable
fun SkySphereTheme(
    themeId: String = "MIDNIGHT_BLUE",
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val preset = AppThemePreset.fromId(themeId)
    val colorScheme = buildColorSchemeForPreset(preset)
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            var context = view.context
            while (context is android.content.ContextWrapper) {
                if (context is Activity) {
                    break
                }
                context = context.baseContext
            }
            val activity = context as? Activity
            if (activity != null) {
                val window = activity.window
                window.statusBarColor = colorScheme.background.toArgb()
                window.navigationBarColor = colorScheme.background.toArgb()
                
                val windowInsetsController = WindowCompat.getInsetsController(window, view)
                windowInsetsController.isAppearanceLightStatusBars = !preset.isDark
                windowInsetsController.isAppearanceLightNavigationBars = !preset.isDark
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = SkySphereShapes,
        content = content
    )
}
