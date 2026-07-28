package com.example.ui.screens.map

data class TimeLapseFrame(
    val index: Int,
    val timestamp: Long,
    val timeLabel: String = "",
    val relativeLabel: String = "",
    val displayLabel: String = relativeLabel.ifBlank { "NOW" },
    val formattedClock: String = timeLabel,
    val radarFrame: RadarFrame? = null,
    val isNow: Boolean = false,
    val isForecast: Boolean = false,
    val isReady: Boolean = false
)

data class TimeLapseState(
    val frames: List<TimeLapseFrame> = emptyList(),
    val currentFrameIndex: Int = 0,
    val isPlaying: Boolean = false,
    val playbackSpeed: Float = 1.0f, // 0.5f, 1.0f, 1.5f, 2.0f
    val isLoading: Boolean = false,
    val isBuffering: Boolean = false,
    val bufferProgress: Float = 0f, // 0.0f to 1.0f
    val activeLayer: MapWeatherLayer = MapWeatherLayer.NONE
) {
    val currentFrame: TimeLapseFrame?
        get() = frames.getOrNull(currentFrameIndex)

    val availableHistoryLabel: String
        get() {
            if (frames.isEmpty()) return "Live Radar"
            val pastFrames = frames.filter { !it.isForecast }
            if (pastFrames.size < 2) return "Live Radar"
            val minTs = pastFrames.minOf { it.timestamp }
            val maxTs = pastFrames.maxOf { it.timestamp }
            val diffSec = maxTs - minTs
            val totalMins = diffSec / 60
            if (totalMins <= 0) return "Live Radar"
            val hrs = totalMins / 60
            val mins = totalMins % 60
            return when {
                hrs > 0 && mins > 0 -> "Past ${hrs}h ${mins}m"
                hrs > 0 -> "Past $hrs ${if (hrs == 1L) "Hour" else "Hours"}"
                else -> "Past $mins Mins"
            }
        }
}
