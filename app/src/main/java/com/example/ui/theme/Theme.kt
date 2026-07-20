package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = LuxurySkyBlue,
    secondary = LuxuryCyan,
    tertiary = LuxuryGold,
    background = ObsidianBg,
    surface = ObsidianCard,
    onPrimary = ObsidianBg,
    onSecondary = ObsidianBg,
    onTertiary = ObsidianBg,
    onBackground = ObsidianTextPrimary,
    onSurface = ObsidianTextPrimary,
    surfaceVariant = ObsidianCardBorder,
    onSurfaceVariant = ObsidianTextSecondary,
    outline = ObsidianCardBorder
)

private val LightColorScheme = lightColorScheme(
    primary = LuxurySkyBlue,
    secondary = LuxuryCyan,
    tertiary = LuxuryGold,
    background = PearlBg,
    surface = PearlCard,
    onPrimary = PearlBg,
    onSecondary = PearlBg,
    onTertiary = PearlBg,
    onBackground = PearlTextPrimary,
    onSurface = PearlTextPrimary,
    surfaceVariant = PearlCardBorder,
    onSurfaceVariant = PearlTextSecondary,
    outline = PearlCardBorder
)

@Composable
fun SkySphereTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
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
                windowInsetsController.isAppearanceLightStatusBars = !darkTheme
                windowInsetsController.isAppearanceLightNavigationBars = !darkTheme
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
