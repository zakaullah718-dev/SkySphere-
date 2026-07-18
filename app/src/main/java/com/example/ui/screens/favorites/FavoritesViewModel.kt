package com.example.ui.screens.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.models.CityWeather
import com.example.data.repository.WeatherRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class FavoritesViewModel(
    private val repository: WeatherRepository
) : ViewModel() {

    // Hot flow representing favorite cities
    val favoriteCities: StateFlow<List<CityWeather>> = repository.getFavoritesFlow()
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

    fun selectCity(cityName: String) {
        repository.selectCity(cityName)
    }

    fun removeFavorite(cityName: String) {
        repository.toggleFavorite(cityName)
    }
}
