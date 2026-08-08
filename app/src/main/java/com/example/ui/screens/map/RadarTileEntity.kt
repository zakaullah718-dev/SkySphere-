package com.example.ui.screens.map

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "radar_tiles")
data class RadarTileEntity(
    @PrimaryKey val tileKey: String,
    val layer: String,
    val timestamp: Long,
    val zoom: Int,
    val x: Int,
    val y: Int,
    val tileData: ByteArray,
    val createdTime: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RadarTileEntity

        if (tileKey != other.tileKey) return false
        if (!tileData.contentEquals(other.tileData)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = tileKey.hashCode()
        result = 31 * result + tileData.contentHashCode()
        return result
    }
}
