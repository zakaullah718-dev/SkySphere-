package com.example.weather.data.api

import com.example.weather.data.models.LocationDto
import com.example.weather.data.models.WeatherResponseDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Retrofit service interface defining the HTTP GET requests for the Weather Provider.
 */
interface WeatherApiService {
    
    @GET(ApiConstants.ENDPOINT_FORECAST)
    suspend fun getForecast(
        @Query(ApiConstants.PARAM_KEY) apiKey: String,
        @Query(ApiConstants.PARAM_QUERY) query: String,
        @Query(ApiConstants.PARAM_DAYS) days: Int = ApiConstants.DEFAULT_FORECAST_DAYS,
        @Query(ApiConstants.PARAM_AQI) aqi: String = ApiConstants.DEFAULT_AQI_ENABLED
    ): Response<WeatherResponseDto>

    @GET(ApiConstants.ENDPOINT_SEARCH)
    suspend fun searchLocations(
        @Query(ApiConstants.PARAM_KEY) apiKey: String,
        @Query(ApiConstants.PARAM_QUERY) query: String
    ): Response<List<LocationDto>>
}
