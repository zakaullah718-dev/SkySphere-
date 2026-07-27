package com.example.ui.screens.map

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class RadarTimeLapseController(
    private val radarRepository: FutureRadarRepository = FutureRadarRepository()
) {
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var playbackJob: Job? = null

    private val _state = MutableStateFlow(TimeLapseState())
    val state: StateFlow<TimeLapseState> = _state.asStateFlow()

    private val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())

    fun initializeForLayer(layer: MapWeatherLayer, onFrameChanged: ((TimeLapseFrame) -> Unit)? = null) {
        pause()
        _state.update { it.copy(activeLayer = layer, isLoading = true) }

        scope.launch {
            val frames = fetchOrGenerateFrames()
            val initialIndex = frames.indexOfLast { !it.isForecast }.coerceAtLeast(0)
            _state.update {
                it.copy(
                    frames = frames,
                    currentFrameIndex = initialIndex,
                    isLoading = false
                )
            }
            val initialFrame = frames.getOrNull(initialIndex)
            if (initialFrame != null && onFrameChanged != null) {
                onFrameChanged(initialFrame)
            }
        }
    }

    private suspend fun fetchOrGenerateFrames(): List<TimeLapseFrame> = withContext(Dispatchers.IO) {
        val nowSec = System.currentTimeMillis() / 1000L
        val radarFrames = try {
            radarRepository.getAllRadarPastFrames()
        } catch (e: Exception) {
            emptyList()
        }

        if (radarFrames.isNotEmpty()) {
            radarFrames.mapIndexed { idx, rf ->
                createFrame(idx, rf.time, rf, nowSec)
            }
        } else {
            // Fallback 13 frames covering past 6 hours at 30-minute intervals
            val list = mutableListOf<TimeLapseFrame>()
            val startSec = nowSec - (6 * 3600L)
            val interval = 1800L
            var idx = 0
            for (ts in startSec..nowSec step interval) {
                list.add(createFrame(idx++, ts, RadarFrame(time = ts), nowSec))
            }
            if (list.none { it.isNow }) {
                list.add(createFrame(idx, nowSec, RadarFrame(time = nowSec), nowSec))
            }
            list
        }
    }

    private fun createFrame(index: Int, timestamp: Long, radarFrame: RadarFrame?, nowSec: Long): TimeLapseFrame {
        val diffSec = timestamp - nowSec
        val isNow = abs(diffSec) < 300L
        val isForecast = diffSec > 300L

        val displayLabel = when {
            isNow -> "NOW"
            diffSec < 0 -> {
                val totalMins = abs(diffSec) / 60
                val hrs = totalMins / 60
                val mins = totalMins % 60
                when {
                    hrs > 0 && mins > 0 -> "-${hrs}h ${mins}m"
                    hrs > 0 -> "-${hrs}h"
                    else -> "-${mins}m"
                }
            }
            else -> {
                val totalMins = diffSec / 60
                val hrs = totalMins / 60
                val mins = totalMins % 60
                when {
                    hrs > 0 && mins > 0 -> "+${hrs}h ${mins}m"
                    hrs > 0 -> "+${hrs}h"
                    else -> "+${mins}m"
                }
            }
        }

        val clockStr = timeFormat.format(Date(timestamp * 1000L))

        return TimeLapseFrame(
            index = index,
            timestamp = timestamp,
            displayLabel = displayLabel,
            formattedClock = clockStr,
            radarFrame = radarFrame,
            isNow = isNow,
            isForecast = isForecast
        )
    }

    fun togglePlayPause(onFrameChanged: (TimeLapseFrame) -> Unit) {
        val currentlyPlaying = _state.value.isPlaying
        if (currentlyPlaying) {
            pause()
        } else {
            play(onFrameChanged)
        }
    }

    fun play(onFrameChanged: (TimeLapseFrame) -> Unit) {
        pause()
        _state.update { it.copy(isPlaying = true) }

        playbackJob = scope.launch {
            while (_state.value.isPlaying) {
                val frames = _state.value.frames
                if (frames.isEmpty()) break

                val currentIndex = _state.value.currentFrameIndex
                val nextIndex = (currentIndex + 1) % frames.size
                _state.update { it.copy(currentFrameIndex = nextIndex) }

                frames.getOrNull(nextIndex)?.let { frame ->
                    onFrameChanged(frame)
                }

                val baseDelay = 750L
                val speed = _state.value.playbackSpeed.coerceAtLeast(0.1f)
                val delayTime = (baseDelay / speed).toLong()
                delay(delayTime)
            }
        }
    }

    fun pause() {
        playbackJob?.cancel()
        playbackJob = null
        _state.update { it.copy(isPlaying = false) }
    }

    fun nextFrame(onFrameChanged: (TimeLapseFrame) -> Unit) {
        pause()
        val frames = _state.value.frames
        if (frames.isEmpty()) return
        val nextIndex = (_state.value.currentFrameIndex + 1) % frames.size
        _state.update { it.copy(currentFrameIndex = nextIndex) }
        frames.getOrNull(nextIndex)?.let { onFrameChanged(it) }
    }

    fun previousFrame(onFrameChanged: (TimeLapseFrame) -> Unit) {
        pause()
        val frames = _state.value.frames
        if (frames.isEmpty()) return
        val prevIndex = if (_state.value.currentFrameIndex - 1 < 0) frames.size - 1 else _state.value.currentFrameIndex - 1
        _state.update { it.copy(currentFrameIndex = prevIndex) }
        frames.getOrNull(prevIndex)?.let { onFrameChanged(it) }
    }

    fun seekToFrame(index: Int, onFrameChanged: (TimeLapseFrame) -> Unit) {
        val frames = _state.value.frames
        if (frames.isEmpty()) return
        val clampedIndex = index.coerceIn(0, frames.size - 1)
        _state.update { it.copy(currentFrameIndex = clampedIndex) }
        frames.getOrNull(clampedIndex)?.let { onFrameChanged(it) }
    }

    fun cycleSpeed() {
        val nextSpeed = when (_state.value.playbackSpeed) {
            0.5f -> 1.0f
            1.0f -> 2.0f
            else -> 0.5f
        }
        _state.update { it.copy(playbackSpeed = nextSpeed) }
    }

    fun destroy() {
        pause()
    }
}
