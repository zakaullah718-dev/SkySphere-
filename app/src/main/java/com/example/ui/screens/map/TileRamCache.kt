package com.example.ui.screens.map

import android.graphics.Bitmap
import android.util.Log
import android.util.LruCache

/**
 * Temporary RAM memory cache for preloaded weather layer tile Bitmaps.
 * Zero disk persistence.
 * Dynamically sized based on available JVM heap with LRU byte eviction,
 * low-end device detection, and OutOfMemory safeguards.
 */
object TileRamCache {
    private const val TAG = "TileRamCache"

    // Dynamic maximum RAM cache budget based on available JVM heap (120MB - 250MB)
    private val maxMemoryKb: Int by lazy {
        val maxHeapKb = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        (maxHeapKb * 0.35).toInt().coerceIn(120_000, 250_000)
    }

    private val cache = object : LruCache<String, Bitmap>(maxMemoryKb) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return (bitmap.byteCount / 1024).coerceAtLeast(1)
        }

        override fun entryRemoved(evicted: Boolean, key: String, oldValue: Bitmap, newValue: Bitmap?) {
            if (evicted) {
                Log.d("RadarCache", "Cache Eviction (RAM LRU Limit Reached) | Key: $key | Remaining: ${size()} tiles (${sizeInKb()} KB)")
            }
        }
    }

    fun isLowMemoryDevice(): Boolean {
        val maxHeapMb = Runtime.getRuntime().maxMemory() / (1024 * 1024)
        return maxHeapMb <= 192
    }

    fun get(key: String): Bitmap? {
        synchronized(cache) {
            val bitmap = cache.get(key)
            if (bitmap != null && !bitmap.isRecycled) {
                RadarDiag.logRamCacheHit(key)
                Log.d("RadarCache", "RAM Cache Hit | Key: $key | Current RAM Cache Size: ${cache.snapshot().size} tiles (${cache.size()} KB / $maxMemoryKb KB)")
                return bitmap
            }
            RadarDiag.logRamCacheMiss(key)
            return null
        }
    }

    fun put(key: String, bitmap: Bitmap) {
        if (!bitmap.isRecycled) {
            try {
                synchronized(cache) {
                    cache.put(key, bitmap)
                    Log.d("RadarCache", "RAM Cache Put | Key: $key | Current RAM Cache Size: ${cache.snapshot().size} tiles (${cache.size()} KB / $maxMemoryKb KB)")
                }
            } catch (oom: OutOfMemoryError) {
                Log.e("RadarCache", "Cache Eviction (OutOfMemoryError) | Clearing RAM cache completely.")
                clear("OutOfMemoryError safeguard")
            } catch (e: Exception) {
                Log.w(TAG, "Error caching bitmap: ${e.localizedMessage}")
            }
        }
    }

    fun contains(key: String): Boolean {
        synchronized(cache) {
            val bitmap = cache.get(key)
            return bitmap != null && !bitmap.isRecycled
        }
    }

    fun sizeInKb(): Int {
        synchronized(cache) {
            return cache.size()
        }
    }

    fun size(): Int {
        synchronized(cache) {
            return cache.snapshot().size
        }
    }

    fun evictFrameByTimestamp(timestamp: Long) {
        synchronized(cache) {
            val prefix = "_${timestamp}_"
            val keysToRemove = cache.snapshot().keys.filter { it.contains(prefix) }
            keysToRemove.forEach { key ->
                cache.remove(key)
            }
            if (keysToRemove.isNotEmpty()) {
                RadarDiag.logEvictFrame(timestamp, keysToRemove.size)
                Log.d("RadarCache", "Evicted ${keysToRemove.size} tiles for frame $timestamp from RAM Cache. Remaining: ${size()} tiles (${sizeInKb()} KB)")
            }
        }
    }

    fun clear(reason: String = "Explicit clear requested") {
        synchronized(cache) {
            val count = cache.snapshot().size
            RadarDiag.logCacheClear("RAM Cache", reason, count)
            Log.d("RadarCache", "Cache Eviction (RAM Cache Cleared) | Evicted $count tiles | Reason: $reason")
            cache.evictAll()
        }
    }
}

