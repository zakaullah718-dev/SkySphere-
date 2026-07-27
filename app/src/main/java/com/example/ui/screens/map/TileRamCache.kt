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
    private const val MAX_TILES = 600 // ~150MB max RAM footprint

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
