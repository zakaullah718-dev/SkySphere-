package com.example.ui.screens.search

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
fun SearchScreen(
    viewModel: SearchViewModel,
    onCitySelected: () -> Unit,
    modifier: Modifier = Modifier
) {
    val query by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isCelsius by viewModel.isCelsius.collectAsState()
    val isDark = MaterialTheme.colorScheme.background.value == 0xFF070913.toULong()

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("search_screen_container")
            .padding(horizontal = 20.dp)
            .padding(top = 24.dp)
    ) {
        // SCREEN TITLE
        Text(
            text = "EXPLORE SPHERES",
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Light,
                letterSpacing = 3.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
        )
        Text(
            text = "Search global atmospheric metrics and weather profiles.",
            style = MaterialTheme.typography.bodySmall.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )

        Spacer(modifier = Modifier.height(20.dp))

        // SEARCH TEXT FIELD
        OutlinedTextField(
            value = query,
            onValueChange = { viewModel.onSearchQueryChanged(it) },
            placeholder = {
                Text(
                    text = "Search city or country...",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear text",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            singleLine = true,
            shape = CircleShape,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = if (isDark) Color(0xFF13172E) else Color(0xFFFFFFFF),
                unfocusedContainerColor = if (isDark) Color(0xFF13172E) else Color(0xFFFFFFFF),
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = if (isDark) Color(0xFF1D2447) else Color(0xFFE2E8F0),
                focusedTextColor = MaterialTheme.colorScheme.onBackground,
                unfocusedTextColor = MaterialTheme.colorScheme.onBackground
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag("search_text_input")
        )

        Spacer(modifier = Modifier.height(24.dp))

        // SEARCH RESULTS
        if (searchResults.isEmpty()) {
            EmptySearchState(query = query)
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("search_results_list"),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                items(searchResults, key = { it.cityName }) { city ->
                    SearchCityCard(
                        city = city,
                        isCelsius = isCelsius,
                        onSelect = {
                            viewModel.selectCity(city.cityName)
                            onCitySelected()
                        },
                        onToggleFavorite = { viewModel.toggleFavorite(city.cityName) }
                    )
                }
            }
        }
    }
}

@Composable
fun SearchCityCard(
    city: CityWeather,
    isCelsius: Boolean,
    onSelect: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val details = city.weatherDetails
    val isDark = MaterialTheme.colorScheme.background.value == 0xFF070913.toULong()

    // Smooth subtle side-to-side gradient for visual luxury
    val cardBrush = Brush.linearGradient(
        colors = listOf(
            details.condition.startColor.copy(alpha = if (isDark) 0.08f else 0.04f),
            details.condition.endColor.copy(alpha = if (isDark) 0.02f else 0.01f)
        )
    )

    SkySphereCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("search_city_card_${city.cityName.lowercase()}"),
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
                // Left Column: City, Country, Weather Condition Text
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    WeatherConditionIcon(
                        condition = details.condition,
                        modifier = Modifier.size(38.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = city.cityName,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = city.country,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = details.condition.displayName,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                }

                // Right Section: Temperature + Favorite Action Button
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
                                fontSize = 28.sp
                            )
                        )
                        Text(
                            text = "H: ${formatTemp(details.highTemp, isCelsius)}° L: ${formatTemp(details.lowTemp, isCelsius)}°",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp
                            )
                        )
                    }

                    IconButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(if (isDark) Color(0xFF1E254C) else Color(0xFFE2E8F0))
                            .testTag("search_favorite_toggle_${city.cityName.lowercase()}")
                    ) {
                        Icon(
                            imageVector = if (city.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = "Favorite Toggle",
                            tint = if (city.isFavorite) Color(0xFFFF5252) else MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EmptySearchState(query: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("search_empty_state")
            .padding(bottom = 120.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = if (query.isEmpty()) "EXPLORE GLOBAL SPHERES" else "ATMOSPHERE NOT FOUND",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (query.isEmpty()) {
                    "Type a global city name above to inspect dynamic high-fidelity atmosphere profiles and weather forecasts."
                } else {
                    "We couldn't find any weather spheres matching '$query'. Check spelling or search a different global center."
                },
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
