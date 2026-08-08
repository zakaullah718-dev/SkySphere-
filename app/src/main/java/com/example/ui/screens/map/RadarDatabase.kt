package com.example.ui.screens.map

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [RadarTileEntity::class], version = 1, exportSchema = false)
abstract class RadarDatabase : RoomDatabase() {
    abstract fun radarTileDao(): RadarTileDao

    companion object {
        @Volatile
        private var INSTANCE: RadarDatabase? = null

        fun getDatabase(context: Context): RadarDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    RadarDatabase::class.java,
                    "radar_tiles_db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
