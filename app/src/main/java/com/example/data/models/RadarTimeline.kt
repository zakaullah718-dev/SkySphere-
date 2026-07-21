package com.example.data.models

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class RadarFrame(
    val time: Long,
    val path: String,
    val isForecast: Boolean = false
) {
    val formattedTime: String
        get() {
            return try {
                val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                sdf.timeZone = TimeZone.getDefault()
                sdf.format(Date(time * 1000L))
            } catch (e: Exception) {
                "$time"
            }
        }
}

data class RadarTimeline(
    val host: String = "https://tilecache.rainviewer.com",
    val pastFrames: List<RadarFrame> = emptyList(),
    val nowcastFrames: List<RadarFrame> = emptyList(),
    val compiled7TimelineFrames: List<RadarFrame> = emptyList(),
    val generatedAt: Long = 0L
) {
    fun getTileUrl(
        frame: RadarFrame,
        tileSize: Int = 256,
        colorScheme: Int = 2,
        smooth: Int = 1,
        snow: Int = 1
    ): String {
        val cleanHost = host.trimEnd('/')
        val cleanPath = frame.path.trimEnd('/')
        val tileUrl = "$cleanHost$cleanPath/$tileSize/{z}/{x}/{y}/$colorScheme/${smooth}_$snow.png"
        Log.d("RadarTimeline", "Tile URL generated: $tileUrl")
        return tileUrl
    }

    fun selectFrameAt(index: Int): RadarFrame? {
        if (compiled7TimelineFrames.isEmpty()) return null
        val safeIndex = index.coerceIn(0, compiled7TimelineFrames.lastIndex)
        val selected = compiled7TimelineFrames[safeIndex]
        Log.d("RadarTimeline", "Current frame selected at index $safeIndex: time=${selected.time}, path=${selected.path}, isForecast=${selected.isForecast}")
        return selected
    }
}
