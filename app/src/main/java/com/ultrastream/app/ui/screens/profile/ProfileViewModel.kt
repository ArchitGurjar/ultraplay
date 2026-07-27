package com.ultrastream.app.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ultrastream.app.data.dao.*
import com.ultrastream.app.data.models.*
import com.ultrastream.app.data.preferences.PreferencesManager
import com.squareup.moshi.Moshi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager,
    private val libraryDao: LibraryDao,
    private val watchlistDao: WatchlistDao,
    private val historyDao: HistoryDao,
    private val watchProgressDao: WatchProgressDao,
    private val watchedEpisodeDao: WatchedEpisodeDao,
    private val addonDao: AddonDao,
    private val smartPlaylistDao: SmartPlaylistDao,
    private val profileDao: ProfileDao,
    private val cachedMetaDao: CachedMetaDao,
    private val moshi: Moshi
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        loadAnalytics()
        viewModelScope.launch {
            preferencesManager.getTheme().collect { theme ->
                _uiState.value = _uiState.value.copy(theme = theme)
            }
        }
        viewModelScope.launch {
            preferencesManager.getHindiPriority().collect { enabled ->
                _uiState.value = _uiState.value.copy(hindiPriority = enabled)
            }
        }
        viewModelScope.launch {
            preferencesManager.getAutoPlayNext().collect { enabled ->
                _uiState.value = _uiState.value.copy(autoPlayNext = enabled)
            }
        }
        viewModelScope.launch {
            preferencesManager.getParentalControl().collect { enabled ->
                _uiState.value = _uiState.value.copy(parentalControl = enabled)
            }
        }
        viewModelScope.launch {
            preferencesManager.getParentalRating().collect { rating ->
                _uiState.value = _uiState.value.copy(parentalRating = rating)
            }
        }
        viewModelScope.launch {
            preferencesManager.getSubtitleLanguage().collect { lang ->
                _uiState.value = _uiState.value.copy(subtitleLanguage = lang)
            }
        }
        viewModelScope.launch {
            preferencesManager.getCurrentProfile().collect { profile ->
                _uiState.value = _uiState.value.copy(currentProfile = profile)
            }
        }
        loadProfiles()
    }

    private fun loadAnalytics() {
        viewModelScope.launch {
            val library = libraryDao.getAll()
            val watchlist = watchlistDao.getAll()
            val history = historyDao.getAll()
            val progressList = watchProgressDao.getAll()
            val watchedCount = progressList.count { it.percent >= 100 }
            val inProgressCount = progressList.count { it.percent in 1..99 }
            val libraryCount = library.size
            val watchlistCount = watchlist.size
            val historyCount = history.size
            val totalProgress = progressList.sumOf { it.percent.coerceIn(0, 100) }
            val avgCompletion = if (progressList.isNotEmpty()) (totalProgress / progressList.size) else 0
            _uiState.value = _uiState.value.copy(
                watchedCount = watchedCount,
                inProgressCount = inProgressCount,
                libraryCount = libraryCount,
                watchlistCount = watchlistCount,
                historyCount = historyCount,
                completionRate = avgCompletion
            )
        }
    }

    private fun loadProfiles() {
        viewModelScope.launch {
            val profiles = profileDao.getAll()
            _uiState.value = _uiState.value.copy(profiles = profiles)
        }
    }

    suspend fun toggleTheme() {
        val current = uiState.value.theme
        val newTheme = if (current == "dark") "light" else "dark"
        preferencesManager.setTheme(newTheme)
        _uiState.value = _uiState.value.copy(theme = newTheme)
    }

    suspend fun toggleHindiPriority() {
        val new = !uiState.value.hindiPriority
        preferencesManager.setHindiPriority(new)
        _uiState.value = _uiState.value.copy(hindiPriority = new)
    }

    suspend fun toggleAutoPlayNext() {
        val new = !uiState.value.autoPlayNext
        preferencesManager.setAutoPlayNext(new)
        _uiState.value = _uiState.value.copy(autoPlayNext = new)
    }

    suspend fun toggleParentalControl() {
        val new = !uiState.value.parentalControl
        preferencesManager.setParentalControl(new)
        _uiState.value = _uiState.value.copy(parentalControl = new)
    }

    suspend fun setParentalRating(rating: String) {
        preferencesManager.setParentalRating(rating)
        _uiState.value = _uiState.value.copy(parentalRating = rating)
    }

    suspend fun setSubtitleLanguage(language: String) {
        preferencesManager.setSubtitleLanguage(language)
        _uiState.value = _uiState.value.copy(subtitleLanguage = language)
    }

    suspend fun switchProfile(profileId: String) {
        preferencesManager.setCurrentProfile(profileId)
        _uiState.value = _uiState.value.copy(currentProfile = profileId)
    }

    suspend fun createProfile(name: String) {
        val id = name.lowercase().replace(" ", "_")
        val profile = Profile(id = id, name = name, avatar = "")
        profileDao.insert(profile)
        loadProfiles()
        switchProfile(id)
    }

    suspend fun deleteProfile(profileId: String) {
        if (profileId == uiState.value.currentProfile) return
        val profile = profileDao.getById(profileId) ?: return
        profileDao.delete(profile)
        loadProfiles()
    }

    suspend fun factoryReset() {
        libraryDao.deleteAll()
        watchlistDao.deleteAll()
        historyDao.deleteAll()
        watchProgressDao.deleteAll()
        watchedEpisodeDao.deleteAll()
        smartPlaylistDao.deleteAll()
        addonDao.deleteAll()
        profileDao.deleteAll()
        preferencesManager.clearAll()
        _uiState.value = ProfileUiState()
    }

    suspend fun exportFullBackup(): String {
        return try {
            val data = FullBackupData(
                addons = addonDao.getAll(),
                library = libraryDao.getAll(),
                watchlist = watchlistDao.getAll(),
                history = historyDao.getAll(),
                progress = watchProgressDao.getAll(),
                watchedEpisodes = watchedEpisodeDao.getAll(),
                playlists = smartPlaylistDao.getAll(),
                profiles = profileDao.getAll(),
                cachedMeta = cachedMetaDao.getAll(),
                settings = SettingsBackup(
                    theme = preferencesManager.getTheme().first(),
                    hindiPriority = preferencesManager.getHindiPriority().first(),
                    autoPlayNext = preferencesManager.getAutoPlayNext().first(),
                    parentalControl = preferencesManager.getParentalControl().first(),
                    parentalRating = preferencesManager.getParentalRating().first(),
                    subtitleLanguage = preferencesManager.getSubtitleLanguage().first(),
                    debridKey = preferencesManager.getDebridKey().first(),
                    debridProvider = preferencesManager.getDebridProvider().first()
                )
            )
            moshi.adapter(FullBackupData::class.java).toJson(data)
        } catch (e: Exception) {
            ""
        }
    }

    suspend fun importFullBackup(json: String): Boolean {
        return try {
            val data = moshi.adapter(FullBackupData::class.java).fromJson(json) ?: return false
            
            // Clear existing
            libraryDao.deleteAll()
            watchlistDao.deleteAll()
            historyDao.deleteAll()
            watchProgressDao.deleteAll()
            watchedEpisodeDao.deleteAll()
            smartPlaylistDao.deleteAll()
            addonDao.deleteAll()
            profileDao.deleteAll()
            cachedMetaDao.deleteAll()

            // Insert new
            libraryDao.insertAll(data.library)
            watchlistDao.insertAll(data.watchlist)
            historyDao.insertAll(data.history)
            watchProgressDao.insertAll(data.progress)
            watchedEpisodeDao.insertAll(data.watchedEpisodes)
            smartPlaylistDao.insertAll(data.playlists)
            addonDao.insertAll(data.addons)
            profileDao.insertAll(data.profiles)
            cachedMetaDao.insertAll(data.cachedMeta)

            // Settings
            preferencesManager.setTheme(data.settings.theme)
            preferencesManager.setHindiPriority(data.settings.hindiPriority)
            preferencesManager.setAutoPlayNext(data.settings.autoPlayNext)
            preferencesManager.setParentalControl(data.settings.parentalControl)
            preferencesManager.setParentalRating(data.settings.parentalRating)
            preferencesManager.setSubtitleLanguage(data.settings.subtitleLanguage)
            preferencesManager.setDebridKey(data.settings.debridKey)
            preferencesManager.setDebridProvider(data.settings.debridProvider)

            loadAnalytics()
            loadProfiles()
            true
        } catch (e: Exception) {
            false
        }
    }

    data class ProfileUiState(
        val theme: String = "dark",
        val hindiPriority: Boolean = true,
        val autoPlayNext: Boolean = false,
        val parentalControl: Boolean = false,
        val parentalRating: String = "PG-13",
        val subtitleLanguage: String = "English",
        val currentProfile: String = "default",
        val profiles: List<Profile> = emptyList(),
        val watchedCount: Int = 0,
        val inProgressCount: Int = 0,
        val libraryCount: Int = 0,
        val watchlistCount: Int = 0,
        val historyCount: Int = 0,
        val completionRate: Int = 0
    )
}

