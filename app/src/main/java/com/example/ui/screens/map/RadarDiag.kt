package com.example.ui.screens.map

import android.util.Log

object RadarDiag {
    const val TAG = "RadarDiag"

    @Volatile
    var currentFrameIndex: Int = -1

    @Volatile
    var currentFrameTimestamp: Long = -1L

    @Volatile
    var isPlaybackActive: Boolean = false

    @Volatile
    var currentJobSequenceId: Long = 0L

    fun logFrameIndexRequested(index: Int, timestamp: Long, label: String) {
        Log.d(TAG, "[Frame Index Requested] Index: $index | Timestamp: $timestamp | Label: $label")
    }

    fun logRadarTimestamp(index: Int, timestamp: Long) {
        Log.d(TAG, "[Radar Timestamp] FrameIndex: $index | Timestamp: $timestamp")
    }

    fun logVisibleTileRequested(zoom: Int, key: String, x: Int, y: Int) {
        Log.d(TAG, "[Visible Tile Requested] Zoom: $zoom | Key: $key | X=$x, Y=$y")
    }

    fun logTileDownloadStart(key: String, url: String) {
        Log.d(TAG, "[Tile Download Start] Key: $key | URL: $url")
    }

    fun logTileDownloadCompletion(key: String, status: String, bytes: Long, httpCode: Int, url: String) {
        Log.d(TAG, "[Tile Download Completion] Key: $key | Status: $status | Bytes: $bytes | HTTP: $httpCode | URL: $url")
    }

    fun logRamCacheHit(key: String) {
        Log.d(TAG, "[RAM Cache Hit] Key: $key")
    }

    fun logRamCacheMiss(key: String) {
        Log.d(TAG, "[RAM Cache Miss] Key: $key")
    }

    fun logDiskCacheHit(key: String) {
        Log.d(TAG, "[Disk Cache Hit] Key: $key")
    }

    fun logDiskCacheMiss(key: String) {
        Log.d(TAG, "[Disk Cache Miss] Key: $key")
    }

    fun logTileRenderingEvent(tileX: Int, tileY: Int, zoom: Int, cacheKey: String?, drawn: Boolean) {
        Log.d(TAG, "[Tile Rendering Event] Tile: X=$tileX, Y=$tileY, Zoom=$zoom | Key: $cacheKey | Drawn: $drawn")
    }

    fun logFrameReadyEvent(frameIndex: Int, timestamp: Long, requiredTiles: Int) {
        Log.d(TAG, "[Frame Ready Event] Frame Index: $frameIndex | Timestamp: $timestamp | RequiredTiles: $requiredTiles")
    }

    fun logPlaybackFrameSwitch(oldIndex: Int, newIndex: Int, timestamp: Long) {
        Log.d(TAG, "[Playback Frame Switch] OldIndex: $oldIndex -> NewIndex: $newIndex | Timestamp: $timestamp")
    }

    fun logMapInvalidate(source: String) {
        Log.d(TAG, "[Map Invalidate Event] Source: $source")
    }

    fun logCacheClear(target: String, reason: String, count: Int = -1) {
        Log.d(TAG, "[Cache Clear Operation] Target: $target | Reason: $reason | Count: $count")

        // Detect if active frame tiles are being cleared!
        if (currentFrameTimestamp > 0L && (target.contains("RAM") || target.contains("Disk") || target.contains("OSMDroid"))) {
            Log.w(TAG, "[DIAG DETECT] ANOMALY: Cache Clear ($target, Reason: $reason) executed while Frame $currentFrameIndex (Timestamp: $currentFrameTimestamp) is active!")
        }
    }

    fun logEvictFrame(timestamp: Long, count: Int) {
        Log.d(TAG, "[Cache Evict Frame] Timestamp: $timestamp | TileCount: $count")
        if (timestamp == currentFrameTimestamp && isPlaybackActive) {
            Log.w(TAG, "[DIAG DETECT] ANOMALY: Cleared tiles for Timestamp $timestamp which belong to CURRENT ACTIVE PLAYBACK FRAME $currentFrameIndex!")
        }
    }

    fun logZoomEvent(source: String, oldZoom: Double, newZoom: Double) {
        Log.d(TAG, "[Zoom Event] Source: $source | OldZoom: $oldZoom -> NewZoom: $newZoom")
    }

    fun logPlaybackStateTransition(oldState: String, newState: String) {
        Log.d(TAG, "[Playback State Transition] $oldState -> $newState")
    }

    fun printFrameSummary(
        frameIndex: Int,
        timestamp: Long,
        requiredTiles: Int,
        loadedTiles: Int,
        failedTiles: Int,
        ramHits: Int,
        diskHits: Int,
        netMisses: Int,
        isReady: Boolean,
        isRendered: Boolean,
        playbackAdvanced: Boolean
    ) {
        val summary = """
        |----------------------------------------
        |Frame $frameIndex
        |Tiles required: $requiredTiles
        |Tiles loaded: $loadedTiles
        |Tiles failed: $failedTiles
        |Cache hits: ${ramHits + diskHits}
        |Cache misses: $netMisses
        |Frame marked ready: ${if (isReady) "YES" else "NO"}
        |Frame rendered: ${if (isRendered) "YES" else "NO"}
        |Playback advanced: ${if (playbackAdvanced) "YES" else "NO"}
        |----------------------------------------
        """.trimMargin()
        Log.d(TAG, summary)

        // ANOMALY DETECTION: Did playback advance before all required tiles loaded?
        if (playbackAdvanced && (loadedTiles < requiredTiles)) {
            Log.w(TAG, "[DIAG DETECT] ANOMALY: Playback advanced to Frame $frameIndex before all required visible tiles finished loading! Loaded: $loadedTiles/$requiredTiles (Missing: ${requiredTiles - loadedTiles})")
        }
    }

    fun detectJobCancellation(jobType: String, oldSeq: Long, oldFrameIndex: Int, newSeq: Long, newFrameIndex: Int) {
        Log.w(TAG, "[DIAG DETECT] ANOMALY: Asynchronous coroutine/job '$jobType' for Frame $oldFrameIndex (Seq: $oldSeq) was CANCELLED or OVERWRITTEN by new job for Frame $newFrameIndex (Seq: $newSeq)!")
    }
}
