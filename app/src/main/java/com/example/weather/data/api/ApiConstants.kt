package com.example.weather.data.api

/**
 * Constants file for remote Weather API configuration.
 */
object ApiConstants {
    const val BASE_URL = "https://api.weatherapi.com/v1/"
    
    const val ENDPOINT_FORECAST = "forecast.json"
    const val ENDPOINT_SEARCH = "search.json"
    
    const val PARAM_KEY = "key"
    const val PARAM_QUERY = "q"
    const val PARAM_DAYS = "days"
    const val PARAM_AQI = "aqi"
    
    const val DEFAULT_FORECAST_DAYS = 7
    const val DEFAULT_AQI_ENABLED = "yes"
    
    const val TIMEOUT_CONNECT_SECONDS = 15L
    const val TIMEOUT_READ_SECONDS = 15L
}
