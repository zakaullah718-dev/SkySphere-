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
    }

    fun isLowMemoryDevice(): Boolean {
        val maxHeapMb = Runtime.getRuntime().maxMemory() / (1024 * 1024)
        return maxHeapMb <= 192
    }

    fun get(key: String): Bitmap? {
        synchronized(cache) {
            val bitmap = cache.get(key)
            if (bitmap != null && !bitmap.isRecycled) {
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
                }
            } catch (oom: OutOfMemoryError) {
                Log.e(TAG, "OutOfMemoryError putting bitmap into cache. Clearing RAM cache.")
                clear()
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

    /**
     * Retains only specified keys, evicting any old or stale tile bitmaps from RAM.
     * Called when animation finishes, layer changes, or playback pauses.
     */
    fun retainOnly(validKeys: Set<String>) {
        if (validKeys.isEmpty()) return
        synchronized(cache) {
            val snapshot = cache.snapshot()
            var evicted = 0
            for (key in snapshot.keys) {
                if (key !in validKeys) {
                    cache.remove(key)
                    evicted++
                }
            }
            if (evicted > 0) {
                Log.d(TAG, "Retained ${validKeys.size} active tiles; evicted $evicted old tiles.")
            }
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

    fun clear() {
        synchronized(cache) {
            Log.d(TAG, "Clearing RAM cache (${cache.snapshot().size} tiles evicted).")
            cache.evictAll()
        }
    }
}

