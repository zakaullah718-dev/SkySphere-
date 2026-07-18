package com.example.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.models.CityWeather
import com.example.data.repository.WeatherRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SearchViewModel(
    private val repository: WeatherRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _liveSearchResults = MutableStateFlow<List<CityWeather>>(emptyList())

    val isCelsius: StateFlow<Boolean> = repository.isCelsius
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = repository.isCelsius.value
        )

    // Combined state flow: merges filtered pre-seeded fallback profiles with remote API hits
    val searchResults: StateFlow<List<CityWeather>> = combine(
        _searchQuery,
        repository.getCitiesFlow(),
        _liveSearchResults
    ) { query, cities, liveCities ->
        if (query.isBlank()) {
            cities
        } else {
            val filteredLocal = cities.filter {
                it.cityName.contains(query, ignoreCase = true) ||
                it.country.contains(query, ignoreCase = true)
            }
            // Combine local matching with live searched hits, distinct by city name
            (filteredLocal + liveCities).distinctBy { it.cityName.lowercase() }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = repository.searchCities("")
    )

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        if (query.isNotBlank() && query.length >= 2) {
            viewModelScope.launch {
                val results = repository.searchLocationsAndFetch(query)
                _liveSearchResults.value = results
            }
        } else {
            _liveSearchResults.value = emptyList()
        }
    }

    fun selectCity(cityName: String) {
        repository.selectCity(cityName)
    }

    fun toggleFavorite(cityName: String) {
        repository.toggleFavorite(cityName)
    }
}
