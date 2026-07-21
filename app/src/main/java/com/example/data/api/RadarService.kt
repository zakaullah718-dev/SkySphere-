package com.example.data.api

import android.util.Log
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class RainViewerResponse(
    @Json(name = "version") val version: String? = null,
    @Json(name = "generated") val generated: Long? = null,
    @Json(name = "host") val host: String? = null,
    @Json(name = "radar") val radar: RainViewerRadarData? = null,
    @Json(name = "satellite") val satellite: RainViewerSatelliteData? = null
)

@JsonClass(generateAdapter = true)
data class RainViewerRadarData(
    @Json(name = "past") val past: List<RainViewerFrame>? = null,
    @Json(name = "nowcast") val nowcast: List<RainViewerFrame>? = null
)

@JsonClass(generateAdapter = true)
data class RainViewerSatelliteData(
    @Json(name = "infrared") val infrared: List<RainViewerFrame>? = null
)

@JsonClass(generateAdapter = true)
data class RainViewerFrame(
    @Json(name = "time") val time: Long,
    @Json(name = "path") val path: String
)

interface RainViewerApi {
    @GET("public/weather-maps.json")
    suspend fun getWeatherMaps(): RainViewerResponse
}

class RadarService {
    private val api: RainViewerApi

    init {
        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()

        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.rainviewer.com/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

        api = retrofit.create(RainViewerApi::class.java)
        Log.d("RadarService", "Radar initialized")
    }

    suspend fun fetchWeatherMaps(): Result<RainViewerResponse> {
        return try {
            val response = api.getWeatherMaps()
            Log.d("RadarService", "Timeline downloaded: generated at ${response.generated}")
            Result.success(response)
        } catch (e: Exception) {
            Log.e("RadarService", "Failed to fetch weather maps timeline: ${e.localizedMessage}")
            Result.failure(e)
        }
    }
}
