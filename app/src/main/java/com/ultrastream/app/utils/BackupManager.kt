package com.ultrastream.app.utils

import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.ultrastream.app.data.dao.*
import com.ultrastream.app.data.models.*
import com.ultrastream.app.data.preferences.PreferencesManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val addonDao: AddonDao,
    private val libraryDao: LibraryDao,
    private val watchlistDao: WatchlistDao,
    private val historyDao: HistoryDao,
    private val cachedMetaDao: CachedMetaDao,
    private val smartPlaylistDao: SmartPlaylistDao,
    private val profileDao: ProfileDao,
    private val watchProgressDao: WatchProgressDao,
    private val watchedEpisodeDao: WatchedEpisodeDao,
    private val preferencesManager: PreferencesManager
) {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    data class BackupData(
        val version: Int = 1,
        val addons: List<Addon> = emptyList(),
        val library: List<LibraryItem> = emptyList(),
        val watchlist: List<WatchlistItem> = emptyList(),
        val history: List<HistoryItem> = emptyList(),
        val cachedMeta: List<CachedMeta> = emptyList(),
        val smartPlaylists: List<SmartPlaylist> = emptyList(),
        val profiles: List<Profile> = emptyList(),
        val watchProgress: List<WatchProgress> = emptyList(),
        val watchedEpisodes: List<WatchedEpisode> = emptyList(),
        val settings: Map<String, String> = emptyMap()
    )

    suspend fun exportBackup(): String {
        val addons = addonDao.getAll()
        val library = libraryDao.getAll()
        val watchlist = watchlistDao.getAll()
        val history = historyDao.getAll()
        val cachedMeta = cachedMetaDao.getAll()
        val smartPlaylists = smartPlaylistDao.getAll()
        val profiles = profileDao.getAll()
        val watchProgress = watchProgressDao.getAll()
        val watchedEpisodes = watchedEpisodeDao.getAll()

        val settings = mapOf(
            "theme" to preferencesManager.getTheme().first(),
            "hindiPriority" to preferencesManager.getHindiPriority().first().toString(),
            "autoPlayNext" to preferencesManager.getAutoPlayNext().first().toString(),
            "parentalControl" to preferencesManager.getParentalControl().first().toString(),
            "parentalRating" to preferencesManager.getParentalRating().first(),
            "subtitleLanguage" to preferencesManager.getSubtitleLanguage().first(),
            "debridKey" to preferencesManager.getDebridKey().first(),
            "debridProvider" to preferencesManager.getDebridProvider().first(),
            "currentProfile" to preferencesManager.getCurrentProfile().first()
        )

        val data = BackupData(
            version = 1,
            addons = addons,
            library = library,
            watchlist = watchlist,
            history = history,
            cachedMeta = cachedMeta,
            smartPlaylists = smartPlaylists,
            profiles = profiles,
            watchProgress = watchProgress,
            watchedEpisodes = watchedEpisodes,
            settings = settings
        )
        val adapter = moshi.adapter(BackupData::class.java)
        return adapter.toJson(data)
    }

    suspend fun importBackup(json: String): Boolean {
        return try {
            val adapter = moshi.adapter(BackupData::class.java)
            val data = adapter.fromJson(json) ?: return false

            // Clear all existing data
            addonDao.deleteAll()
            libraryDao.deleteAll()
            watchlistDao.deleteAll()
            historyDao.deleteAll()
            cachedMetaDao.deleteAll()
            smartPlaylistDao.deleteAll()
            profileDao.deleteAll()
            watchProgressDao.deleteAll()
            watchedEpisodeDao.deleteAll()
            preferencesManager.clearAll()

            // Insert new data
            addonDao.insertAll(data.addons)
            libraryDao.insertAll(data.library)
            watchlistDao.insertAll(data.watchlist)
            historyDao.insertAll(data.history)
            cachedMetaDao.insertAll(data.cachedMeta)
            smartPlaylistDao.insertAll(data.smartPlaylists)
            profileDao.insertAll(data.profiles)
            watchProgressDao.insertAll(data.watchProgress)
            watchedEpisodeDao.insertAll(data.watchedEpisodes)

            // Restore settings
            data.settings.forEach { (key, value) ->
                when (key) {
                    "theme" -> preferencesManager.setTheme(value)
                    "hindiPriority" -> preferencesManager.setHindiPriority(value.toBoolean())
                    "autoPlayNext" -> preferencesManager.setAutoPlayNext(value.toBoolean())
                    "parentalControl" -> preferencesManager.setParentalControl(value.toBoolean())
                    "parentalRating" -> preferencesManager.setParentalRating(value)
                    "subtitleLanguage" -> preferencesManager.setSubtitleLanguage(value)
                    "debridKey" -> preferencesManager.setDebridKey(value)
                    "debridProvider" -> preferencesManager.setDebridProvider(value)
                    "currentProfile" -> preferencesManager.setCurrentProfile(value)
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}
