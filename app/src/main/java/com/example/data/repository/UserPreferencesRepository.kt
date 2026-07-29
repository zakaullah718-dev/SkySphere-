package com.example.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "skysphere_user_prefs")

class UserPreferencesRepository(private val context: Context) {

    companion object {
        val KEY_USER_NAME = stringPreferencesKey("user_display_name")
        val KEY_NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val KEY_NOTIFICATION_INTERVAL_HOURS = intPreferencesKey("notification_interval_hours")
        val KEY_LAST_NOTIFICATION_HASH = stringPreferencesKey("last_notification_hash")
        val KEY_LAST_NOTIFICATION_TIME = longPreferencesKey("last_notification_time")

        @Volatile
        private var INSTANCE: UserPreferencesRepository? = null

        fun getInstance(context: Context): UserPreferencesRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: UserPreferencesRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    val userNameFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_USER_NAME] ?: ""
    }

    val notificationsEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_NOTIFICATIONS_ENABLED] ?: true
    }

    val notificationIntervalHoursFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_NOTIFICATION_INTERVAL_HOURS] ?: 6
    }

    suspend fun getUserName(): String {
        return context.dataStore.data.map { prefs -> prefs[KEY_USER_NAME] ?: "" }.first()
    }

    suspend fun setUserName(name: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_USER_NAME] = name.trim()
        }
    }

    suspend fun isNotificationsEnabled(): Boolean {
        return context.dataStore.data.map { prefs -> prefs[KEY_NOTIFICATIONS_ENABLED] ?: true }.first()
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_NOTIFICATIONS_ENABLED] = enabled
        }
    }

    suspend fun getNotificationIntervalHours(): Int {
        return context.dataStore.data.map { prefs -> prefs[KEY_NOTIFICATION_INTERVAL_HOURS] ?: 6 }.first()
    }

    suspend fun setNotificationIntervalHours(hours: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_NOTIFICATION_INTERVAL_HOURS] = hours
        }
    }

    suspend fun isDuplicateNotification(hash: String, windowMillis: Long = 3 * 3600 * 1000L): Boolean {
        val data = context.dataStore.data.first()
        val lastHash = data[KEY_LAST_NOTIFICATION_HASH] ?: ""
        val lastTime = data[KEY_LAST_NOTIFICATION_TIME] ?: 0L
        val currentTime = System.currentTimeMillis()

        if (lastHash == hash && (currentTime - lastTime) < windowMillis) {
            return true
        }
        return false
    }

    suspend fun recordNotificationSent(hash: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LAST_NOTIFICATION_HASH] = hash
            prefs[KEY_LAST_NOTIFICATION_TIME] = System.currentTimeMillis()
        }
    }
}
