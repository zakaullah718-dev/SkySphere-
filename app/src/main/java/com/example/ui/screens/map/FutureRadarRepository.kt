package com.example.ui.screens.map

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class FutureRadarRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var cachedTimestamp: Long = 0L

    @Volatile
    private var lastFetchTime: Long = 0L

    suspend fun getLatestRadarTimestamp(): Long = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        // Cache for 3 minutes (180,000 ms)
        if (cachedTimestamp > 0L && (now - lastFetchTime) < 180_000L) {
            return@withContext cachedTimestamp
        }

        try {
            val request = Request.Builder()
                .url("https://api.rainviewer.com/public/weather-maps.json")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyString = response.body?.string()
                    if (!bodyString.isNullOrBlank()) {
                        val json = JSONObject(bodyString)
                        val radarObj = json.optJSONObject("radar")
                        val pastArray = radarObj?.optJSONArray("past")
                        if (pastArray != null && pastArray.length() > 0) {
                            val latestItem = pastArray.getJSONObject(pastArray.length() - 1)
                            val time = latestItem.optLong("time", 0L)
                            if (time > 0L) {
                                cachedTimestamp = time
                                lastFetchTime = now
                                Log.d("RainRadarRepo", "Fetched latest RainViewer timestamp: $time")
                                return@withContext time
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("RainRadarRepo", "Error fetching RainViewer timestamp: ${e.localizedMessage}")
        }

        // Fallback: 10 minutes ago timestamp (in seconds)
        val fallbackTime = ((now - 600_000L) / 600_000L) * 600L
        cachedTimestamp = fallbackTime
        lastFetchTime = now
        fallbackTime
    }

    fun getFallbackTimestamp(): Long {
        val now = System.currentTimeMillis()
        if (cachedTimestamp > 0L) return cachedTimestamp
        return ((now - 600_000L) / 600_000L) * 600L
    }
}
