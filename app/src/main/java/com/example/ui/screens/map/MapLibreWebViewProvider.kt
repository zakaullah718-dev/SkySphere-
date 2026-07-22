package com.example.ui.screens.map

import android.content.Context
import android.os.Build
import android.view.View
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

/**
 * High-performance WebView-based MapLibre GL JS map provider implementation.
 * Integrates modular RadarOverlayManager, Kotlin RadarTileProxy, and modern RainViewer API.
 */
class MapLibreWebViewProvider : RadarMapProvider {
    private var webView: WebView? = null
    val radarOverlayManager = RadarOverlayManager()

    override fun createMapView(
        context: Context,
        onMapLoaded: () -> Unit,
        onCoordinatesSelected: (Double, Double) -> Unit,
        onApiKeyMissing: (String) -> Unit
    ): View {
        val tileProxy = RadarTileProxy(context)

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

        val wv = WebView(context)
        wv.apply {
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
                javaScriptCanOpenWindowsAutomatically = true
                setSupportMultipleWindows(false)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    safeBrowsingEnabled = false
                }
            }
            
            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(this, true)

            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            
            addOnLayoutChangeListener { v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                val newWidth = right - left
                val newHeight = bottom - top
                val oldWidth = oldRight - oldLeft
                val oldHeight = oldBottom - oldTop
                if (newWidth > 0 && newHeight > 0 && (newWidth != oldWidth || newHeight != oldHeight)) {
                    android.util.Log.d("RadarWebView", "WebView layout size changed: ${newWidth}x${newHeight}. Invalidating map size.")
                    v.postDelayed({
                        (v as? WebView)?.evaluateJavascript("if (typeof invalidateMapSize === 'function') invalidateMapSize();", null)
                    }, 100)
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
                    consoleMessage?.let {
                        android.util.Log.d("RadarWebView", "[${it.messageLevel()}] ${it.message()} -- at ${it.sourceId()}:${it.lineNumber()}")
                    }
                    return super.onConsoleMessage(consoleMessage)
                }
            }
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    val proxiedResponse = tileProxy.shouldIntercept(request)
                    if (proxiedResponse != null) {
                        return proxiedResponse
                    }
                    return super.shouldInterceptRequest(view, request)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    view?.postDelayed({
                        view.evaluateJavascript("if (typeof invalidateMapSize === 'function') invalidateMapSize();", null)
                    }, 200)
                }

                override fun onReceivedSslError(
                    view: WebView?,
                    handler: android.webkit.SslErrorHandler?,
                    error: android.net.http.SslError?
                ) {
                    android.util.Log.w("RadarWebView", "SSL Error inside WebView: ${error?.primaryError}. Proceeding to ensure high reliability.")
                    handler?.proceed()
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: android.webkit.WebResourceError?
                ) {
                    super.onReceivedError(view, request, error)
                    android.util.Log.e("RadarWebView", "Resource loading error in WebView: ${error?.description} for URL: ${request?.url}")
                }
            }
            
            addJavascriptInterface(
                MapAppInterface(
                    onMapLoaded = {
                        wv.post {
                            wv.evaluateJavascript("if (typeof invalidateMapSize === 'function') invalidateMapSize();", null)
                        }
                        onMapLoaded.invoke()
                        radarOverlayManager.loadRadarData(CoroutineScope(Dispatchers.Main))
                    },
                    onCoordinatesSelected = onCoordinatesSelected,
                    onApiKeyMissing = onApiKeyMissing
                ),
                "AndroidMap"
            )

            addJavascriptInterface(
                radarOverlayManager.JSInterface(),
                "AndroidRadar"
            )

            val htmlContent = try {
                context.assets.open("radar_map.html").bufferedReader().use { it.readText() }
            } catch (e: Exception) {
                android.util.Log.e("RadarWebView", "Failed to read radar_map.html from assets folder", e)
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
        radarOverlayManager.attachWebView(wv)
        return wv
    }

    override fun setCenter(latitude: Double, longitude: Double, zoom: Float?) {
        val zoomParam = zoom?.toString() ?: "null"
        webView?.post {
            webView?.evaluateJavascript("if (typeof setCenter === 'function') { setCenter($latitude, $longitude, $zoomParam); }", null)
            webView?.postDelayed({
                webView?.evaluateJavascript("if (typeof invalidateMapSize === 'function') invalidateMapSize();", null)
            }, 100)
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

    override fun setTimelineIndex(index: Int) {
        radarOverlayManager.setTimelineIndex(index)
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
    private val onApiKeyMissing: (String) -> Unit
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
}
