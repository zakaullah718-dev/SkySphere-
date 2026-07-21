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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
    val recentSearches by viewModel.recentSearches.collectAsState()
    val errorState by viewModel.errorState.collectAsState()
    val isDark = true

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

        // ERROR NOTIFICATION BANNER
        if (errorState != null) {
            SkySphereCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .testTag("search_error_banner"),
                onClick = { viewModel.clearError() }
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
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Warning",
                            tint = Color(0xFFFF5252),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = errorState!!,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        )
                    }
                    IconButton(onClick = { viewModel.clearError() }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss error",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

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
                focusedContainerColor = Color(0xFF1E1E2E), // Solid accessible dark gray card background (as requested)
                unfocusedContainerColor = Color(0xFF1E1E2E),
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color(0xFF374151), // Solid high-contrast border for high accessibility (as requested)
                focusedTextColor = MaterialTheme.colorScheme.onBackground,
                unfocusedTextColor = MaterialTheme.colorScheme.onBackground
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag("search_text_input")
        )

        Spacer(modifier = Modifier.height(24.dp))

        // CONDITIONAL RENDERING: Recent Searches history vs Search Results list
        if (query.isEmpty() && recentSearches.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "RECENT SEARCHES",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                )
                TextButton(
                    onClick = { viewModel.clearRecentSearches() },
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(
                        text = "CLEAR ALL",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color(0xFFFF5252),
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(recentSearches) { searchItem ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .background(Color(0xFF1E1E2E)) // Solid accessible dark gray card background (as requested)
                            .clickable {
                                viewModel.selectCity(searchItem)
                                onCitySelected()
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = searchItem,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            )
                        }
                        IconButton(
                            onClick = { viewModel.deleteRecentSearch(searchItem) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete search",
                                tint = Color(0xFFFF5252).copy(alpha = 0.8f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

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
                items(searchResults, key = { city -> "${city.cityName},${city.region ?: ""},${city.country}_${searchResults.indexOf(city)}" }) { city ->
                    SearchCityCard(
                        city = city,
                        isCelsius = isCelsius,
                        onSelect = {
                            val selectQuery = if (city.latitude != null && city.longitude != null) {
                                "COORDS:${city.latitude},${city.longitude}|${city.cityName}|${city.region ?: ""}|${city.country}"
                            } else {
                                city.cityName
                            }
                            viewModel.selectCity(selectQuery)
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
    val isDark = true

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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val locationSubtitle = if (!city.region.isNullOrBlank()) {
                                "${city.region}, ${city.country}"
                            } else {
                                city.country
                            }
                            Text(
                                text = locationSubtitle,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                            if (!city.localTime.isNullOrBlank()) {
                                Text(
                                    text = "  •  " + city.localTime,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                            }
                        }
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

                // Right Section: Temperature + Favorite Action Button (Visible if weatherDetails have values)
                if (details.currentTemp != 0) {
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
                    "No matching city found. Please check the spelling."
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
