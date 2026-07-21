package com.example.ui.screens.map

import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.example.data.models.RadarFrame
import com.example.data.models.RadarTimeline
import com.example.data.repository.RadarRepository
import com.example.data.repository.RadarState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class RadarOverlayManager(
    private val radarRepository: RadarRepository = RadarRepository()
) {
    private var webView: WebView? = null
    private var currentTimeline: RadarTimeline? = null
    private var currentSelectedIndex: Int = 3 // Default to 'Now'

    fun attachWebView(view: WebView) {
        this.webView = view
        Log.d("RadarOverlayManager", "Radar overlay manager attached to WebView")
    }

    fun loadRadarData(coroutineScope: CoroutineScope) {
        coroutineScope.launch(Dispatchers.IO) {
            Log.d("RadarOverlayManager", "Radar initialized")
            val timeline = radarRepository.fetchRadarTimeline()
            if (timeline != null) {
                currentTimeline = timeline
                pushTimelineToWebView(timeline)
            } else {
                notifyRadarUnavailable()
            }
        }
    }

    fun setTimelineIndex(index: Int) {
        currentSelectedIndex = index
        val frame = currentTimeline?.selectFrameAt(index)
        if (frame != null && currentTimeline != null) {
            val tileUrl = currentTimeline!!.getTileUrl(frame)
            Log.d("RadarOverlayManager", "Current frame selected at index $index, generated tile URL: $tileUrl")
        }
        
        webView?.post {
            webView?.evaluateJavascript("if (typeof setTimelineIndex === 'function') { setTimelineIndex($index); }", null)
        }
    }

    private fun pushTimelineToWebView(timeline: RadarTimeline) {
        val framesJsonArray = JSONArray()
        timeline.compiled7TimelineFrames.forEach { frame ->
            val obj = JSONObject()
            obj.put("time", frame.time)
            obj.put("path", frame.path)
            obj.put("isForecast", frame.isForecast)
            obj.put("tileUrl", timeline.getTileUrl(frame))
            framesJsonArray.put(obj)
        }

        val payload = JSONObject()
        payload.put("host", timeline.host)
        payload.put("generatedAt", timeline.generatedAt)
        payload.put("frames", framesJsonArray)

        val jsonStr = payload.toString()
        Log.d("RadarOverlayManager", "Pushing modern RainViewer timeline (${timeline.compiled7TimelineFrames.size} frames) to WebView JS engine.")

        webView?.post {
            webView?.evaluateJavascript("if (typeof updateRadarTimeline === 'function') { updateRadarTimeline($jsonStr); }", null)
        }
    }

    private fun notifyRadarUnavailable() {
        Log.w("RadarOverlayManager", "Radar temporarily unavailable - notifying JS interface")
        webView?.post {
            webView?.evaluateJavascript("if (typeof showRadarUnavailable === 'function') { showRadarUnavailable('Radar temporarily unavailable'); }", null)
        }
    }

    inner class JSInterface {
        @JavascriptInterface
        fun onTileLoaded(tileUrl: String) {
            Log.d("RadarOverlayManager", "Tile loaded successfully: $tileUrl")
        }

        @JavascriptInterface
        fun onTileError(tileUrl: String, retryCount: Int) {
            Log.w("RadarOverlayManager", "Tile load failed: $tileUrl (Attempt: $retryCount)")
        }

        @JavascriptInterface
        fun onRetryingTile(tileUrl: String, nextDelayMs: Long) {
            Log.d("RadarOverlayManager", "Retrying... tile download for: $tileUrl in ${nextDelayMs}ms")
        }

        @JavascriptInterface
        fun onRequestTimelineRefresh() {
            Log.d("RadarOverlayManager", "JS requested timeline refresh")
            webView?.let {
                CoroutineScope(Dispatchers.IO).launch {
                    val timeline = radarRepository.fetchRadarTimeline(forceRefresh = true)
                    if (timeline != null) {
                        currentTimeline = timeline
                        pushTimelineToWebView(timeline)
                    } else {
                        notifyRadarUnavailable()
                    }
                }
            }
        }
    }
}
