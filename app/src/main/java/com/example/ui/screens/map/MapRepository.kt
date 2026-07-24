package com.example.ui.screens.map

import com.example.data.repository.WeatherRepository

class MapRepository(
    private val weatherRepository: WeatherRepository
) {
    fun getDefaultZoom(): Double = 2.5
    fun getDefaultCenter(): Pair<Double, Double> = Pair(20.0, 0.0)
}
