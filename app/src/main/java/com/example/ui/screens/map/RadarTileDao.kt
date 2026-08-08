package com.example.ui.screens.map

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RadarTileDao {
    @Query("SELECT * FROM radar_tiles WHERE tileKey = :key LIMIT 1")
    suspend fun getTileByKey(key: String): RadarTileEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM radar_tiles WHERE tileKey = :key LIMIT 1)")
    suspend fun containsTile(key: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTile(tile: RadarTileEntity)

    @Query("DELETE FROM radar_tiles WHERE timestamp = :timestamp")
    suspend fun deleteTilesByTimestamp(timestamp: Long): Int

    @Query("SELECT COUNT(*) FROM radar_tiles")
    fun getTileCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM radar_tiles")
    suspend fun getTileCountSync(): Int

    @Query("DELETE FROM radar_tiles")
    suspend fun clearAll()
}
