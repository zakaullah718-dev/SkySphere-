package com.example.ui.screens.map

import android.graphics.Bitmap
import android.util.Log
import android.util.LruCache

/**
 * Temporary RAM memory cache for preloaded weather layer tile Bitmaps.
 * Zero disk persistence.
 * Automatically cleared on layer change, location change, or screen exit.
 */
object TileRamCache {
    private const val MAX_TILES = 220 // Efficient memory footprint (~35-45MB RAM max)

    private val cache = object : LruCache<String, Bitmap>(MAX_TILES) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int = 1
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
            synchronized(cache) {
                cache.put(key, bitmap)
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
                Log.d("TileRamCache", "Retained ${validKeys.size} active tiles; evicted $evicted old tiles.")
            }
        }
    }

    fun size(): Int {
        synchronized(cache) {
            return cache.size()
        }
    }

    fun clear() {
        synchronized(cache) {
            Log.d("TileRamCache", "Clearing RAM cache (${cache.size()} tiles evicted).")
            cache.evictAll()
        }
    }
}
