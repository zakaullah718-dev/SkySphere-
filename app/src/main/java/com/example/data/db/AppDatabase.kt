package com.example.data.db

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "cached_weather")
data class CachedWeatherEntity(
    @PrimaryKey val id: String,
    val cityName: String,
    val country: String,
    val weatherJson: String,
    val isFavorite: Boolean,
    val timestamp: Long,
    val region: String? = null
)

@Entity(tableName = "recent_searches")
data class RecentSearchEntity(
    @PrimaryKey val query: String,
    val timestamp: Long
)

@Entity(tableName = "radar_metadata")
data class RadarMetadataEntity(
    @PrimaryKey val key: String = "latest_radar_frames",
    val host: String,
    val framesJson: String,
    val timestamp: Long
)

@Entity(tableName = "favorite_locations")
data class FavoriteLocationEntity(
    @PrimaryKey val id: String,
    val cityName: String,
    val country: String,
    val region: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val timeZoneId: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val weatherJson: String? = null
)

@Dao
interface WeatherDao {
    @Query("SELECT * FROM cached_weather WHERE id = :id OR LOWER(cityName) = LOWER(:id) ORDER BY (id = :id) DESC LIMIT 1")
    suspend fun getCachedWeather(id: String): CachedWeatherEntity?

    @Query("SELECT * FROM cached_weather ORDER BY timestamp DESC")
    fun getAllCachedWeatherFlow(): Flow<List<CachedWeatherEntity>>

    @Query("SELECT * FROM cached_weather ORDER BY timestamp DESC")
    suspend fun getAllCachedWeather(): List<CachedWeatherEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCachedWeather(cached: CachedWeatherEntity)

    @Query("DELETE FROM cached_weather WHERE id = :id")
    suspend fun deleteCachedWeather(id: String)

    @Query("UPDATE cached_weather SET isFavorite = :isFav WHERE id = :id OR LOWER(cityName) = LOWER(:id)")
    suspend fun updateFavorite(id: String, isFav: Boolean)
}

@Dao
interface RecentSearchDao {
    @Query("SELECT * FROM recent_searches ORDER BY timestamp DESC LIMIT 10")
    fun getRecentSearchesFlow(): Flow<List<RecentSearchEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecentSearch(search: RecentSearchEntity)

    @Query("DELETE FROM recent_searches WHERE `query` = :query")
    suspend fun deleteRecentSearch(query: String)

    @Query("DELETE FROM recent_searches")
    suspend fun clearAll()
}

@Dao
interface RadarMetadataDao {
    @Query("SELECT * FROM radar_metadata WHERE `key` = :key LIMIT 1")
    suspend fun getRadarMetadata(key: String = "latest_radar_frames"): RadarMetadataEntity?

    @Query("SELECT * FROM radar_metadata WHERE `key` = :key LIMIT 1")
    fun getRadarMetadataFlow(key: String = "latest_radar_frames"): Flow<RadarMetadataEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRadarMetadata(metadata: RadarMetadataEntity)

    @Query("DELETE FROM radar_metadata WHERE `key` = :key")
    suspend fun deleteRadarMetadata(key: String = "latest_radar_frames")
}

@Dao
interface FavoriteLocationDao {
    @Query("SELECT * FROM favorite_locations ORDER BY timestamp ASC")
    fun getAllFavoritesFlow(): Flow<List<FavoriteLocationEntity>>

    @Query("SELECT * FROM favorite_locations ORDER BY timestamp ASC")
    suspend fun getAllFavorites(): List<FavoriteLocationEntity>

    @Query("SELECT * FROM favorite_locations WHERE id = :id OR (LOWER(cityName) = LOWER(:cityName) AND LOWER(country) = LOWER(:country)) LIMIT 1")
    suspend fun getFavorite(id: String, cityName: String, country: String): FavoriteLocationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(favorite: FavoriteLocationEntity)

    @Query("DELETE FROM favorite_locations WHERE id = :id OR (LOWER(cityName) = LOWER(:cityName) AND LOWER(country) = LOWER(:country))")
    suspend fun deleteFavorite(id: String, cityName: String, country: String)

    @Query("DELETE FROM favorite_locations WHERE LOWER(cityName) = LOWER(:cityName)")
    suspend fun deleteFavoriteByCityName(cityName: String)

    @Query("DELETE FROM favorite_locations")
    suspend fun clearAllFavorites()
}

@Database(
    entities = [CachedWeatherEntity::class, RecentSearchEntity::class, RadarMetadataEntity::class, FavoriteLocationEntity::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun weatherDao(): WeatherDao
    abstract fun recentSearchDao(): RecentSearchDao
    abstract fun radarMetadataDao(): RadarMetadataDao
    abstract fun favoriteLocationDao(): FavoriteLocationDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "skysphere_weather.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
