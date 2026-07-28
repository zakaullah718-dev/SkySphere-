package com.example.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.models.CityWeather
import com.example.data.repository.WeatherRepository
import com.example.ui.theme.SkySphereTheme

class SkySphereWidgetConfigActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Set result CANCELED by default so if user backs out, widget is not added
        setResult(Activity.RESULT_CANCELED)

        val intent = intent
        val extras = intent.extras
        if (extras != null) {
            appWidgetId = extras.getInt(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
            )
        }

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val repository = WeatherRepository(applicationContext)

        setContent {
            val currentAppTheme by repository.appTheme.collectAsState()

            SkySphereTheme(themeId = currentAppTheme, darkTheme = true) {
                WidgetConfigScreen(
                    repository = repository,
                    onSave = { mode, cityName ->
                        SkySphereWidgetPreferences.saveWidgetConfig(
                            applicationContext,
                            appWidgetId,
                            mode,
                            cityName
                        )
                        SkySphereWidgetManager.updateAllWidgets(applicationContext)

                        val resultValue = Intent().apply {
                            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                        }
                        setResult(Activity.RESULT_OK, resultValue)
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
fun WidgetConfigScreen(
    repository: WeatherRepository,
    onSave: (mode: String, cityName: String?) -> Unit
) {
    val favorites by repository.getFavoritesFlow().collectAsState(initial = emptyList())
    val activeCity by repository.selectedCity.collectAsState()

    var selectedMode by remember { mutableStateOf(SkySphereWidgetPreferences.MODE_CURRENT_LOCATION) }
    var selectedCityName by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    Scaffold(
        containerColor = Color(0xFF0F172A)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp)
        ) {
            Text(
                text = "SKYSPHERE WIDGET SETTINGS",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    color = Color(0xFF38BDF8)
                )
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Choose which location to display on this widget.",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = Color(0xFF94A3B8)
                )
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Option 1: Current GPS Location (Dynamic)
            ConfigOptionCard(
                title = "Current Location",
                subtitle = "Automatically updates with your GPS location",
                icon = Icons.Default.LocationOn,
                isSelected = selectedMode == SkySphereWidgetPreferences.MODE_CURRENT_LOCATION,
                onClick = {
                    selectedMode = SkySphereWidgetPreferences.MODE_CURRENT_LOCATION
                    selectedCityName = null
                },
                testTag = "config_option_current_location"
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Option 2: Active App City
            ConfigOptionCard(
                title = "Selected City in SkySphere",
                subtitle = "Currently showing: ${activeCity.cityName.uppercase()}",
                icon = Icons.Default.CheckCircle,
                isSelected = selectedMode == SkySphereWidgetPreferences.MODE_FIXED_CITY && selectedCityName == activeCity.cityName,
                onClick = {
                    selectedMode = SkySphereWidgetPreferences.MODE_FIXED_CITY
                    selectedCityName = activeCity.cityName
                },
                testTag = "config_option_active_city"
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "FAVORITE CITIES",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    color = Color(0xFFCBD5E1)
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(favorites) { city ->
                    val isSelected = selectedMode == SkySphereWidgetPreferences.MODE_FIXED_CITY && selectedCityName == city.cityName
                    ConfigCityRow(
                        city = city,
                        isSelected = isSelected,
                        onClick = {
                            selectedMode = SkySphereWidgetPreferences.MODE_FIXED_CITY
                            selectedCityName = city.cityName
                        }
                    )
                }

                if (favorites.isEmpty()) {
                    item {
                        Text(
                            text = "No favorite cities saved yet in SkySphere.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color(0xFF64748B)
                            ),
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    onSave(selectedMode, selectedCityName)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("save_widget_config_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF0284C7),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(imageVector = Icons.Default.Check, contentDescription = "Apply")
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "APPLY WIDGET SETTINGS",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                )
            }
        }
    }
}

@Composable
fun ConfigOptionCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    val borderColor = if (isSelected) Color(0xFF38BDF8) else Color(0xFF1E293B)
    val bgColor = if (isSelected) Color(0xFF0C4A6E).copy(alpha = 0.4f) else Color(0xFF1E293B).copy(alpha = 0.5f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .border(1.5.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(16.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = if (isSelected) Color(0xFF38BDF8) else Color(0xFF94A3B8),
            modifier = Modifier.size(28.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = Color(0xFF94A3B8)
                )
            )
        }

        RadioButton(
            selected = isSelected,
            onClick = onClick
        )
    }
}

@Composable
fun ConfigCityRow(
    city: CityWeather,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) Color(0xFF38BDF8) else Color(0xFF1E293B)
    val bgColor = if (isSelected) Color(0xFF0C4A6E).copy(alpha = 0.4f) else Color(0xFF1E293B).copy(alpha = 0.3f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Star,
            contentDescription = "Favorite",
            tint = Color(0xFFFACC15),
            modifier = Modifier.size(20.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = city.cityName.uppercase(),
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            )
            Text(
                text = city.weatherDetails.condition.displayName,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = Color(0xFF94A3B8)
                )
            )
        }

        Text(
            text = "${city.weatherDetails.currentTemp}°",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        )

        Spacer(modifier = Modifier.width(12.dp))

        RadioButton(
            selected = isSelected,
            onClick = onClick
        )
    }
}
