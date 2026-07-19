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

    // Room-backed Recent Searches flow
    val recentSearches: StateFlow<List<String>> = repository.getRecentSearchesFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Error State management
    private val _errorState = MutableStateFlow<String?>(null)
    val errorState: StateFlow<String?> = _errorState.asStateFlow()

    fun clearError() {
        _errorState.value = null
    }

    val isCelsius: StateFlow<Boolean> = repository.isCelsius
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = repository.isCelsius.value
        )

    // Combined state flow: merges cached/favorite cities with live searched locations
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
            // Prioritize live api hits combined with matching local caches
            (filteredLocal + liveCities).distinctBy { it.cityName.lowercase() }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = repository.searchCities("")
    )

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        clearError()
        if (query.isNotBlank() && query.length >= 2) {
            viewModelScope.launch {
                try {
                    val results = repository.searchLocationsAndFetch(query)
                    if (results.isEmpty()) {
                        _errorState.value = "Atmosphere resolution failed. The requested location '$query' could not be resolved."
                    } else {
                        _liveSearchResults.value = results
                        clearError()
                    }
                } catch (e: Exception) {
                    _errorState.value = "Network or server connection timeout. Please check your network and try again."
                }
            }
        } else {
            _liveSearchResults.value = emptyList()
        }
    }

    fun selectCity(cityName: String) {
        viewModelScope.launch {
            try {
                repository.selectCity(cityName)
                repository.saveRecentSearch(cityName)
                clearError()
            } catch (e: Exception) {
                _errorState.value = "Unable to fetch complete forecast for $cityName. Please try again."
            }
        }
    }

    fun toggleFavorite(cityName: String) {
        repository.toggleFavorite(cityName)
    }

    fun deleteRecentSearch(query: String) {
        viewModelScope.launch {
            repository.deleteRecentSearch(query)
        }
    }

    fun clearRecentSearches() {
        viewModelScope.launch {
            repository.clearRecentSearches()
        }
    }
}
