package com.example.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.models.CityWeather
import com.example.data.repository.WeatherRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(
    private val repository: WeatherRepository
) : ViewModel() {

    // Error State management for beautiful screens
    private val _errorState = MutableStateFlow<String?>(null)
    val errorState: StateFlow<String?> = _errorState.asStateFlow()

    fun clearError() {
        _errorState.value = null
        repository.clearRepositoryError()
    }

    init {
        viewModelScope.launch {
            repository.repositoryError.collect { error ->
                if (error != null) {
                    _errorState.value = error
                }
            }
        }
    }

    val isGpsActive: StateFlow<Boolean> = repository.isGpsActive
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = repository.isGpsActive.value
        )

    // Active selected city flow
    val selectedCityWeather: StateFlow<CityWeather> = repository.selectedCity
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = repository.selectedCity.value
        )

    val allCities: StateFlow<List<CityWeather>> = repository.getCitiesFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val isCelsius: StateFlow<Boolean> = repository.isCelsius
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = repository.isCelsius.value
        )

    val windUnit: StateFlow<String> = repository.windUnit
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = repository.windUnit.value
        )

    fun toggleFavorite(cityName: String) {
        repository.toggleFavorite(cityName)
    }

    fun loadWeatherForCurrentLocation(latitude: Double, longitude: Double) {
        viewModelScope.launch {
            try {
                repository.selectLocationCoordinates(latitude, longitude)
                clearError()
            } catch (e: Exception) {
                _errorState.value = "GPS Location reverse geocoding failed. Please check internet access or try searching manually."
            }
        }
    }

    fun refreshActiveCity() {
        viewModelScope.launch {
            try {
                repository.forceRefreshActiveCity()
                clearError()
            } catch (e: Exception) {
                _errorState.value = "Unable to contact meteorological telemetry servers. Please verify internet connectivity."
            }
        }
    }

    fun setError(message: String) {
        _errorState.value = message
    }
}
