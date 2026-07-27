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
    val isForecast: Boolean = false
)

data class TimeLapseState(
    val frames: List<TimeLapseFrame> = emptyList(),
    val currentFrameIndex: Int = 0,
    val isPlaying: Boolean = false,
    val playbackSpeed: Float = 1.0f, // 0.5f, 1.0f, 2.0f
    val isLoading: Boolean = false,
    val activeLayer: MapWeatherLayer = MapWeatherLayer.NONE
) {
    val currentFrame: TimeLapseFrame?
        get() = frames.getOrNull(currentFrameIndex)
}
