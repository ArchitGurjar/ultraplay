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
