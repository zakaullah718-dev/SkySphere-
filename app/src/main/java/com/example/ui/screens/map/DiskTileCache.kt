package com.example.ui.screens.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Local disk cache for radar and weather map tile PNGs.
 * Provides persistent local tile re-use across screen transitions and app restarts,
 * drastically reducing duplicate network requests.
 */
object DiskTileCache {
    private const val TAG = "DiskTileCache"
    private var cacheDir: File? = null

    fun init(context: Context) {
        if (cacheDir == null) {
            val dir = File(context.cacheDir, "radar_tiles")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            cacheDir = dir
            Log.d(TAG, "DiskTileCache initialized at ${dir.absolutePath}")
        }
    }

    private fun getFileForKey(key: String): File? {
        val dir = cacheDir ?: return null
        val safeFileName = key.replace(Regex("[^a-zA-Z0-9_-]"), "_") + ".png"
        return File(dir, safeFileName)
    }

    fun get(key: String): Bitmap? {
        val file = getFileForKey(key) ?: return null
        if (!file.exists() || file.length() == 0L) return null

        return try {
            BitmapFactory.decodeFile(file.absolutePath)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to decode disk-cached tile for key '$key': ${e.localizedMessage}")
            file.delete()
            null
        }
    }

    fun put(key: String, bitmap: Bitmap) {
        val file = getFileForKey(key) ?: return
        if (bitmap.isRecycled) return

        try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save tile to disk cache for key '$key': ${e.localizedMessage}")
        }
    }

    fun contains(key: String): Boolean {
        val file = getFileForKey(key) ?: return false
        return file.exists() && file.length() > 0L
    }

    fun clear() {
        val dir = cacheDir ?: return
        try {
            dir.listFiles()?.forEach { it.delete() }
            Log.d(TAG, "DiskTileCache cleared.")
        } catch (e: Exception) {
            Log.w(TAG, "Error clearing DiskTileCache: ${e.localizedMessage}")
        }
    }
}
