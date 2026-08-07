package com.example.ui.screens.map

import android.graphics.Bitmap
import android.util.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * Protected RAM pool for active time-lapse playback tiles.
 * Prevents normal map tile requests and LRU eviction from discarding
 * tiles belonging to the currently prepared time-lapse sequence.
 */
object PlaybackProtectedCache {
    private const val TAG = "PlaybackProtectedCache"

    private val protectedTiles = ConcurrentHashMap<String, Bitmap>()
    private val activePlaybackKeys = ConcurrentHashMap.newKeySet<String>()

    fun setProtectedKeys(keys: Collection<String>) {
        activePlaybackKeys.clear()
        activePlaybackKeys.addAll(keys)
        keys.forEach { key ->
            Log.d("SKYSPHERE_TIMELAPSE", "TILE_PROTECTED key=$key")
        }
        
        // Retain only tiles that are still part of the active playback sequence
        val beforeCount = protectedTiles.size
        protectedTiles.keys.retainAll(activePlaybackKeys)
        val evictedCount = beforeCount - protectedTiles.size
        
        Log.d(
            "SKYSPHERE_TIMELAPSE",
            "PLAYBACK_PROTECTED_SET_KEYS required_count=${keys.size} protected_tiles=${protectedTiles.size} evicted_tiles=$evictedCount"
        )
    }

    fun addProtectedKeys(keys: Collection<String>) {
        activePlaybackKeys.addAll(keys)
        keys.forEach { key ->
            Log.d("SKYSPHERE_TIMELAPSE", "TILE_PROTECTED key=$key")
        }
    }

    fun put(key: String, bitmap: Bitmap) {
        if (!bitmap.isRecycled) {
            protectedTiles[key] = bitmap
            Log.d(
                "SKYSPHERE_TIMELAPSE",
                "TILE_PROTECTED key=$key"
            )
            Log.d(
                "SKYSPHERE_TIMELAPSE",
                "PLAYBACK_PROTECTED RESULT=PUT KEY=$key PROTECTED_TILES=${protectedTiles.size}"
            )
        }
    }

    fun get(key: String): Bitmap? {
        val bitmap = protectedTiles[key]
        if (bitmap != null && !bitmap.isRecycled) {
            Log.d("SKYSPHERE_TIMELAPSE", "CACHE=PLAYBACK_PROTECTED RESULT=HIT KEY=$key")
            return bitmap
        }
        return null
    }

    fun contains(key: String): Boolean {
        val bitmap = protectedTiles[key]
        return bitmap != null && !bitmap.isRecycled
    }

    fun isProtectedKey(key: String): Boolean {
        return activePlaybackKeys.contains(key) || protectedTiles.containsKey(key)
    }

    fun clear(reason: String = "Explicit clear") {
        val count = protectedTiles.size
        protectedTiles.clear()
        activePlaybackKeys.clear()
        Log.d(
            "SKYSPHERE_TIMELAPSE",
            "PLAYBACK_PROTECTED RESULT=CLEAR Reason=$reason EVICTED_TILES=$count"
        )
    }

    fun size(): Int = protectedTiles.size
}
