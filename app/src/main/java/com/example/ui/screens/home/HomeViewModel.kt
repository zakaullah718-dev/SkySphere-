package com.example.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.models.CityWeather
import com.example.data.repository.WeatherRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(
    private val repository: WeatherRepository
) : ViewModel() {

    // Active selected city flow
    val selectedCityWeather: StateFlow<CityWeather> = repository.selectedCity
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = repository.selectedCity.value
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
        repository.selectLocationCoordinates(latitude, longitude)
    }

    fun refreshActiveCity() {
        viewModelScope.launch {
            repository.forceRefreshActiveCity()
        }
    }
}
