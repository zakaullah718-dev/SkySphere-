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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.Icon
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
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

import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.platform.LocalContext
import com.example.worker.WeatherWorkerScheduler

import com.example.ui.theme.AppThemePreset

@Composable
fun SettingsScreen(
    darkTheme: Boolean,
    onThemeToggle: (Boolean) -> Unit,
    currentTheme: String = "MIDNIGHT_BLUE",
    onAppThemeChange: (String) -> Unit = {},
    isCelsius: Boolean,
    onCelsiusToggle: (Boolean) -> Unit,
    windUnit: String,
    onWindUnitChange: (String) -> Unit,
    selectedProvider: ProviderType,
    onProviderChange: (ProviderType) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = true

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

        // THEME PRESETS GRID ("A lot of theme options")
        item {
            SettingsSectionHeader(title = "VISUAL THEME PRESETS", icon = Icons.Default.Palette)
            Spacer(modifier = Modifier.height(12.dp))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AppThemePreset.values().forEach { preset ->
                    val isSelected = currentTheme.equals(preset.id, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(preset.surfaceColor)
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) preset.primaryColor else Color(0x33FFFFFF),
                                shape = RoundedCornerShape(18.dp)
                            )
                            .clickable {
                                onAppThemeChange(preset.id)
                                onThemeToggle(preset.isDark)
                            }
                            .padding(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                // Theme preview color circle pair
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(
                                            Brush.linearGradient(
                                                colors = listOf(preset.primaryColor, preset.secondaryColor)
                                            )
                                        )
                                )

                                Spacer(modifier = Modifier.width(12.dp))

                                Column {
                                    Text(
                                        text = preset.displayName.uppercase(),
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = if (preset.isDark) Color.White else Color(0xFF0F172A)
                                        )
                                    )
                                    Text(
                                        text = preset.description,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = if (preset.isDark) Color(0x99FFFFFF) else Color(0x990F172A),
                                            fontSize = 11.sp
                                        )
                                    )
                                }
                            }

                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(preset.primaryColor),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = "Selected",
                                        tint = if (preset.isDark) Color.Black else Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // WORKMANAGER BACKGROUND TASKS & PERSONALIZED NOTIFICATIONS
        item {
            val context = LocalContext.current
            val userPrefs = remember { com.example.data.repository.UserPreferencesRepository.getInstance(context) }
            val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
            
            val savedName by userPrefs.userNameFlow.collectAsState(initial = "")
            val isNotifEnabled by userPrefs.notificationsEnabledFlow.collectAsState(initial = true)
            
            var nameInput by remember(savedName) { mutableStateOf(savedName) }
            var isEditingName by remember { mutableStateOf(false) }

            SettingsSectionHeader(title = "PERSONALIZED WEATHER BRIEFINGS (WORKMANAGER)", icon = Icons.Default.NotificationsActive)
            Spacer(modifier = Modifier.height(10.dp))

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // USER DISPLAY NAME CARD
                SkySphereCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(4.dp)) {
                        Text(
                            text = "USER DISPLAY NAME",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.5.sp
                            )
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.material3.OutlinedTextField(
                                value = nameInput,
                                onValueChange = { nameInput = it },
                                label = { Text("Your Name (e.g. Paul)") },
                                singleLine = true,
                                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = Color(0x33FFFFFF),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("settings_user_name_input")
                            )

                            Spacer(modifier = Modifier.width(10.dp))

                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        userPrefs.setUserName(nameInput.trim())
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier.testTag("save_user_name_settings_button")
                            ) {
                                Text("SAVE", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }

                        if (savedName.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Greeting preview: \"Hi $savedName 👋\" / \"Good Morning $savedName ☀️\"",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color(0xFF38BDF8),
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }
                }

                // 6-HOUR PERIODIC NOTIFICATION SCHEDULE TOGGLE
                SegmentedControl(
                    options = listOf("6-HOUR BRIEFINGS (ACTIVE)", "NOTIFICATIONS OFF"),
                    selectedIndex = if (isNotifEnabled) 0 else 1,
                    onOptionSelected = { index ->
                        val enabled = (index == 0)
                        coroutineScope.launch {
                            userPrefs.setNotificationsEnabled(enabled)
                            if (enabled) {
                                WeatherWorkerScheduler.schedulePeriodicWeatherUpdates(context, intervalHours = 6)
                            } else {
                                WeatherWorkerScheduler.cancelPeriodicWeatherUpdates(context)
                            }
                        }
                    },
                    modifier = Modifier.testTag("work_manager_control")
                )

                // TEST NOTIFICATION BUTTON
                Button(
                    onClick = {
                        WeatherWorkerScheduler.triggerImmediateWeatherUpdate(context)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("test_notification_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                        contentColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = CircleShape
                ) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "TRIGGER TEST WEATHER NOTIFICATION NOW",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            letterSpacing = 1.sp
                        )
                    )
                }

                Text(
                    text = "WorkManager automatically delivers personalized weather briefings every 6 hours (6 AM, 12 PM, 6 PM, 12 AM). Notifications update using fresh weather API data and survive device reboots. If network is unavailable, notifications skip automatically to avoid stale data.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        fontSize = 11.sp
                    ),
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
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
    val isDark = true

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(CircleShape)
            .background(Color(0xFF1E1E2E)) // Solid accessible dark gray background (as requested)
            .border(1.dp, Color(0xFF374151), CircleShape) // High-contrast border for high accessibility (as requested)
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
                MaterialTheme.colorScheme.onPrimary
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
