package com.example.ui.screens.map

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    val visibleTileRequests = AtomicLong(0)
    val uniqueTileRequests = AtomicLong(0)
    val duplicateRequestsPrevented = AtomicLong(0)
    val ramCacheHits = AtomicLong(0)
    val diskCacheHits = AtomicLong(0)
    val inFlightReuseCount = AtomicLong(0)
    val actualNetworkDownloads = AtomicLong(0)

    val reusedTiles = AtomicLong(0)
    val incrementalTileCount = AtomicLong(0)
    val queueMergeCount = AtomicLong(0)
    val queueRestartCount = AtomicLong(0)
    val cancelledDownloads = AtomicLong(0)
    val skippedDownloads = AtomicLong(0)
    val total429Responses = AtomicLong(0)
    val peakConcurrentDownloads = AtomicInteger(0)

    private val visibleReadinessSum = AtomicLong(0)
    private val visibleReadinessCount = AtomicLong(0)
    private val backgroundReadinessSum = AtomicLong(0)
    private val backgroundReadinessCount = AtomicLong(0)
    private val httpRequestDelaySum = AtomicLong(0)
    private val httpRequestDelayCount = AtomicLong(0)

    private val preloadCompletionSum = AtomicLong(0)
    private val preloadCompletionCount = AtomicLong(0)
    private val frameReadinessSum = AtomicLong(0)
    private val frameReadinessCount = AtomicLong(0)

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

    val deletedOldTilesCount = AtomicLong(0)
    val oldFramesRemovedCount = AtomicLong(0)
    val newFramesAddedCount = AtomicLong(0)
    private val queueWaitSumMs = AtomicLong(0)
    private val queueWaitCount = AtomicLong(0)
    private var diagStartTimeMs: Long = System.currentTimeMillis()

    private val tickerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isTickerStarted = false

    fun logDeletedOldTiles(count: Long) {
        deletedOldTilesCount.addAndGet(count)
    }

    fun logOldFrameRemoved(count: Int = 1) {
        oldFramesRemovedCount.addAndGet(count.toLong())
    }

    fun logNewFrameAdded(count: Int = 1) {
        newFramesAddedCount.addAndGet(count.toLong())
    }

    fun recordQueueWait(waitMs: Long) {
        queueWaitSumMs.addAndGet(waitMs.coerceAtLeast(0L))
        queueWaitCount.incrementAndGet()
    }

    fun startPeriodicDiagnostics() {
        synchronized(this) {
            if (isTickerStarted) return
            isTickerStarted = true
            diagStartTimeMs = System.currentTimeMillis()
        }
        tickerScope.launch {
            while (true) {
                delay(10_000L)
                printDiagnosticCounters()
            }
        }
    }

    fun recordConcurrentDownloads(count: Int) {
        var currentPeak = peakConcurrentDownloads.get()
        while (count > currentPeak) {
            if (peakConcurrentDownloads.compareAndSet(currentPeak, count)) break
            currentPeak = peakConcurrentDownloads.get()
        }
    }

    fun recordHttpRequestDelay(delayMs: Long) {
        httpRequestDelaySum.addAndGet(delayMs)
        httpRequestDelayCount.incrementAndGet()
    }

    fun recordVisibleTileReadiness(pct: Float) {
        visibleReadinessSum.addAndGet((pct * 10f).toLong())
        visibleReadinessCount.incrementAndGet()
    }

    fun recordBackgroundTileReadiness(pct: Float) {
        backgroundReadinessSum.addAndGet((pct * 10f).toLong())
        backgroundReadinessCount.incrementAndGet()
    }

    fun logQueueMerge(addedCount: Int) {
        queueMergeCount.incrementAndGet()
        incrementalTileCount.addAndGet(addedCount.toLong())
    }

    fun logQueueRestart() {
        queueRestartCount.incrementAndGet()
    }

    fun recordPreloadCompletion(pct: Float) {
        preloadCompletionSum.addAndGet((pct * 10f).toLong())
        preloadCompletionCount.incrementAndGet()
    }

    fun recordFrameReadiness(pct: Float) {
        frameReadinessSum.addAndGet((pct * 10f).toLong())
        frameReadinessCount.incrementAndGet()
    }

    fun log429Response() {
        total429Responses.incrementAndGet()
    }

    fun logCancelledDownload() {
        cancelledDownloads.incrementAndGet()
        downloadCancellationCount.incrementAndGet()
    }

    fun logSkippedDownload() {
        skippedDownloads.incrementAndGet()
    }

    fun logReusedTile() {
        reusedTiles.incrementAndGet()
    }

    fun printDiagnosticCounters() {
        val ramHits = ramCacheHits.get()
        val diskHits = diskCacheHits.get()
        val totalHits = ramHits + diskHits
        val downloads = actualNetworkDownloads.get()
        val dupsPrevented = duplicateRequestsPrevented.get()
        val reused = reusedTiles.get()

        val requestsSaved = ramHits + diskHits + dupsPrevented + reused
        val totalAllRequests = requestsSaved + downloads
        val cacheEfficiencyPct = if (totalAllRequests > 0) (requestsSaved.toDouble() / totalAllRequests.toDouble() * 100.0) else 100.0

        val avgQueueWaitMs = if (queueWaitCount.get() > 0) (queueWaitSumMs.get() / queueWaitCount.get()) else 0L
        val avgReadinessPct = if (frameReadinessCount.get() > 0) (frameReadinessSum.get().toDouble() / (frameReadinessCount.get() * 10.0)) else 0.0

        val warmState = RadarWarmUpEngine.state.value

        val summary = """
        |==================== RADAR CACHE DIAGNOSTICS SUMMARY (10s) ====================
        |RAM Cache Hits:                        $ramHits
        |Disk Cache Hits:                       $diskHits
        |Tiles Reused:                          $reused
        |New Downloads:                         $downloads
        |Duplicate Download Requests Prevented: $dupsPrevented
        |Frames Fully Cached:                  ${warmState.fullyCachedFrames}
        |Frames Partially Cached:              ${warmState.partiallyCachedFrames}
        |Frames Missing:                       ${warmState.missingFrames}
        |Old Frames Removed:                    ${oldFramesRemovedCount.get()}
        |New Frames Added:                      ${newFramesAddedCount.get()}
        |Queue Length:                         ${downloadQueueSize.get()}
        |Average Queue Wait:                    ${avgQueueWaitMs} ms
        |Cancelled Downloads:                  ${cancelledDownloads.get()}
        |HTTP 429 Responses:                   ${total429Responses.get()}
        |Cache Efficiency (%):                 ${String.format("%.1f", cacheEfficiencyPct)}%
        |Network Requests Saved:               $requestsSaved
        |-----------------------------------------------------------------------------
        |Avg Playback Readiness:               ${String.format("%.1f", avgReadinessPct)}%
        |RAM Cache Size:                       ${TileRamCache.size()} tiles (${TileRamCache.sizeInKb()} KB)
        |Disk Cache Size:                      ${DiskTileCache.fileCount()} files (${String.format("%.2f", DiskTileCache.diskSizeBytes() / (1024.0 * 1024.0))} MB)
        |=============================================================================
        """.trimMargin()
        Log.d(TAG, summary)
    }

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
        visibleTileRequests.incrementAndGet()
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
        ramCacheHits.incrementAndGet()
        totalCacheHits.incrementAndGet()
        Log.d(TAG, "[RAM Cache Hit] Key: $key | CacheHitPct: ${String.format("%.1f", getCacheHitPercentage())}%")
    }

    fun logRamCacheMiss(key: String) {
        totalCacheMisses.incrementAndGet()
        Log.d(TAG, "[RAM Cache Miss] Key: $key | CacheHitPct: ${String.format("%.1f", getCacheHitPercentage())}%")
    }

    fun logDiskCacheHit(key: String) {
        diskCacheHits.incrementAndGet()
        totalCacheHits.incrementAndGet()
        Log.d(TAG, "[Disk Cache Hit] Key: $key | CacheHitPct: ${String.format("%.1f", getCacheHitPercentage())}%")
    }

    fun logDiskCacheMiss(key: String) {
        totalCacheMisses.incrementAndGet()
        Log.d(TAG, "[Disk Cache Miss] Key: $key | CacheHitPct: ${String.format("%.1f", getCacheHitPercentage())}%")
    }

    fun logDuplicateRequest(key: String) {
        inFlightReuseCount.incrementAndGet()
        duplicateRequestsPrevented.incrementAndGet()
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
        downloadCancellationCount.incrementAndGet()
        Log.w(TAG, "[DIAG DETECT] ANOMALY: Asynchronous coroutine/job '$jobType' for Frame $oldFrameIndex (Seq: $oldSeq) was CANCELLED or OVERWRITTEN by new job for Frame $newFrameIndex (Seq: $newSeq)!")
    }

    fun logSessionStart(sessionId: String, layer: String, zoom: Int, totalTiles: Int) {
        Log.d(TAG, "[PRELOAD SESSION START] SessionID: $sessionId | Layer: $layer | Zoom: $zoom | TotalTiles: $totalTiles")
    }

    fun logSessionCancel(sessionId: String, reason: String, cancelledTaskCount: Int) {
        downloadCancellationCount.addAndGet(cancelledTaskCount.toLong())
        Log.w(TAG, "[PRELOAD SESSION CANCEL] SessionID: $sessionId | Reason: $reason | CancelledTasks: $cancelledTaskCount")
    }

    fun logSessionComplete(sessionId: String, zoom: Int, loadedTiles: Int, totalTiles: Int) {
        Log.d(TAG, "[PRELOAD SESSION COMPLETE] SessionID: $sessionId | Zoom: $zoom | Loaded: $loadedTiles/$totalTiles")
    }

    fun logTileLifecycle(requestId: String, key: String, stage: String, details: String = "") {
        Log.d(TAG, "[TILE TRACE] RequestID: $requestId | Key: $key | Stage: $stage | $details")
    }
}

