package com.example.ui.screens.map

import android.content.Context
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * High-performance WebView-based MapLibre GL JS map provider implementation.
 * Ensures zero-native binary compilation overhead while maintaining smooth WebGL 60fps rendering.
 */
class MapLibreWebViewProvider : RadarMapProvider {
    private var webView: WebView? = null

    override fun createMapView(
        context: Context,
        onMapLoaded: () -> Unit,
        onCoordinatesSelected: (Double, Double) -> Unit,
        onApiKeyMissing: (String) -> Unit
    ): View {
        // Pre-create WebView Default HTTP Cache directories to avoid benign Chromium console "opendir" warnings
        try {
            val cacheDir = context.cacheDir
            if (cacheDir != null) {
                val jsCache = java.io.File(cacheDir, "WebView/Default/HTTP Cache/Code Cache/js")
                val wasmCache = java.io.File(cacheDir, "WebView/Default/HTTP Cache/Code Cache/wasm")
                if (!jsCache.exists()) jsCache.mkdirs()
                if (!wasmCache.exists()) wasmCache.mkdirs()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val newWebView = WebView(context).apply {
            // WebGL and hardware acceleration settings for ultra-smooth fluid experience
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                builtInZoomControls = false
                displayZoomControls = false
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                cacheMode = WebSettings.LOAD_DEFAULT
                allowContentAccess = true
                allowFileAccess = true
                allowFileAccessFromFileURLs = true
                allowUniversalAccessFromFileURLs = true
            }
            
            // Enforce hardware acceleration at the Webview container level
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
                    consoleMessage?.let {
                        android.util.Log.d("RadarWebView", "[${it.messageLevel()}] ${it.message()} -- at ${it.sourceId()}:${it.lineNumber()}")
                    }
                    return super.onConsoleMessage(consoleMessage)
                }
            }
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                }
            }
            
            // Safely interface between JS and Kotlin Compose state
            addJavascriptInterface(object {
                @JavascriptInterface
                fun onMapLoaded() {
                    post { onMapLoaded() }
                }

                @JavascriptInterface
                fun onCoordinatesSelected(latitude: Double, longitude: Double) {
                    post { onCoordinatesSelected(latitude, longitude) }
                }

                @JavascriptInterface
                fun onApiKeyMissing(layerName: String) {
                    post { onApiKeyMissing(layerName) }
                }
            }, "AndroidMap")

            loadUrl("file:///android_asset/radar_map.html")
        }
        
        webView = newWebView
        return newWebView
    }

    override fun setCenter(latitude: Double, longitude: Double, zoom: Float?) {
        val zoomParam = zoom?.toString() ?: "null"
        webView?.post {
            webView?.evaluateJavascript("if (typeof setCenter === 'function') { setCenter($latitude, $longitude, $zoomParam); }", null)
        }
    }

    override fun setWeatherLayer(layer: MapLayer) {
        webView?.post {
            webView?.evaluateJavascript("if (typeof setWeatherLayer === 'function') { setWeatherLayer('${layer.name}'); }", null)
        }
    }

    override fun setWeatherApiKey(apiKey: String) {
        webView?.post {
            webView?.evaluateJavascript("if (typeof setWeatherApiKey === 'function') { setWeatherApiKey('$apiKey'); }", null)
        }
    }

    override fun setMapTilerApiKey(apiKey: String) {
        webView?.post {
            webView?.evaluateJavascript("if (typeof setMapTilerApiKey === 'function') { setMapTilerApiKey('$apiKey'); }", null)
        }
    }
}
