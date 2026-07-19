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
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * Sends a direct POST request to the Gemini API using the gemini-3.5-flash model.
     */
    suspend fun queryGemini(prompt: String, systemInstruction: String? = null): String = withContext(Dispatchers.IO) {
        val apiKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Exception) {
            ""
        }
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY" || apiKey == "PLACEHOLDER") {
            return@withContext "AI assistance is currently offline. Please configure your GEMINI_API_KEY in the Secrets panel."
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
                    return@withContext "AI response unavailable (Code: ${response.code})."
                }
                val bodyString = response.body?.string() ?: return@withContext "Empty response content."
                val jsonResponse = JSONObject(bodyString)
                val candidates = jsonResponse.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val firstCandidate = candidates.getJSONObject(0)
                    val content = firstCandidate.optJSONObject("content")
                    val parts = content?.optJSONArray("parts")
                    if (parts != null && parts.length() > 0) {
                        return@withContext parts.getJSONObject(0).optString("text", "No text received.").trim()
                    }
                }
                "No insights available."
            }
        } catch (e: Exception) {
            "Connection failed: ${e.localizedMessage ?: "Unknown network exception"}."
        }
    }

    /**
     * Generates a context-rich, elegant weather summary using Gemini.
     */
    suspend fun generateWeatherSummary(
        cityName: String,
        tempFormatted: String,
        condition: String,
        humidity: Int,
        windSpeed: Double,
        aqi: Int
    ): String {
        val systemInstruction = "You are SkySphere's Premium AI Weather summary generator. " +
                "Write an elegant, engaging 2-sentence summary of the weather. Highlight the temperature, " +
                "the atmosphere, and recommend a small daily action. Keep it premium, poetic but concise, and strictly brief."
        val prompt = "Generate a daily summary for $cityName. Current: $tempFormatted, skies are $condition, " +
                "humidity is $humidity%, wind currents are $windSpeed km/h, air quality index is $aqi."
        return queryGemini(prompt, systemInstruction)
    }

    /**
     * Answers specific questions using local weather context.
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
        val systemInstruction = "You are SkySphere's Premium AI Weather Advisor. " +
                "Provide a beautifully written, precise answer to the user's weather question using the local weather data provided. " +
                "Always relate your response to the weather in $cityName. Keep it under 3-4 sentences, encouraging, and highly helpful."
        val prompt = "Context weather details for $cityName: Temp is $tempFormatted, skies are $condition, " +
                "humidity is $humidity%, wind currents are $windSpeed km/h, AQI is $aqi. Question: $question"
        return queryGemini(prompt, systemInstruction)
    }
}
