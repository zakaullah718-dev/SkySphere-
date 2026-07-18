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
import com.example.ui.components.SkySphereCard

@Composable
fun SettingsScreen(
    darkTheme: Boolean,
    onThemeToggle: (Boolean) -> Unit,
    isCelsius: Boolean,
    onCelsiusToggle: (Boolean) -> Unit,
    windUnit: String,
    onWindUnitChange: (String) -> Unit,
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
                            text = "Version 1.0.0 (Milestone 1)",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Architectural Readiness:",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        ArchitecturalPoint(point = "• Clean MVVM Pattern with reactive Kotlin Flows.")
                        ArchitecturalPoint(point = "• Seamless Navigation Compose backstack routing.")
                        ArchitecturalPoint(point = "• Double-Theme design tokens with Material 3 integration.")
                        ArchitecturalPoint(point = "• Scalable Domain model ready for Gemini AI summarizers.")
                        ArchitecturalPoint(point = "• Local SQLite cache integration with Room Database API.")
                        
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
    val containerBg = if (isDark) Color(0xFF13172E) else Color(0xFFFFFFFF)
    val borderColor = if (isDark) Color(0xFF1D2447) else Color(0xFFE2E8F0)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(CircleShape)
            .background(containerBg)
            .border(1.dp, borderColor, CircleShape),
        verticalAlignment = Alignment.CenterVertically
    ) {
        options.forEachIndexed { index, option ->
            val isSelected = index == selectedIndex
            
            // Premium background coloring
            val tabBg by animateColorAsState(
                targetValue = if (isSelected) {
                    Color(0xFF2FA3FF) // Luxury sky blue background for selected tab
                } else {
                    Color.Transparent
                },
                label = "Segment Bg color animation"
            )

            val tabTextColor by animateColorAsState(
                targetValue = if (isSelected) {
                    Color.White
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                label = "Segment text color animation"
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .background(tabBg)
                    .clickable { onOptionSelected(index) }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = option,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        letterSpacing = 1.sp,
                        color = tabTextColor
                    )
                )
            }
        }
    }
}
