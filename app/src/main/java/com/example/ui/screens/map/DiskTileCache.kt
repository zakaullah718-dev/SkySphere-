package com.example.ui.screens.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Local disk cache for radar and weather map tile PNGs.
 * Uses a hybrid approach with file-based caching and Room Database persistence,
 * allowing previously loaded radar frames to be retrieved offline or during spotty connectivity.
 */
object DiskTileCache {
    private const val TAG = "DiskTileCache"
    private var cacheDir: File? = null
    @Volatile private var repository: RadarTileRepository? = null

    fun init(context: Context) {
        if (cacheDir == null) {
            val dir = File(context.cacheDir, "radar_tiles")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            cacheDir = dir
            Log.d(TAG, "DiskTileCache initialized at ${dir.absolutePath}")
        }
        if (repository == null) {
            val db = RadarDatabase.getDatabase(context)
            repository = RadarTileRepository(db.radarTileDao())
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
        val file = getFileForKey(key)
        if (file != null && file.exists() && file.length() > 0L) {
            try {
                val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                if (bitmap != null) {
                    RadarDiag.logDiskCacheHit(key)
                    Log.d("SKYSPHERE_TIMELAPSE", "CACHE=DISK RESULT=HIT KEY=$key FILES=${fileCount()}")
                    return bitmap
                }
            } catch (e: Exception) {
                file.delete()
            }
        }

        // Fallback to Room Database for offline persistence
        val repo = repository
        if (repo != null) {
            val dbBitmap = runBlocking(Dispatchers.IO) { repo.getTileBitmap(key) }
            if (dbBitmap != null) {
                RadarDiag.logDiskCacheHit(key)
                Log.d("SKYSPHERE_TIMELAPSE", "CACHE=ROOM_DB RESULT=HIT KEY=$key")
                if (file != null && !file.exists()) {
                    try {
                        FileOutputStream(file).use { out ->
                            dbBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                        }
                    } catch (_: Exception) {}
                }
                return dbBitmap
            }
        }

        RadarDiag.logDiskCacheMiss(key)
        Log.d("SKYSPHERE_TIMELAPSE", "CACHE=DISK RESULT=MISS KEY=$key")
        return null
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

        // Also persist tile bitmap to Room Database
        val repo = repository
        if (repo != null) {
            val parts = key.split("_")
            var timestamp = 0L
            var zoom = 0
            var x = 0
            var y = 0
            var layer = "radar"
            if (parts.size >= 5) {
                layer = parts[0]
                timestamp = parts[1].toLongOrNull() ?: 0L
                zoom = parts[2].toIntOrNull() ?: 0
                x = parts[3].toIntOrNull() ?: 0
                y = parts[4].toIntOrNull() ?: 0
            }
            GlobalScope.launch(Dispatchers.IO) {
                try {
                    repo.saveTileBitmap(key, bitmap, layer, timestamp, zoom, x, y)
                } catch (e: Exception) {
                    Log.w("SKYSPHERE_TIMELAPSE", "CACHE=ROOM_DB RESULT=WRITE_FAILED KEY=$key")
                }
            }
        }
    }

    fun contains(key: String): Boolean {
        val file = getFileForKey(key)
        if (file != null && file.exists() && file.length() > 0L) {
            return true
        }
        val repo = repository ?: return false
        return runBlocking(Dispatchers.IO) { repo.containsTile(key) }
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
        val repo = repository
        if (repo != null) {
            GlobalScope.launch(Dispatchers.IO) {
                try {
                    repo.deleteTilesByTimestamp(timestamp)
                } catch (_: Exception) {}
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
            val repo = repository
            if (repo != null) {
                GlobalScope.launch(Dispatchers.IO) {
                    try {
                        repo.clearAll()
                    } catch (_: Exception) {}
                }
            }
            RadarDiag.logCacheClear("Disk Cache", reason, count)
            Log.d("RadarCache", "Cache Eviction (Disk Cache Cleared) | Evicted $count tile files | Reason: $reason")
        } catch (e: Exception) {
            Log.w("RadarCache", "Error clearing DiskTileCache: ${e.localizedMessage}")
        }
    }
}

