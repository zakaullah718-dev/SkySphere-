package com.example.ui.screens.map

import android.content.Context
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Production-ready Kotlin Tile Proxy using OkHttp.
 * Intercepts RainViewer tile requests and weather map layers in WebView,
 * serving them with explicit CORS headers (Access-Control-Allow-Origin: *)
 * and local caching. Completely bypasses Android WebView ORB (Opaque Response Blocking) errors.
 */
class RadarTileProxy(private val context: Context) {
    private val client: OkHttpClient by lazy {
        val cacheDir = File(context.cacheDir, "radar_tile_cache")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        val cacheSize = 50L * 1024L * 1024L // 50 MB disk cache for weather radar tiles

        OkHttpClient.Builder()
            .cache(Cache(cacheDir, cacheSize))
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    fun shouldIntercept(request: WebResourceRequest?): WebResourceResponse? {
        val url = request?.url?.toString() ?: return null

        // Only intercept HTTP/HTTPS tile or map resource requests
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return null
        }

        val isRainViewer = url.contains("rainviewer.com")
        val isWeatherTile = url.contains("openweathermap.org") || 
                           url.contains("cartocdn.com") || 
                           url.contains("fastly.net") ||
                           url.contains("maptiler.com") || 
                           url.contains("unpkg.com") ||
                           url.contains("openstreetmap.org") ||
                           url.contains("cdnjs.cloudflare.com") ||
                           url.contains(".png") || url.contains(".jpg") || url.contains(".webp")

        if (!isRainViewer && !isWeatherTile) {
            return null
        }

        return try {
            val okRequest = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 15; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
                .header("Accept", "image/png,image/webp,image/apng,image/*,*/*;q=0.8")
                .build()

            val response = client.newCall(okRequest).execute()
            val body = response.body
            if (response.isSuccessful && body != null) {
                val bytes = body.bytes()
                val contentType = body.contentType()?.toString() ?: "image/png"
                val mimeType = contentType.split(";")[0].trim()

                val headers = mutableMapOf(
                    "Access-Control-Allow-Origin" to "*",
                    "Access-Control-Allow-Methods" to "GET, OPTIONS, HEAD",
                    "Access-Control-Allow-Headers" to "*",
                    "Cache-Control" to "public, max-age=86400"
                )

                if (isRainViewer) {
                    Log.d("RadarTileProxy", "Radar tile loaded: $url")
                } else {
                    Log.d("RadarTileProxy", "Base map loaded: $url")
                }

                val encoding: String? = if (mimeType.startsWith("image/")) {
                    null
                } else if (mimeType.contains("javascript") || mimeType.contains("css") || mimeType.contains("json") || mimeType.contains("html") || mimeType.contains("text")) {
                    "UTF-8"
                } else {
                    null
                }

                WebResourceResponse(
                    mimeType,
                    encoding,
                    200,
                    "OK",
                    headers,
                    ByteArrayInputStream(bytes)
                )
            } else {
                if (isRainViewer) {
                    Log.w("RadarTileProxy", "Radar tile failed: $url (HTTP ${response.code})")
                } else {
                    Log.w("RadarTileProxy", "Base map failed: $url (HTTP ${response.code})")
                }
                null
            }
        } catch (e: Exception) {
            if (isRainViewer) {
                Log.e("RadarTileProxy", "Radar tile failed: $url - ${e.localizedMessage}")
            } else {
                Log.e("RadarTileProxy", "Base map failed: $url - ${e.localizedMessage}")
            }
            null
        }
    }
}
