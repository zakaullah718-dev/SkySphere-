package com.example.ui.screens.map

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

class RadarTileRepository(private val radarTileDao: RadarTileDao) {

    val tileCount: Flow<Int> = radarTileDao.getTileCount()

    suspend fun getTileBitmap(key: String): Bitmap? = withContext(Dispatchers.IO) {
        val entity = radarTileDao.getTileByKey(key) ?: return@withContext null
        try {
            BitmapFactory.decodeByteArray(entity.tileData, 0, entity.tileData.size)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun containsTile(key: String): Boolean = withContext(Dispatchers.IO) {
        radarTileDao.containsTile(key)
    }

    suspend fun saveTileBitmap(
        key: String,
        bitmap: Bitmap,
        layer: String = "radar",
        timestamp: Long = 0L,
        zoom: Int = 0,
        x: Int = 0,
        y: Int = 0
    ) = withContext(Dispatchers.IO) {
        if (bitmap.isRecycled) return@withContext
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        val bytes = stream.toByteArray()
        val entity = RadarTileEntity(
            tileKey = key,
            layer = layer,
            timestamp = timestamp,
            zoom = zoom,
            x = x,
            y = y,
            tileData = bytes
        )
        radarTileDao.insertTile(entity)
    }

    suspend fun deleteTilesByTimestamp(timestamp: Long): Int = withContext(Dispatchers.IO) {
        radarTileDao.deleteTilesByTimestamp(timestamp)
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        radarTileDao.clearAll()
    }
}
