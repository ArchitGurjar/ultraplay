package com.ultrastream.app.ui.screens.profile

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ultrastream.app.ui.components.AnalyticsCard
import com.ultrastream.app.ui.theme.premiumGlass
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var expandedRating by remember { mutableStateOf(false) }
    var showNewProfileDialog by remember { mutableStateOf(false) }
    var newProfileName by remember { mutableStateOf("") }
    
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val json = viewModel.exportFullBackup()
                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        outputStream.write(json.toByteArray())
                    }
                    Toast.makeText(context, "✅ Backup exported!", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "❌ Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val json = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
                    val success = viewModel.importFullBackup(json)
                    if (success) {
                        Toast.makeText(context, "✅ Data restored! Please restart the app.", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "❌ Restore failed: invalid file", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "❌ Restore failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 100.dp, start = 16.dp, end = 16.dp, top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("App Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
        }

        // Analytics Dashboard
        item {
            Text("Viewing Statistics", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AnalyticsCard(label = "WATCHED", value = uiState.watchedCount.toString(), modifier = Modifier.weight(1f))
                AnalyticsCard(label = "PROGRESS", value = uiState.inProgressCount.toString(), modifier = Modifier.weight(1f))
                AnalyticsCard(label = "LIBRARY", value = uiState.libraryCount.toString(), modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AnalyticsCard(label = "WATCHLIST", value = uiState.watchlistCount.toString(), modifier = Modifier.weight(1f))
                AnalyticsCard(label = "HISTORY", value = uiState.historyCount.toString(), modifier = Modifier.weight(1f))
                AnalyticsCard(label = "COMPLETION", value = "${uiState.completionRate}%", modifier = Modifier.weight(1f))
            }
        }

        // Settings items with glassy look
        item {
            Card(
                modifier = Modifier.fillMaxWidth().premiumGlass(RoundedCornerShape(20.dp)),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SettingToggle("Dark Experience", uiState.theme == "dark") { scope.launch { viewModel.toggleTheme() } }
                    SettingToggle("Hindi Priority", uiState.hindiPriority) { scope.launch { viewModel.toggleHindiPriority() } }
                    SettingToggle("Auto-play Next", uiState.autoPlayNext) { scope.launch { viewModel.toggleAutoPlayNext() } }
                    SettingToggle("Parental Lock", uiState.parentalControl) { scope.launch { viewModel.toggleParentalControl() } }
                }
            }
        }

        // Parental Rating dropdown
        item {
            Text("Content Restrictions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            ExposedDropdownMenuBox(
                expanded = expandedRating,
                onExpandedChange = { expandedRating = !expandedRating }
            ) {
                OutlinedTextField(
                    value = uiState.parentalRating,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Parental Rating") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedRating) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                    )
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
        }

        // Profile switching
        item {
            Text("User Profiles", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Card(
                modifier = Modifier.fillMaxWidth().premiumGlass(RoundedCornerShape(24.dp)),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    uiState.profiles.forEach { profile ->
                        Row(
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(profile.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = Color.White)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (profile.id == uiState.currentProfile) {
                                    Text("ACTIVE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black)
                                } else {
                                    TextButton(onClick = { scope.launch { viewModel.switchProfile(profile.id) } }) {
                                        Text("SWITCH", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                    }
                                    IconButton(onClick = { scope.launch { viewModel.deleteProfile(profile.id) } }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                        }
                    }
                    Button(
                        onClick = { showNewProfileDialog = true },
                        modifier = Modifier.fillMaxWidth().height(48.dp).premiumGlass(RoundedCornerShape(24.dp)),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = Color.White),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Text("Add New Profile", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Data Management (Backup/Restore)
        item {
            Text("Data Management", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { exportLauncher.launch("ultrastream_backup.json") },
                    modifier = Modifier.weight(1f).height(56.dp).premiumGlass(RoundedCornerShape(28.dp)),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = Color.White)
                ) {
                    Icon(Icons.Default.FileDownload, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Backup")
                }
                Button(
                    onClick = { importLauncher.launch(arrayOf("application/json")) },
                    modifier = Modifier.weight(1f).height(56.dp).premiumGlass(RoundedCornerShape(28.dp)),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = Color.White)
                ) {
                    Icon(Icons.Default.FileUpload, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Restore")
                }
            }
        }

        // Factory Reset
        item {
            Button(
                onClick = {
                    scope.launch {
                        viewModel.factoryReset()
                        Toast.makeText(context, "All data cleared", Toast.LENGTH_LONG).show()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp).premiumGlass(RoundedCornerShape(28.dp), backgroundColor = Color.Red.copy(alpha = 0.1f)),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = Color.Red),
                shape = RoundedCornerShape(28.dp)
            ) {
                Text("Erase All Data", fontWeight = FontWeight.Black)
            }
        }
    }

    // New Profile Dialog
    if (showNewProfileDialog) {
        AlertDialog(
            onDismissRequest = { showNewProfileDialog = false },
            containerColor = Color(0xFF1A1A1A),
            title = { Text("Create Profile", color = Color.White) },
            text = {
                OutlinedTextField(
                    value = newProfileName,
                    onValueChange = { newProfileName = it },
                    label = { Text("Profile Name") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
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
                    Text("CREATE", fontWeight = FontWeight.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewProfileDialog = false }) {
                    Text("CANCEL", color = Color.White.copy(alpha = 0.6f))
                }
            }
        )
    }
}

@Composable
fun SettingToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color.White, fontWeight = FontWeight.Medium)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
