#!/bin/bash
# add_backup_restore.sh – Adds full data backup/restore to ultraplay

cd ~/projects/ultraplay || exit

# ============================================================
# 1. Create a new use case for exporting/importing data
# ============================================================
mkdir -p app/src/main/java/com/ultrastream/app/domain/usecase

cat > app/src/main/java/com/ultrastream/app/domain/usecase/BackupRestoreUseCase.kt <<'EOF'
package com.ultrastream.app.domain.usecase

import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.ultrastream.app.data.dao.*
import com.ultrastream.app.data.models.*
import com.ultrastream.app.data.preferences.PreferencesManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupRestoreUseCase @Inject constructor(
    private val context: Context,
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
EOF

# ============================================================
# 2. Update ProfileViewModel to include backup/restore
# ============================================================
# We'll patch the existing file by appending new functions and adding the use case.
# Since we can't easily patch with sed, we'll replace the whole file (but we'll keep the existing logic).
# We'll read the existing file, then write a new version that includes the new imports and functions.
# For safety, we backup first.

cp app/src/main/java/com/ultrastream/app/ui/screens/profile/ProfileViewModel.kt{,.bak}

cat > app/src/main/java/com/ultrastream/app/ui/screens/profile/ProfileViewModel.kt <<'EOF'
package com.ultrastream.app.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ultrastream.app.data.dao.*
import com.ultrastream.app.data.models.Profile
import com.ultrastream.app.data.preferences.PreferencesManager
import com.ultrastream.app.domain.usecase.BackupRestoreUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager,
    private val libraryDao: LibraryDao,
    private val watchlistDao: WatchlistDao,
    private val historyDao: HistoryDao,
    private val watchProgressDao: WatchProgressDao,
    private val addonDao: AddonDao,
    private val profileDao: ProfileDao,
    private val backupRestoreUseCase: BackupRestoreUseCase   // ✅ added
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
        addonDao.deleteAll()
        profileDao.deleteAll()
        preferencesManager.clearAll()
        _uiState.value = ProfileUiState()
    }

    // ==================== BACKUP / RESTORE ====================

    suspend fun exportBackup(): String? {
        return try {
            backupRestoreUseCase.exportData()
        } catch (e: Exception) {
            null
        }
    }

    suspend fun importBackup(json: String): Boolean {
        return try {
            backupRestoreUseCase.importData(json)
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
EOF

# ============================================================
# 3. Update ProfileScreen to add Backup/Restore buttons
# ============================================================
# We'll patch the existing file by adding new items in the LazyColumn.
# Since the file is long, we'll use a safer approach: replace the whole file with a version that includes the new buttons.
# We'll backup first.

cp app/src/main/java/com/ultrastream/app/ui/screens/profile/ProfileScreen.kt{,.bak}

cat > app/src/main/java/com/ultrastream/app/ui/screens/profile/ProfileScreen.kt <<'EOF'
package com.ultrastream.app.ui.screens.profile

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ultrastream.app.ui.components.AnalyticsCard
import kotlinx.coroutines.launch
import java.io.InputStreamReader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var expandedRating by remember { mutableStateOf(false) }
    var expandedLanguage by remember { mutableStateOf(false) }
    var showNewProfileDialog by remember { mutableStateOf(false) }
    var newProfileName by remember { mutableStateOf("") }
    
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    // File picker for import backup
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val reader = InputStreamReader(inputStream)
                val json = reader.readText()
                reader.close()
                inputStream?.close()
                scope.launch {
                    val success = viewModel.importBackup(json)
                    if (success) {
                        Toast.makeText(context, "✅ Data restored successfully!", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "❌ Invalid backup file.", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to read file: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Settings", style = MaterialTheme.typography.headlineMedium)
        }

        // Analytics Dashboard
        item {
            Text("Analytics", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AnalyticsCard(label = "Watched", value = uiState.watchedCount.toString(), modifier = Modifier.weight(1f))
                AnalyticsCard(label = "In Progress", value = uiState.inProgressCount.toString(), modifier = Modifier.weight(1f))
                AnalyticsCard(label = "Library", value = uiState.libraryCount.toString(), modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AnalyticsCard(label = "Watchlist", value = uiState.watchlistCount.toString(), modifier = Modifier.weight(1f))
                AnalyticsCard(label = "History", value = uiState.historyCount.toString(), modifier = Modifier.weight(1f))
                AnalyticsCard(label = "Completion", value = uiState.completionRate.toString() + "%", modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Theme toggle
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Dark Theme")
                Switch(
                    checked = uiState.theme == "dark",
                    onCheckedChange = { scope.launch { viewModel.toggleTheme() } }
                )
            }
        }

        // Hindi Priority
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Hindi Priority")
                Switch(
                    checked = uiState.hindiPriority,
                    onCheckedChange = { scope.launch { viewModel.toggleHindiPriority() } }
                )
            }
        }

        // Auto-play Next
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Auto-play Next")
                Switch(
                    checked = uiState.autoPlayNext,
                    onCheckedChange = { scope.launch { viewModel.toggleAutoPlayNext() } }
                )
            }
        }

        // Parental Control toggle
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Parental Control")
                Switch(
                    checked = uiState.parentalControl,
                    onCheckedChange = { scope.launch { viewModel.toggleParentalControl() } }
                )
            }
        }

        // Parental Rating dropdown
        item {
            Text("Parental Rating", style = MaterialTheme.typography.titleMedium)
            ExposedDropdownMenuBox(
                expanded = expandedRating,
                onExpandedChange = { expandedRating = !expandedRating }
            ) {
                OutlinedTextField(
                    value = uiState.parentalRating,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Rating") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedRating) },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = expandedRating,
                    onDismissRequest = { expandedRating = false }
                ) {
                    listOf("G", "PG", "PG-13", "R", "NC-17").forEach { rating ->
                        DropdownMenuItem(
                            text = { Text(rating) },
                            onClick = {
                                expandedRating = false
                                scope.launch { viewModel.setParentalRating(rating) }
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Subtitle Language dropdown
        item {
            Text("Preferred Subtitle Language", style = MaterialTheme.typography.titleMedium)
            ExposedDropdownMenuBox(
                expanded = expandedLanguage,
                onExpandedChange = { expandedLanguage = !expandedLanguage }
            ) {
                OutlinedTextField(
                    value = uiState.subtitleLanguage,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Language") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedLanguage) },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = expandedLanguage,
                    onDismissRequest = { expandedLanguage = false }
                ) {
                    listOf("English", "Hindi", "Spanish", "French", "German", "Tamil", "Telugu", "Malayalam").forEach { lang ->
                        DropdownMenuItem(
                            text = { Text(lang) },
                            onClick = {
                                expandedLanguage = false
                                scope.launch { viewModel.setSubtitleLanguage(lang) }
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Profile switching
        item {
            Text("Profiles", style = MaterialTheme.typography.titleMedium)
            if (uiState.profiles.isEmpty()) {
                Text("No profiles found. Create one.", style = MaterialTheme.typography.bodySmall)
            } else {
                uiState.profiles.forEach { profile ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(profile.name, style = MaterialTheme.typography.bodyLarge)
                        Row {
                            if (profile.id == uiState.currentProfile) {
                                Text("(Active)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            } else {
                                Button(
                                    onClick = { scope.launch { viewModel.switchProfile(profile.id) } },
                                    modifier = Modifier.width(80.dp)
                                ) {
                                    Text("Switch")
                                }
                                IconButton(
                                    onClick = { scope.launch { viewModel.deleteProfile(profile.id) } }
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { showNewProfileDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Create New Profile")
            }
        }

        // ==================== BACKUP / RESTORE ====================
        item {
            Text("Data Backup & Restore", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Export Backup
                Button(
                    onClick = {
                        scope.launch {
                            val json = viewModel.exportBackup()
                            if (!json.isNullOrBlank()) {
                                clipboard.setText(AnnotatedString(json))
                                Toast.makeText(context, "✅ Backup JSON copied to clipboard!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "❌ Failed to generate backup.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Copy Backup")
                }
                // Import Backup from file
                Button(
                    onClick = { importLauncher.launch(arrayOf("application/json", "text/plain")) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Restore from File")
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedButton(
                onClick = {
                    scope.launch {
                        val json = viewModel.exportBackup()
                        if (!json.isNullOrBlank()) {
                            // Share via intent
                            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "application/json"
                                putExtra(android.content.Intent.EXTRA_TEXT, json)
                            }
                            context.startActivity(android.content.Intent.createChooser(intent, "Share Backup"))
                        } else {
                            Toast.makeText(context, "❌ Failed to generate backup.", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Share Backup (JSON)")
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Factory Reset
        item {
            Button(
                onClick = {
                    scope.launch {
                        viewModel.factoryReset()
                        Toast.makeText(context, "Factory reset performed", Toast.LENGTH_LONG).show()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Factory Reset")
            }
        }
    }

    // New Profile Dialog
    if (showNewProfileDialog) {
        AlertDialog(
            onDismissRequest = { showNewProfileDialog = false },
            title = { Text("Create Profile") },
            text = {
                OutlinedTextField(
                    value = newProfileName,
                    onValueChange = { newProfileName = it },
                    label = { Text("Profile Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newProfileName.isNotBlank()) {
                            scope.launch {
                                viewModel.createProfile(newProfileName)
                                newProfileName = ""
                                showNewProfileDialog = false
                            }
                        }
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewProfileDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
EOF

# ============================================================
# 4. Stage and commit changes (optional)
# ============================================================
git add app/src/main/java/com/ultrastream/app/domain/usecase/BackupRestoreUseCase.kt
git add app/src/main/java/com/ultrastream/app/ui/screens/profile/ProfileViewModel.kt
git add app/src/main/java/com/ultrastream/app/ui/screens/profile/ProfileScreen.kt

git commit -m "Add full data backup/restore feature (Export/Import all user data)"

echo "✅ Backup/Restore feature added successfully!"
echo "📌 Now you can copy backup JSON or restore from file in Profile screen."
echo "🚀 Don't forget to test the feature."
