package com.example.ui.screens.map

import android.content.Context
import android.os.Build
import android.util.Log
import android.view.View
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * Modern, simple, hardware-accelerated WebView map provider for MapLibre GL JS.
 * Connects directly to CartoDB Dark Matter basemaps and RainViewer Doppler radar layers.
 */
class MapLibreWebViewProvider : RadarMapProvider {
    private var webView: WebView? = null
    var controller: RadarMapController? = null

    override fun createMapView(
        context: Context,
        onMapLoaded: () -> Unit,
        onCoordinatesSelected: (Double, Double) -> Unit,
        onApiKeyMissing: (String) -> Unit
    ): View {
        val wv = WebView(context).apply {
            // Configure simple, modern WebView settings
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
                javaScriptCanOpenWindowsAutomatically = true
                setSupportMultipleWindows(false)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    safeBrowsingEnabled = false
                }
            }

            // Enable cookies and hardware acceleration
            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(this, true)
            setLayerType(View.LAYER_TYPE_HARDWARE, null)

            // Layout size listener to notify MapLibre canvas on resize/orientation
            addOnLayoutChangeListener { v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                val newWidth = right - left
                val newHeight = bottom - top
                val oldWidth = oldRight - oldLeft
                val oldHeight = oldBottom - oldTop
                if (newWidth > 0 && newHeight > 0 && (newWidth != oldWidth || newHeight != oldHeight)) {
                    v.postDelayed({
                        (v as? WebView)?.evaluateJavascript("if (typeof invalidateMapSize === 'function') invalidateMapSize();", null)
                    }, 100)
                }
            }

            // Standard WebChromeClient for JS console debugging
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
                    consoleMessage?.let {
                        Log.d("SkySphereMap", "[${it.messageLevel()}] ${it.message()} (${it.sourceId()}:${it.lineNumber()})")
                    }
                    return super.onConsoleMessage(consoleMessage)
                }
            }

            // Simple WebViewClient without unnecessary request proxying
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    view?.postDelayed({
                        view.evaluateJavascript("if (typeof invalidateMapSize === 'function') invalidateMapSize();", null)
                    }, 150)
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    super.onReceivedError(view, request, error)
                    Log.e("SkySphereMap", "WebView resource error: ${error?.description} for ${request?.url}")
                }
            }

            // Add JavaScript interfaces for Kotlin <-> MapLibre JS bridge
            addJavascriptInterface(
                MapAppInterface(
                    onMapLoaded = {
                        post {
                            evaluateJavascript("if (typeof invalidateMapSize === 'function') invalidateMapSize();", null)
                        }
                        onMapLoaded.invoke()
                    },
                    onCoordinatesSelected = onCoordinatesSelected,
                    onApiKeyMissing = onApiKeyMissing,
                    onRequestTimelineRefresh = {
                        controller?.let { ctrl ->
                            post {
                                ctrl.loadRadarTimeline(kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main), forceRefresh = true)
                            }
                        }
                    }
                ),
                "AndroidMap"
            )

            // Load local radar_map.html asset
            val htmlContent = try {
                context.assets.open("radar_map.html").bufferedReader().use { it.readText() }
            } catch (e: Exception) {
                Log.e("SkySphereMap", "Failed to load radar_map.html from assets", e)
                ""
            }

            loadDataWithBaseURL(
                "file:///android_asset/",
                htmlContent,
                "text/html",
                "utf-8",
                null
            )
        }

        webView = wv
        return wv
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

    override fun setTimelineIndex(index: Int) {
        webView?.post {
            webView?.evaluateJavascript("if (typeof setTimelineIndex === 'function') { setTimelineIndex($index); }", null)
        }
    }

    fun setRadarOpacity(opacity: Float) {
        webView?.post {
            webView?.evaluateJavascript("if (typeof setRadarOpacity === 'function') { setRadarOpacity($opacity); }", null)
        }
    }

    fun updateRadarTimeline(jsonPayloadStr: String) {
        webView?.post {
            webView?.evaluateJavascript("if (typeof updateRadarTimeline === 'function') { updateRadarTimeline($jsonPayloadStr); }", null)
        }
    }

    fun notifyRadarUnavailable(message: String) {
        webView?.post {
            webView?.evaluateJavascript("if (typeof showRadarUnavailable === 'function') { showRadarUnavailable('$message'); }", null)
        }
    }

    fun zoomIn() {
        webView?.post {
            webView?.evaluateJavascript("if (typeof zoomIn === 'function') { zoomIn(); }", null)
        }
    }

    fun zoomOut() {
        webView?.post {
            webView?.evaluateJavascript("if (typeof zoomOut === 'function') { zoomOut(); }", null)
        }
    }

    fun resetNorth() {
        webView?.post {
            webView?.evaluateJavascript("if (typeof resetNorth === 'function') { resetNorth(); }", null)
        }
    }

    fun invalidateMapSize() {
        webView?.post {
            webView?.evaluateJavascript("if (typeof invalidateMapSize === 'function') invalidateMapSize();", null)
        }
    }
}

class MapAppInterface(
    private val onMapLoaded: () -> Unit,
    private val onCoordinatesSelected: (Double, Double) -> Unit,
    private val onApiKeyMissing: (String) -> Unit,
    private val onRequestTimelineRefresh: () -> Unit
) {
    @JavascriptInterface
    fun onMapLoaded() {
        onMapLoaded.invoke()
    }

    @JavascriptInterface
    fun onCoordinatesSelected(latitude: Double, longitude: Double) {
        onCoordinatesSelected.invoke(latitude, longitude)
    }

    @JavascriptInterface
    fun onApiKeyMissing(layerName: String) {
        onApiKeyMissing.invoke(layerName)
    }

    @JavascriptInterface
    fun onRequestTimelineRefresh() {
        onRequestTimelineRefresh.invoke()
    }
}
