package com.example.ui.screens.map

import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

class RadarFrameStore {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var historyPreparationJob: Job? = null

    private val _frames = MutableStateFlow<List<TimeLapseFrame>>(emptyList())
    val frames: StateFlow<List<TimeLapseFrame>> = _frames.asStateFlow()

    @Volatile
    private var activeLayer: MapWeatherLayer = MapWeatherLayer.NONE

    private val frameReadinessCache = ConcurrentHashMap<String, Boolean>()

    fun setFrames(layer: MapWeatherLayer, frameList: List<TimeLapseFrame>) {
        activeLayer = layer
        _frames.value = frameList
        frameReadinessCache.clear()
        Log.d("SKYSPHERE_RADAR", "[TL] RADAR_FRAME_STORE_SET_FRAMES count=${frameList.size} layer=$layer")
    }

    fun getFrames(): List<TimeLapseFrame> = _frames.value

    fun getFrame(index: Int): TimeLapseFrame? {
        val list = _frames.value
        if (list.isEmpty()) return null
        val safeIndex = index.coerceIn(0, list.size - 1)
        return list.getOrNull(safeIndex)
    }

    fun getCurrentFrame(index: Int): TimeLapseFrame? = getFrame(index)

    fun getNextFrame(index: Int): TimeLapseFrame? {
        val list = _frames.value
        if (list.isEmpty()) return null
        val nextIdx = (index + 1) % list.size
        return list.getOrNull(nextIdx)
    }

    fun getPreviousFrame(index: Int): TimeLapseFrame? {
        val list = _frames.value
        if (list.isEmpty()) return null
        val prevIdx = if (index - 1 < 0) list.size - 1 else index - 1
        return list.getOrNull(prevIdx)
    }

    fun buildTileKey(layer: MapWeatherLayer, frame: TimeLapseFrame, zoom: Int, x: Int, y: Int): String {
        val ts = frame.radarFrame?.time ?: frame.timestamp
        return RadarPreloader.buildTileKey(layer, ts, zoom, x, y)
    }

    fun checkFrameReadiness(
        layer: MapWeatherLayer,
        frame: TimeLapseFrame,
        zoom: Int,
        tileXs: List<Int>,
        tileYs: List<Int>
    ): FrameReadiness {
        if (tileXs.isEmpty() || tileYs.isEmpty()) {
            return FrameReadiness(requiredCount = 0, readyCount = 0, isReady = true)
        }

        val requiredKeys = mutableListOf<String>()
        for (x in tileXs) {
            for (y in tileYs) {
                requiredKeys.add(buildTileKey(layer, frame, zoom, x, y))
            }
        }

        var readyCount = 0
        var ramHits = 0
        var diskHits = 0
        val missingKeys = mutableListOf<String>()

        for (key in requiredKeys) {
            if (TileRamCache.contains(key)) {
                ramHits++
                readyCount++
            } else if (DiskTileCache.contains(key)) {
                val diskBmp = DiskTileCache.get(key)
                if (diskBmp != null && !diskBmp.isRecycled) {
                    TileRamCache.put(key, diskBmp)
                    diskHits++
                    readyCount++
                } else {
                    missingKeys.add(key)
                }
            } else {
                missingKeys.add(key)
            }
        }

        val isReady = readyCount == requiredKeys.size
        val readinessKey = "${frame.timestamp}_${zoom}_${tileXs.hashCode()}_${tileYs.hashCode()}"
        frameReadinessCache[readinessKey] = isReady

        Log.d(
            "SKYSPHERE_RADAR",
            "[TL] RADAR_VISIBLE_TILES required=${requiredKeys.size} ram=$ramHits disk=$diskHits missing=${missingKeys.size} frame=${frame.timestamp}"
        )

        return FrameReadiness(
            requiredCount = requiredKeys.size,
            readyCount = readyCount,
            isReady = isReady,
            missingKeys = missingKeys
        )
    }

    fun prepareHistoricalFramesAsync(
        layer: MapWeatherLayer,
        currentFrameIndex: Int,
        zoom: Int,
        tileXs: List<Int>,
        tileYs: List<Int>,
        repo: FutureRadarRepository
    ) {
        if (layer == MapWeatherLayer.NONE) return

        synchronized(this) {
            if (historyPreparationJob?.isActive == true) {
                // If job is already running and params are similar, let it continue
                return
            }

            historyPreparationJob = scope.launch {
                val allFrames = _frames.value
                if (allFrames.isEmpty()) return@launch

                Log.d("SKYSPHERE_RADAR", "[TL] HISTORY_PREPARATION_STARTED totalFrames=${allFrames.size}")

                val safeCurrentIndex = currentFrameIndex.coerceIn(0, allFrames.size - 1)
                val orderedIndices = mutableListOf<Int>()

                // Order: next frames first, then older frames
                for (i in 0 until allFrames.size) {
                    val idx = (safeCurrentIndex + i) % allFrames.size
                    orderedIndices.add(idx)
                }

                for (idx in orderedIndices) {
                    val frame = allFrames.getOrNull(idx) ?: continue

                    val readiness = checkFrameReadiness(layer, frame, zoom, tileXs, tileYs)
                    if (readiness.isReady) {
                        Log.d("SKYSPHERE_RADAR", "[TL] RADAR_FRAME_READY frame=${frame.timestamp} ready=${readiness.readyCount}/${readiness.requiredCount}")
                        continue
                    }

                    // Prepare missing tiles for this historical frame sequentially
                    val pZoom = zoom.coerceIn(
                        FutureWeatherLayerManager.PROVIDER_MIN_ZOOM,
                        FutureWeatherLayerManager.RAIN_RADAR_PROVIDER_MAX_ZOOM
                    )

                    for (x in tileXs) {
                        for (y in tileYs) {
                            val key = buildTileKey(layer, frame, pZoom, x, y)
                            if (TileRamCache.contains(key) || DiskTileCache.contains(key)) {
                                Log.d("SKYSPHERE_RADAR", "[TL] RADAR_CACHE_HIT source=CACHE key=$key")
                                continue
                            }

                            Log.d("SKYSPHERE_RADAR", "[TL] RADAR_DOWNLOAD_STARTED key=$key")
                            val bitmap = RadarTileFetcher.fetchOrDeduplicateTile(key) {
                                val rf = frame.radarFrame ?: repo.getLatestRadarFrameSync()
                                val tileUrl = rf.buildTileUrl(pZoom, x, y)
                                kotlinx.coroutines.runBlocking { repo.downloadTileBitmap(tileUrl) }
                            }

                            if (bitmap != null && !bitmap.isRecycled) {
                                Log.d("SKYSPHERE_RADAR", "[TL] RADAR_DOWNLOAD_COMPLETED key=$key")
                            } else {
                                Log.d("SKYSPHERE_RADAR", "[TL] RADAR_CACHE_MISS key=$key")
                            }
                        }
                    }

                    val finalCheck = checkFrameReadiness(layer, frame, zoom, tileXs, tileYs)
                    if (finalCheck.isReady) {
                        Log.d("SKYSPHERE_RADAR", "[TL] RADAR_FRAME_READY frame=${frame.timestamp} ready=${finalCheck.readyCount}/${finalCheck.requiredCount}")
                    }
                }

                Log.d("SKYSPHERE_RADAR", "[TL] HISTORY_PREPARATION_COMPLETED")
            }
        }
    }

    fun cancelHistoryPreparation() {
        historyPreparationJob?.cancel()
        historyPreparationJob = null
    }
}

data class FrameReadiness(
    val requiredCount: Int,
    val readyCount: Int,
    val isReady: Boolean,
    val missingKeys: List<String> = emptyList()
)
