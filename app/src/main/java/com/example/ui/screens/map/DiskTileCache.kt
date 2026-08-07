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

    fun fileCount(): Int {
        val dir = cacheDir ?: return 0
        return dir.listFiles()?.size ?: 0
    }

    fun get(key: String): Bitmap? {
        val file = getFileForKey(key) ?: return null
        if (!file.exists() || file.length() == 0L) {
            RadarDiag.logDiskCacheMiss(key)
            Log.d("SKYSPHERE_TIMELAPSE", "CACHE=DISK RESULT=MISS KEY=$key")
            return null
        }

        return try {
            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            if (bitmap != null) {
                RadarDiag.logDiskCacheHit(key)
                Log.d("SKYSPHERE_TIMELAPSE", "CACHE=DISK RESULT=HIT KEY=$key FILES=${fileCount()}")
            } else {
                RadarDiag.logDiskCacheMiss(key)
                Log.d("SKYSPHERE_TIMELAPSE", "CACHE=DISK RESULT=MISS KEY=$key")
            }
            bitmap
        } catch (e: Exception) {
            RadarDiag.logDiskCacheMiss(key)
            Log.w("SKYSPHERE_TIMELAPSE", "CACHE=DISK RESULT=CORRUPT KEY=$key")
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
            Log.d("SKYSPHERE_TIMELAPSE", "CACHE=DISK RESULT=PUT KEY=$key FILES=${fileCount()}")
        } catch (e: Exception) {
            Log.w("SKYSPHERE_TIMELAPSE", "CACHE=DISK RESULT=WRITE_FAILED KEY=$key Error=${e.localizedMessage}")
        }
    }

    fun contains(key: String): Boolean {
        val file = getFileForKey(key) ?: return false
        return file.exists() && file.length() > 0L
    }

    fun diskSizeBytes(): Long {
        val dir = cacheDir ?: return 0L
        return dir.listFiles()?.sumOf { it.length() } ?: 0L
    }

    fun evictFrameByTimestamp(timestamp: Long): Int {
        val dir = cacheDir ?: return 0
        val targetPattern = "_${timestamp}_"
        val filesToDelete = dir.listFiles()?.filter { it.name.contains(targetPattern) } ?: emptyList()
        var deletedCount = 0
        filesToDelete.forEach { file ->
            if (file.delete()) {
                deletedCount++
            }
        }
        if (deletedCount > 0) {
            RadarDiag.logDeletedOldTiles(deletedCount.toLong())
            RadarDiag.logEvictFrame(timestamp, deletedCount)
            Log.d(TAG, "Evicted $deletedCount files for frame $timestamp from Disk Cache. Remaining files: ${fileCount()}")
        }
        return deletedCount
    }

    fun clear(reason: String = "Explicit clear requested") {
        val dir = cacheDir ?: return
        try {
            val count = dir.listFiles()?.size ?: 0
            dir.listFiles()?.forEach { it.delete() }
            RadarDiag.logCacheClear("Disk Cache", reason, count)
            Log.d("RadarCache", "Cache Eviction (Disk Cache Cleared) | Evicted $count tile files | Reason: $reason")
        } catch (e: Exception) {
            Log.w("RadarCache", "Error clearing DiskTileCache: ${e.localizedMessage}")
        }
    }
}
