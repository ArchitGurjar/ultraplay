package com.ultrastream.app.ui.screens.profile

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.ultrastream.app.data.dao.*
import com.ultrastream.app.data.preferences.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager,
    private val libraryDao: LibraryDao,
    private val watchlistDao: WatchlistDao,
    private val historyDao: HistoryDao,
    private val watchProgressDao: WatchProgressDao,
    private val addonDao: AddonDao,
    private val profileDao: ProfileDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
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
            preferencesManager.getCurrentProfile().collect { profile ->
                _uiState.value = _uiState.value.copy(currentProfile = profile)
            }
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

    suspend fun exportData(context: Context): Boolean {
        return try {
            val data = buildExportData()
            val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
            val json = moshi.adapter(ExportData::class.java).toJson(data)
            val fileName = "ultrastream_backup_${System.currentTimeMillis()}.json"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Documents/UltraStream")
                }
                val uri = resolver.insert(MediaStore.Files.getContentUri("external"), contentValues)
                uri?.let {
                    resolver.openOutputStream(it)?.use { outputStream ->
                        outputStream.write(json.toByteArray())
                    }
                    true
                } ?: false
            } else {
                val file = File(context.getExternalFilesDir("backups"), fileName)
                file.parentFile?.mkdirs()
                file.writeText(json)
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    suspend fun importData(context: Context, uri: Uri): Boolean {
        return try {
            val inputStream: InputStream = context.contentResolver.openInputStream(uri) ?: return false
            val json = inputStream.bufferedReader().use { it.readText() }
            val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
            val data = moshi.adapter(ExportData::class.java).fromJson(json) ?: return false
            libraryDao.deleteAll()
            watchlistDao.deleteAll()
            historyDao.deleteAll()
            watchProgressDao.deleteAll()
            addonDao.deleteAll()
            profileDao.deleteAll()
            libraryDao.insertAll(data.library)
            watchlistDao.insertAll(data.watchlist)
            historyDao.insertAll(data.history)
            watchProgressDao.insertAll(data.watchProgress)
            addonDao.insertAll(data.addons)
            profileDao.insertAll(data.profiles)
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun factoryReset(context: Context) {
        libraryDao.deleteAll()
        watchlistDao.deleteAll()
        historyDao.deleteAll()
        watchProgressDao.deleteAll()
        addonDao.deleteAll()
        profileDao.deleteAll()
        preferencesManager.clearAll()
        _uiState.value = ProfileUiState()
    }

    private suspend fun buildExportData(): ExportData {
        return ExportData(
            library = libraryDao.getAll(),
            watchlist = watchlistDao.getAll(),
            history = historyDao.getAll(),
            watchProgress = watchProgressDao.getAll(),
            addons = addonDao.getAll(),
            profiles = profileDao.getAll()
        )
    }

    data class ProfileUiState(
        val theme: String = "dark",
        val hindiPriority: Boolean = true,
        val autoPlayNext: Boolean = false,
        val parentalControl: Boolean = false,
        val currentProfile: String = "default"
    )

    data class ExportData(
        val library: List<com.ultrastream.app.data.models.LibraryItem>,
        val watchlist: List<com.ultrastream.app.data.models.WatchlistItem>,
        val history: List<com.ultrastream.app.data.models.HistoryItem>,
        val watchProgress: List<com.ultrastream.app.data.models.WatchProgress>,
        val addons: List<com.ultrastream.app.data.models.Addon>,
        val profiles: List<com.ultrastream.app.data.models.Profile>
    )
}

