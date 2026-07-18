package com.example.ui.screens.favorites

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.HeartBroken
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.models.CityWeather
import com.example.ui.components.SkySphereCard
import com.example.ui.components.WeatherConditionIcon

@Composable
fun FavoritesScreen(
    viewModel: FavoritesViewModel,
    onCitySelected: () -> Unit,
    modifier: Modifier = Modifier
) {
    val favorites by viewModel.favoriteCities.collectAsState()
    val isCelsius by viewModel.isCelsius.collectAsState()
    val isDark = MaterialTheme.colorScheme.background.value == 0xFF070913.toULong()

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("favorites_screen_container")
            .padding(horizontal = 20.dp)
            .padding(top = 24.dp)
    ) {
        // TITLE HEADER
        Text(
            text = "ATMOSPHERIC VAULT",
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Light,
                letterSpacing = 3.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
        )
        Text(
            text = "Your selected atmospheric profiles, saved for rapid retrieval.",
            style = MaterialTheme.typography.bodySmall.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )

        Spacer(modifier = Modifier.height(24.dp))

        // FAVORITES LIST
        if (favorites.isEmpty()) {
            EmptyVaultState()
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("favorites_list"),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                items(favorites, key = { it.cityName }) { city ->
                    FavoriteCityCard(
                        city = city,
                        isCelsius = isCelsius,
                        onSelect = {
                            viewModel.selectCity(city.cityName)
                            onCitySelected()
                        },
                        onRemove = { viewModel.removeFavorite(city.cityName) }
                    )
                }
            }
        }
    }
}

@Composable
fun FavoriteCityCard(
    city: CityWeather,
    isCelsius: Boolean,
    onSelect: () -> Unit,
    onRemove: () -> Unit
) {
    val details = city.weatherDetails
    val isDark = MaterialTheme.colorScheme.background.value == 0xFF070913.toULong()

    val cardBrush = Brush.linearGradient(
        colors = listOf(
            details.condition.startColor.copy(alpha = if (isDark) 0.12f else 0.05f),
            details.condition.endColor.copy(alpha = if (isDark) 0.03f else 0.01f)
        )
    )

    SkySphereCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("favorite_card_${city.cityName.lowercase()}"),
        onClick = onSelect
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(cardBrush)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Part: Weather Icon + Name & Country
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    WeatherConditionIcon(
                        condition = details.condition,
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = city.cityName,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = city.country,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }

                // Right Part: Big Temp, Condition details + Delete Action
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Text(
                            text = "${formatTemp(details.currentTemp, isCelsius)}°",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Light,
                                color = MaterialTheme.colorScheme.onBackground,
                                fontSize = 32.sp,
                                letterSpacing = (-1).sp
                            )
                        )
                        Text(
                            text = details.condition.displayName,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium,
                                fontSize = 11.sp
                            )
                        )
                    }

                    IconButton(
                        onClick = onRemove,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(0x1AFF5252))
                            .testTag("favorite_remove_button_${city.cityName.lowercase()}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Favorite,
                            contentDescription = "Remove from Vault",
                            tint = Color(0xFFFF5252),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyVaultState() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("favorites_empty_state")
            .padding(bottom = 120.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.HeartBroken,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "THE VAULT IS EMPTY",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Select any weather profile in the Explore screen and touch the favorite icon to save their complete atmospheric parameters here for immediate access.",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp
                ),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

private fun formatTemp(tempF: Int, isCelsius: Boolean): String {
    return if (isCelsius) {
        val tempC = ((tempF - 32) * 5 / 9)
        "$tempC"
    } else {
        "$tempF"
    }
}
