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

    // Dynamically calculate memory budget: ~15% of max JVM heap, bounded between 16MB and 48MB
    private val maxMemoryKb: Int by lazy {
        val maxHeapKb = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        val budgetKb = maxHeapKb / 6 // ~16.6% of heap
        budgetKb.coerceIn(16 * 1024, 48 * 1024) // 16 MB min, 48 MB max
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
                Log.d("RadarCache", "RAM Cache Hit | Key: $key | Current RAM Cache Size: ${cache.snapshot().size} tiles (${cache.size()} KB / $maxMemoryKb KB)")
                return bitmap
            }
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

    fun clear(reason: String = "Explicit clear requested") {
        synchronized(cache) {
            val count = cache.snapshot().size
            Log.d("RadarCache", "Cache Eviction (RAM Cache Cleared) | Evicted $count tiles | Reason: $reason")
            cache.evictAll()
        }
    }
}

