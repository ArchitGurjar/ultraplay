package com.ultrastream.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class PreferencesManager @Inject constructor(@ApplicationContext private val context: Context) {

    companion object {
        val THEME_KEY = stringPreferencesKey("theme")
        val DEBRID_KEY = stringPreferencesKey("debrid_key")
        val CURRENT_PROFILE_KEY = stringPreferencesKey("current_profile")
        val HINDI_PRIORITY_KEY = booleanPreferencesKey("hindi_priority")
        val AUTO_PLAY_NEXT_KEY = booleanPreferencesKey("auto_play_next")
        val PARENTAL_CONTROL_KEY = booleanPreferencesKey("parental_control")
        val PARENTAL_RATING_KEY = stringPreferencesKey("parental_rating")
    }

    suspend fun setTheme(theme: String) {
        context.dataStore.edit { preferences ->
            preferences[THEME_KEY] = theme
        }
    }

    fun getTheme(): Flow<String> {
        return context.dataStore.data.map { preferences ->
            preferences[THEME_KEY] ?: "dark"
        }
    }

    suspend fun setDebridKey(key: String) {
        context.dataStore.edit { preferences ->
            preferences[DEBRID_KEY] = key
        }
    }

    fun getDebridKey(): Flow<String> {
        return context.dataStore.data.map { preferences ->
            preferences[DEBRID_KEY] ?: ""
        }
    }

    suspend fun setCurrentProfile(profileId: String) {
        context.dataStore.edit { preferences ->
            preferences[CURRENT_PROFILE_KEY] = profileId
        }
    }

    fun getCurrentProfile(): Flow<String> {
        return context.dataStore.data.map { preferences ->
            preferences[CURRENT_PROFILE_KEY] ?: "default"
        }
    }

    suspend fun setHindiPriority(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[HINDI_PRIORITY_KEY] = enabled
        }
    }

    fun getHindiPriority(): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences[HINDI_PRIORITY_KEY] ?: true
        }
    }

    suspend fun setAutoPlayNext(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[AUTO_PLAY_NEXT_KEY] = enabled
        }
    }

    fun getAutoPlayNext(): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences[AUTO_PLAY_NEXT_KEY] ?: false
        }
    }

    suspend fun setParentalControl(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PARENTAL_CONTROL_KEY] = enabled
        }
    }

    fun getParentalControl(): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences[PARENTAL_CONTROL_KEY] ?: false
        }
    }

    suspend fun setParentalRating(rating: String) {
        context.dataStore.edit { preferences ->
            preferences[PARENTAL_RATING_KEY] = rating
        }
    }

    fun getParentalRating(): Flow<String> {
        return context.dataStore.data.map { preferences ->
            preferences[PARENTAL_RATING_KEY] ?: "PG-13"
        }
    }

    suspend fun clearAll() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }
}

