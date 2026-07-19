package com.example.ui.screens.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.repository.WeatherRepository.ProviderType
import com.example.ui.components.SkySphereCard

@Composable
fun SettingsScreen(
    darkTheme: Boolean,
    onThemeToggle: (Boolean) -> Unit,
    isCelsius: Boolean,
    onCelsiusToggle: (Boolean) -> Unit,
    windUnit: String,
    onWindUnitChange: (String) -> Unit,
    selectedProvider: ProviderType,
    onProviderChange: (ProviderType) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.background.value == 0xFF070913.toULong()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("settings_screen_container"),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // SCREEN TITLE
        item {
            Column {
                Text(
                    text = "SETTINGS",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Light,
                        letterSpacing = 3.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                )
                Text(
                    text = "Personalize measurements and visual preferences.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }

        // SEGMENTED WEATHER PROVIDER OPTION
        item {
            SettingsSectionHeader(title = "METEOROLOGICAL BACKEND PROVIDER", icon = Icons.Default.Cloud)
            Spacer(modifier = Modifier.height(10.dp))

            SegmentedControl(
                options = listOf("OPEN-METEO", "WEATHERAPI", "OPENWEATHER"),
                selectedIndex = when (selectedProvider) {
                    ProviderType.OPEN_METEO -> 0
                    ProviderType.WEATHER_API_COM -> 1
                    ProviderType.OPEN_WEATHER -> 2
                },
                onOptionSelected = { index ->
                    val provider = when (index) {
                        0 -> ProviderType.OPEN_METEO
                        1 -> ProviderType.WEATHER_API_COM
                        else -> ProviderType.OPEN_WEATHER
                    }
                    onProviderChange(provider)
                },
                modifier = Modifier.testTag("weather_provider_control")
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Note: WeatherAPI and OpenWeather require an active API Key. Open-Meteo runs on public access with zero setup.",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    fontSize = 11.sp
                ),
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }

        // SEGMENTED TEMPERATURE OPTION
        item {
            SettingsSectionHeader(title = "TEMPERATURE SCALE", icon = Icons.Default.Thermostat)
            Spacer(modifier = Modifier.height(10.dp))
            
            SegmentedControl(
                options = listOf("FAHRENHEIT (°F)", "CELSIUS (°C)"),
                selectedIndex = if (isCelsius) 1 else 0,
                onOptionSelected = { index -> onCelsiusToggle(index == 1) },
                modifier = Modifier.testTag("temp_unit_control")
            )
        }

        // SEGMENTED WIND OPTION
        item {
            SettingsSectionHeader(title = "WIND VELOCITY UNIT", icon = Icons.Default.Speed)
            Spacer(modifier = Modifier.height(10.dp))

            SegmentedControl(
                options = listOf("km/h", "mph", "knots"),
                selectedIndex = when (windUnit) {
                    "km/h" -> 0
                    "mph" -> 1
                    else -> 2
                },
                onOptionSelected = { index ->
                    val selectedUnit = when (index) {
                        0 -> "km/h"
                        1 -> "mph"
                        else -> "knots"
                    }
                    onWindUnitChange(selectedUnit)
                },
                modifier = Modifier.testTag("wind_unit_control")
            )
        }

        // THEME OPTION
        item {
            SettingsSectionHeader(title = "VISUAL SPACE THEME", icon = Icons.Default.Palette)
            Spacer(modifier = Modifier.height(10.dp))

            SegmentedControl(
                options = listOf("OBSIDIAN (DARK)", "PEARL (LIGHT)"),
                selectedIndex = if (darkTheme) 0 else 1,
                onOptionSelected = { index ->
                    onThemeToggle(index == 0)
                },
                modifier = Modifier.testTag("theme_unit_control")
            )
        }

        // ABOUT & SCALABILITY METRICS CARD
        item {
            SettingsSectionHeader(title = "SKYSPHERE INSIGHTS", icon = Icons.Default.Info)
            Spacer(modifier = Modifier.height(10.dp))

            val luxuryGrad = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF2FA3FF).copy(alpha = 0.08f),
                    Color(0xFF00E5FF).copy(alpha = 0.03f)
                )
            )

            SkySphereCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("about_card")
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(luxuryGrad)
                ) {
                    Column(modifier = Modifier.padding(2.dp)) {
                        Text(
                            text = "SKYSPHERE FOUNDATION",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Version 2.0.0 (Global Weather Engine)",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Architectural Upgrades (Phase 2):",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        ArchitecturalPoint(point = "• Production-ready, zero-mock Weather Platform engine.")
                        ArchitecturalPoint(point = "• Dynamic switching between Open-Meteo, WeatherAPI, and OpenWeather.")
                        ArchitecturalPoint(point = "• Room-Database local persistence cache for offline metrics.")
                        ArchitecturalPoint(point = "• 30-Minute automatic cache validation for minimized API overhead.")
                        ArchitecturalPoint(point = "• Room-Database recent search query recorder with clear capability.")
                        ArchitecturalPoint(point = "• Exact timezone matching and local time tracking per location.")
                        ArchitecturalPoint(point = "• GPS telemetry with geocoding, exception handling and denied permissions routing.")
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Developed by SkySphere Celestial Labs.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsSectionHeader(
    title: String,
    icon: ImageVector
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
        )
    }
}

@Composable
fun ArchitecturalPoint(point: String) {
    Text(
        text = point,
        style = MaterialTheme.typography.bodyMedium.copy(
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            lineHeight = 18.sp
        ),
        modifier = Modifier.padding(vertical = 2.dp)
    )
}

/**
 * Premium glassmorphic Segmented Control component.
 */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onOptionSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.background.value == 0xFF070913.toULong()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(CircleShape)
            .background(if (isDark) Color(0xFF13172E) else Color(0xFFF1F5F9))
            .border(1.dp, if (isDark) Color(0xFF1D2447) else Color(0xFFE2E8F0), CircleShape)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEachIndexed { index, text ->
            val isSelected = index == selectedIndex
            val targetBgColor = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                Color.Transparent
            }
            val bgAnimateColor by animateColorAsState(targetBgColor, label = "bg_color")

            val targetTextColor = if (isSelected) {
                Color.White
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
            val textAnimateColor by animateColorAsState(targetTextColor, label = "text_color")

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .background(bgAnimateColor)
                    .clickable { onOptionSelected(index) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = textAnimateColor,
                        fontSize = 10.sp,
                        letterSpacing = 0.5.sp
                    )
                )
            }
        }
    }
}
