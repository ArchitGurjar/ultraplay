package com.ultrastream.app.domain.usecase

import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.ultrastream.app.data.dao.*
import com.ultrastream.app.data.models.*
import com.ultrastream.app.data.preferences.PreferencesManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupRestoreUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val libraryDao: LibraryDao,
    private val watchlistDao: WatchlistDao,
    private val historyDao: HistoryDao,
    private val watchProgressDao: WatchProgressDao,
    private val watchedEpisodeDao: WatchedEpisodeDao,
    private val smartPlaylistDao: SmartPlaylistDao,
    private val profileDao: ProfileDao,
    private val addonDao: AddonDao,
    private val preferencesManager: PreferencesManager
) {

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    data class BackupData(
        val version: String = "1.0",
        val addons: List<Addon> = emptyList(),
        val library: List<LibraryItem> = emptyList(),
        val watchlist: List<WatchlistItem> = emptyList(),
        val history: List<HistoryItem> = emptyList(),
        val watchProgress: List<WatchProgress> = emptyList(),
        val watchedEpisodes: List<WatchedEpisode> = emptyList(),
        val smartPlaylists: List<SmartPlaylist> = emptyList(),
        val profiles: List<Profile> = emptyList(),
        val currentProfile: String = "",
        val settings: Map<String, String> = emptyMap(),
        val debridKey: String = ""
    )

    suspend fun exportData(): String {
        val addons = addonDao.getAll()
        val library = libraryDao.getAll()
        val watchlist = watchlistDao.getAll()
        val history = historyDao.getAll()
        val watchProgress = watchProgressDao.getAll()
        val watchedEpisodes = watchedEpisodeDao.getAll()
        val smartPlaylists = smartPlaylistDao.getAll()
        val profiles = profileDao.getAll()
        val currentProfile = preferencesManager.getCurrentProfile().first()
        val debridKey = preferencesManager.getDebridKey().first()
        val settings = mapOf(
            "theme" to preferencesManager.getTheme().first(),
            "hindiPriority" to preferencesManager.getHindiPriority().first().toString(),
            "autoPlayNext" to preferencesManager.getAutoPlayNext().first().toString(),
            "parentalControl" to preferencesManager.getParentalControl().first().toString(),
            "parentalRating" to preferencesManager.getParentalRating().first(),
            "subtitleLanguage" to preferencesManager.getSubtitleLanguage().first(),
            "debridProvider" to preferencesManager.getDebridProvider().first()
        )

        val data = BackupData(
            addons = addons,
            library = library,
            watchlist = watchlist,
            history = history,
            watchProgress = watchProgress,
            watchedEpisodes = watchedEpisodes,
            smartPlaylists = smartPlaylists,
            profiles = profiles,
            currentProfile = currentProfile,
            settings = settings,
            debridKey = debridKey
        )
        return moshi.adapter(BackupData::class.java).toJson(data)
    }

    suspend fun importData(json: String): Boolean {
        return try {
            val data = moshi.adapter(BackupData::class.java).fromJson(json) ?: return false

            // Clear existing data
            addonDao.deleteAll()
            libraryDao.deleteAll()
            watchlistDao.deleteAll()
            historyDao.deleteAll()
            watchProgressDao.deleteAll()
            watchedEpisodeDao.deleteAll()
            smartPlaylistDao.deleteAll()
            profileDao.deleteAll()

            // Insert new data
            addonDao.insertAll(data.addons)
            libraryDao.insertAll(data.library)
            watchlistDao.insertAll(data.watchlist)
            historyDao.insertAll(data.history)
            watchProgressDao.insertAll(data.watchProgress)
            watchedEpisodeDao.insertAll(data.watchedEpisodes)
            smartPlaylistDao.insertAll(data.smartPlaylists)
            profileDao.insertAll(data.profiles)

            // Restore preferences
            preferencesManager.setCurrentProfile(data.currentProfile)
            preferencesManager.setDebridKey(data.debridKey)
            data.settings.forEach { (key, value) ->
                when (key) {
                    "theme" -> preferencesManager.setTheme(value)
                    "hindiPriority" -> preferencesManager.setHindiPriority(value.toBoolean())
                    "autoPlayNext" -> preferencesManager.setAutoPlayNext(value.toBoolean())
                    "parentalControl" -> preferencesManager.setParentalControl(value.toBoolean())
                    "parentalRating" -> preferencesManager.setParentalRating(value)
                    "subtitleLanguage" -> preferencesManager.setSubtitleLanguage(value)
                    "debridProvider" -> preferencesManager.setDebridProvider(value)
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}
