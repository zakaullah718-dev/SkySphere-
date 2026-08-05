package com.example.ui.screens.map

import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

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

    // Metrics
    val concurrentDownloadCount = AtomicInteger(0)
    val downloadQueueSize = AtomicInteger(0)
    val totalCacheHits = AtomicLong(0)
    val totalCacheMisses = AtomicLong(0)
    val duplicateRequestCount = AtomicLong(0)
    val tileReplacementCount = AtomicLong(0)
    val downloadCancellationCount = AtomicLong(0)
    val totalDownloadTimeMs = AtomicLong(0)
    val completedDownloadCount = AtomicLong(0)
    val httpStatusSummary = ConcurrentHashMap<Int, AtomicInteger>()

    fun recordHttpStatus(code: Int) {
        httpStatusSummary.computeIfAbsent(code) { AtomicInteger(0) }.incrementAndGet()
    }

    fun recordDownloadDuration(durationMs: Long) {
        totalDownloadTimeMs.addAndGet(durationMs)
        completedDownloadCount.incrementAndGet()
    }

    fun getCacheHitPercentage(): Float {
        val hits = totalCacheHits.get()
        val misses = totalCacheMisses.get()
        val total = hits + misses
        return if (total > 0) (hits.toFloat() / total.toFloat()) * 100f else 0f
    }

    fun getAverageDownloadTimeMs(): Long {
        val count = completedDownloadCount.get()
        return if (count > 0) totalDownloadTimeMs.get() / count else 0L
    }

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
        val active = concurrentDownloadCount.get()
        val queue = downloadQueueSize.get()
        Log.d(TAG, "[Tile Download Start] Key: $key | ActiveDownloads: $active | QueueSize: $queue | URL: $url")
    }

    fun logTileDownloadCompletion(key: String, status: String, bytes: Long, httpCode: Int, url: String) {
        recordHttpStatus(httpCode)
        val active = concurrentDownloadCount.get()
        val queue = downloadQueueSize.get()
        Log.d(TAG, "[Tile Download Completion] Key: $key | Status: $status | Bytes: $bytes | HTTP: $httpCode | ActiveDownloads: $active | QueueSize: $queue | URL: $url")
    }

    fun logRamCacheHit(key: String) {
        totalCacheHits.incrementAndGet()
        Log.d(TAG, "[RAM Cache Hit] Key: $key | CacheHitPct: ${String.format("%.1f", getCacheHitPercentage())}%")
    }

    fun logRamCacheMiss(key: String) {
        totalCacheMisses.incrementAndGet()
        Log.d(TAG, "[RAM Cache Miss] Key: $key | CacheHitPct: ${String.format("%.1f", getCacheHitPercentage())}%")
    }

    fun logDiskCacheHit(key: String) {
        totalCacheHits.incrementAndGet()
        Log.d(TAG, "[Disk Cache Hit] Key: $key | CacheHitPct: ${String.format("%.1f", getCacheHitPercentage())}%")
    }

    fun logDiskCacheMiss(key: String) {
        totalCacheMisses.incrementAndGet()
        Log.d(TAG, "[Disk Cache Miss] Key: $key | CacheHitPct: ${String.format("%.1f", getCacheHitPercentage())}%")
    }

    fun logDuplicateRequest(key: String) {
        val count = duplicateRequestCount.incrementAndGet()
        Log.d(TAG, "[Duplicate Request Deduplicated] Key: $key | TotalDeduplicated: $count")
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

    fun logPlaybackAdvanceOrPause(frameIndex: Int, action: String, reason: String) {
        Log.d(TAG, "[Playback Scheduler] Action: $action | FrameIndex: $frameIndex | Reason: $reason")
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
        playbackAdvanced: Boolean,
        reason: String = "Normal frame advance"
    ) {
        val readinessPct = if (requiredTiles > 0) (loadedTiles.toFloat() / requiredTiles.toFloat() * 100f) else 100f
        val httpSummaryStr = httpStatusSummary.entries.joinToString(", ") { "${it.key}: ${it.value.get()}" }

        val summary = """
        |----------------------------------------
        |Frame $frameIndex
        |Tiles required: $requiredTiles | Loaded: $loadedTiles | Failed: $failedTiles
        |Readiness: ${String.format("%.1f", readinessPct)}%
        |Cache hits: ${ramHits + diskHits} (Hit Rate: ${String.format("%.1f", getCacheHitPercentage())}%)
        |Concurrent downloads: ${concurrentDownloadCount.get()} | Queue size: ${downloadQueueSize.get()}
        |Deduplicated requests: ${duplicateRequestCount.get()}
        |Avg Download Time: ${getAverageDownloadTimeMs()} ms
        |HTTP Summary: [$httpSummaryStr]
        |Frame marked ready: ${if (isReady) "YES" else "NO"}
        |Frame rendered: ${if (isRendered) "YES" else "NO"}
        |Playback advanced: ${if (playbackAdvanced) "YES" else "NO"}
        |Action Reason: $reason
        |----------------------------------------
        """.trimMargin()
        Log.d(TAG, summary)

        if (playbackAdvanced && (loadedTiles == 0 && requiredTiles > 0)) {
            Log.e(TAG, "[DIAG DETECT] CRITICAL ANOMALY: Playback advanced to Frame $frameIndex with ZERO loaded tiles (0/$requiredTiles)! Reason given: $reason")
        } else if (playbackAdvanced && (loadedTiles < requiredTiles)) {
            Log.w(TAG, "[DIAG DETECT] ANOMALY: Playback advanced to Frame $frameIndex partially loaded: $loadedTiles/$requiredTiles (${String.format("%.1f", readinessPct)}%)")
        }
    }

    fun detectJobCancellation(jobType: String, oldSeq: Long, oldFrameIndex: Int, newSeq: Long, newFrameIndex: Int) {
        Log.w(TAG, "[DIAG DETECT] ANOMALY: Asynchronous coroutine/job '$jobType' for Frame $oldFrameIndex (Seq: $oldSeq) was CANCELLED or OVERWRITTEN by new job for Frame $newFrameIndex (Seq: $newSeq)!")
    }
}

