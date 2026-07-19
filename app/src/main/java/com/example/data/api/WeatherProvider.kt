package com.example.data.api

import com.example.data.models.CityWeather

interface WeatherProvider {
    /**
     * Fetch weather details for a given query (city name or "lat,lon" coordinates).
     */
    suspend fun getForecast(query: String): Result<CityWeather>

    /**
     * Search for locations matching a query string (for autocompleting searches).
     */
    suspend fun searchLocations(query: String): Result<List<CityWeather>>
}
