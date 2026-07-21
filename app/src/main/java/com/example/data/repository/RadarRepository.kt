package com.example.data.repository

import android.util.Log
import com.example.data.api.RadarService
import com.example.data.models.RadarFrame
import com.example.data.models.RadarTimeline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

sealed class RadarState {
    object Loading : RadarState()
    data class Success(val timeline: RadarTimeline) : RadarState()
    data class Error(val message: String) : RadarState()
}

class RadarRepository(
    private val radarService: RadarService = RadarService()
) {
    private val _radarState = MutableStateFlow<RadarState>(RadarState.Loading)
    val radarState: StateFlow<RadarState> = _radarState.asStateFlow()

    private var cachedTimeline: RadarTimeline? = null

    suspend fun fetchRadarTimeline(forceRefresh: Boolean = false): RadarTimeline? = withContext(Dispatchers.IO) {
        if (!forceRefresh && cachedTimeline != null) {
            val ageMs = System.currentTimeMillis() - (cachedTimeline!!.generatedAt * 1000L)
            if (ageMs < 10 * 60 * 1000L) { // 10 minutes cache
                Log.d("RadarRepository", "Returning cached radar timeline")
                return@withContext cachedTimeline
            }
        }

        _radarState.value = RadarState.Loading
        var attempts = 0
        val maxAttempts = 3
        var lastException: Throwable? = null

        while (attempts < maxAttempts) {
            attempts++
            val result = radarService.fetchWeatherMaps()
            if (result.isSuccess) {
                val response = result.getOrNull()
                if (response != null && response.radar != null) {
                    val host = response.host ?: "https://tilecache.rainviewer.com"
                    val rawPast = response.radar.past ?: emptyList()
                    val rawNowcast = response.radar.nowcast ?: emptyList()

                    val pastFrames = rawPast.map { RadarFrame(time = it.time, path = it.path, isForecast = false) }
                    val nowcastFrames = rawNowcast.map { RadarFrame(time = it.time, path = it.path, isForecast = true) }

                    val compiled7 = compile7StepTimeline(pastFrames, nowcastFrames)

                    val timeline = RadarTimeline(
                        host = host,
                        pastFrames = pastFrames,
                        nowcastFrames = nowcastFrames,
                        compiled7TimelineFrames = compiled7,
                        generatedAt = response.generated ?: (System.currentTimeMillis() / 1000L)
                    )

                    cachedTimeline = timeline
                    _radarState.value = RadarState.Success(timeline)
                    Log.d("RadarRepository", "Radar timeline successfully loaded and cached with ${compiled7.size} steps.")
                    return@withContext timeline
                }
            } else {
                lastException = result.exceptionOrNull()
                Log.w("RadarRepository", "Radar fetch attempt $attempts failed: ${lastException?.localizedMessage}. Retrying...")
                if (attempts < maxAttempts) {
                    delay(1000L * attempts)
                }
            }
        }

        val errorMsg = "Radar temporarily unavailable"
        Log.e("RadarRepository", "$errorMsg after $maxAttempts attempts. Cause: ${lastException?.localizedMessage}")
        _radarState.value = RadarState.Error(errorMsg)
        return@withContext null
    }

    private fun compile7StepTimeline(
        past: List<RadarFrame>,
        nowcast: List<RadarFrame>
    ): List<RadarFrame> {
        if (past.isEmpty() && nowcast.isEmpty()) return emptyList()

        val compiled = mutableListOf<RadarFrame>()
        val nowIndex = if (past.isNotEmpty()) past.size - 1 else 0
        val nowFrame = if (past.isNotEmpty()) past[nowIndex] else nowcast.first()

        // Step 0: -3h (approx 18 frames back @ 10-min intervals)
        val s0 = if (past.isNotEmpty()) past[Math.max(0, nowIndex - 18)] else nowFrame
        // Step 1: -2h (approx 12 frames back)
        val s1 = if (past.isNotEmpty()) past[Math.max(0, nowIndex - 12)] else nowFrame
        // Step 2: -1h (approx 6 frames back)
        val s2 = if (past.isNotEmpty()) past[Math.max(0, nowIndex - 6)] else nowFrame
        // Step 3: Now
        val s3 = nowFrame

        // Forecast steps from nowcast array (if available)
        val s4 = when {
            nowcast.size > 5 -> nowcast[5]
            nowcast.isNotEmpty() -> nowcast.last()
            else -> nowFrame
        }
        val s5 = when {
            nowcast.size > 11 -> nowcast[11]
            nowcast.isNotEmpty() -> nowcast.last()
            else -> nowFrame
        }
        val s6 = when {
            nowcast.size > 17 -> nowcast[17]
            nowcast.isNotEmpty() -> nowcast.last()
            else -> nowFrame
        }

        compiled.add(s0)
        compiled.add(s1)
        compiled.add(s2)
        compiled.add(s3)
        compiled.add(s4)
        compiled.add(s5)
        compiled.add(s6)

        return compiled
    }
}
