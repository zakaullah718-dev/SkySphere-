package com.example.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "cached_weather")
data class CachedWeatherEntity(
    @PrimaryKey val id: String,
    val cityName: String,
    val country: String,
    val weatherJson: String,
    val isFavorite: Boolean,
    val timestamp: Long
)

@Entity(tableName = "recent_searches")
data class RecentSearchEntity(
    @PrimaryKey val query: String,
    val timestamp: Long
)

@Dao
interface WeatherDao {
    @Query("SELECT * FROM cached_weather WHERE id = :id")
    suspend fun getCachedWeather(id: String): CachedWeatherEntity?

    @Query("SELECT * FROM cached_weather ORDER BY timestamp DESC")
    fun getAllCachedWeatherFlow(): Flow<List<CachedWeatherEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCachedWeather(cached: CachedWeatherEntity)

    @Query("DELETE FROM cached_weather WHERE id = :id")
    suspend fun deleteCachedWeather(id: String)

    @Query("UPDATE cached_weather SET isFavorite = :isFav WHERE id = :id")
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

@Database(entities = [CachedWeatherEntity::class, RecentSearchEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun weatherDao(): WeatherDao
    abstract fun recentSearchDao(): RecentSearchDao
}
