package com.example.data.api

import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object AiAssistantService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * Attempts to query Gemini API if API key exists; otherwise falls back gracefully to local intelligence.
     */
    suspend fun queryGemini(prompt: String, systemInstruction: String? = null): String = withContext(Dispatchers.IO) {
        val apiKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Exception) {
            ""
        }
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY" || apiKey == "PLACEHOLDER") {
            return@withContext ""
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"

        try {
            val root = JSONObject()
            
            if (systemInstruction != null) {
                val systemContent = JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().put("text", systemInstruction))
                    })
                }
                root.put("systemInstruction", systemContent)
            }

            val contentsArray = JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().put("text", prompt))
                    })
                })
            }
            root.put("contents", contentsArray)

            val requestBody = root.toString().toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext ""
                }
                val bodyString = response.body?.string() ?: return@withContext ""
                val jsonResponse = JSONObject(bodyString)
                val candidates = jsonResponse.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val firstCandidate = candidates.getJSONObject(0)
                    val content = firstCandidate.optJSONObject("content")
                    val parts = content?.optJSONArray("parts")
                    if (parts != null && parts.length() > 0) {
                        return@withContext parts.getJSONObject(0).optString("text", "").trim()
                    }
                }
                ""
            }
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Generates a context-rich weather summary locally (with cloud API fallback attempt).
     */
    suspend fun generateWeatherSummary(
        cityName: String,
        tempFormatted: String,
        condition: String,
        humidity: Int,
        windSpeed: Double,
        aqi: Int
    ): String {
        val cloudResult = queryGemini(
            prompt = "Generate daily summary for $cityName: $tempFormatted, $condition, humidity $humidity%, wind $windSpeed km/h, AQI $aqi.",
            systemInstruction = "Write an elegant, 2-sentence weather summary for $cityName."
        )
        if (cloudResult.isNotBlank()) return cloudResult

        // Instant local smart weather summary generation
        val condLower = condition.lowercase()
        val precipAdvice = if (condLower.contains("rain") || condLower.contains("storm")) {
            "Carry an umbrella and avoid unnecessary travel during heavy rain."
        } else if (windSpeed > 25.0) {
            "Expect strong winds throughout the day. Secure loose items."
        } else if (aqi >= 3) {
            "Air quality is moderately affected today. Consider wearing a protective mask."
        } else {
            "Enjoy clear, pleasant conditions for your daily travel and outdoor plans."
        }

        return "Current conditions in $cityName show $tempFormatted with $condition. $precipAdvice"
    }

    /**
     * Answers specific questions locally using available weather data.
     */
    suspend fun answerQuestion(
        question: String,
        cityName: String,
        tempFormatted: String,
        condition: String,
        humidity: Int,
        windSpeed: Double,
        aqi: Int
    ): String {
        val cloudResult = queryGemini(
            prompt = "Context for $cityName: Temp $tempFormatted, $condition, humidity $humidity%, wind $windSpeed km/h, AQI $aqi. Question: $question",
            systemInstruction = "Answer precisely in 2 concise sentences."
        )
        if (cloudResult.isNotBlank()) return cloudResult

        // Intelligent local Q&A engine
        val q = question.lowercase()
        val condLower = condition.lowercase()

        return when {
            q.contains("umbrella") || q.contains("rain") -> {
                if (condLower.contains("rain") || condLower.contains("storm") || humidity > 80) {
                    "Yes, carry an umbrella today in $cityName! Precipitation chances are high with $condition skies and $humidity% humidity."
                } else {
                    "No umbrella needed in $cityName right now. Skies are $condition with dry atmospheric conditions."
                }
            }
            q.contains("run") || q.contains("jog") || q.contains("exercise") -> {
                if (condLower.contains("rain") || condLower.contains("storm") || windSpeed > 30.0 || aqi >= 4) {
                    "Running outdoors is not recommended currently in $cityName due to $condition weather and wind speed of ${windSpeed.toInt()} km/h."
                } else {
                    "Great day for a run in $cityName! Temperatures are around $tempFormatted with safe air quality (AQI $aqi)."
                }
            }
            q.contains("wear") || q.contains("cloth") || q.contains("jacket") -> {
                "In $cityName ($tempFormatted, $condition), we recommend comfortable layers. ${if (windSpeed > 20) "A light jacket is advised for breezes." else "A standard t-shirt or shirt is perfect."}"
            }
            q.contains("travel") || q.contains("drive") || q.contains("drive") -> {
                if (condLower.contains("storm") || condLower.contains("fog") || windSpeed > 35.0) {
                    "Travel caution advised in $cityName. Sub-optimal road conditions due to $condition weather."
                } else {
                    "Travel conditions in $cityName are clear and safe. Good visibility and stable winds expected."
                }
            }
            else -> {
                "Currently in $cityName, it is $tempFormatted with $condition skies, $humidity% humidity, and wind speeds of ${windSpeed.toInt()} km/h. Overall weather is stable."
            }
        }
    }
}
