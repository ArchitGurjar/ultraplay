package com.ultrastream.app.ui.screens.addons

import android.widget.Toast
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch

@Composable
fun AddonsScreen(
    viewModel: AddonsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    
    var addonUrl by remember { mutableStateOf("") }
    var debridKey by remember { mutableStateOf(uiState.debridKey) }
    var jsonInputText by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Addons", style = MaterialTheme.typography.headlineMedium)
        }
        
        // 1. FIXED: Addon URL Installation Box (Strictly Single Line)
        item {
            OutlinedTextField(
                value = addonUrl,
                onValueChange = { addonUrl = it },
                label = { Text("Manifest URL (https:// or stremio://)") },
                singleLine = true,
                maxLines = 1,
                modifier = Modifier.fillMaxWidth().height(64.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    if (addonUrl.isBlank()) return@Button
                    scope.launch {
                        val success = viewModel.installAddon(addonUrl)
                        if (success) {
                            addonUrl = ""
                            Toast.makeText(context, "✅ Addon Installed!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "❌ Install Failed: Server blocked or invalid format.", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Install Addon")
            }
        }

        // 2. Import & Export JSON Array
        item {
            Text("Sync / Backup", style = MaterialTheme.typography.titleMedium)
            
            OutlinedTextField(
                value = jsonInputText,
                onValueChange = { jsonInputText = it },
                label = { Text("Paste Import JSON here") },
                modifier = Modifier.fillMaxWidth().height(120.dp),
                minLines = 3,
                maxLines = 5
            )
            
            Spacer(modifier = Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        val json = viewModel.exportAddonsJson()
                        if (json.isNotBlank()) {
                            clipboardManager.setText(AnnotatedString(json))
                            Toast.makeText(context, "✅ JSON Copied to Clipboard!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Export JSON")
                }
                Button(
                    onClick = {
                        if (jsonInputText.isBlank()) {
                            Toast.makeText(context, "Paste JSON code first!", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        scope.launch {
                            val success = viewModel.importAddonsJson(jsonInputText)
                            if (success) {
                                jsonInputText = ""
                                Toast.makeText(context, "✅ Addons Imported!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "❌ Invalid JSON format.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Import JSON")
                }
            }
        }

        // 3. Debrid Settings
        item {
            Text("Real-Debrid Key", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = debridKey,
                onValueChange = { debridKey = it },
                label = { Text("Debrid API Key") },
                singleLine = true,
                maxLines = 1,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    scope.launch {
                        viewModel.saveDebridKey(debridKey)
                        Toast.makeText(context, "Saved!", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Debrid Key")
            }
        }

        // 4. Installed Addons List
        item {
            Text("Installed Addons (${uiState.addons.size})", style = MaterialTheme.typography.titleMedium)
        }
        items(uiState.addons.size) { index ->
            val addon = uiState.addons[index]
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(addon.name, style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = addon.url, 
                            style = MaterialTheme.typography.bodySmall, 
                            maxLines = 1, 
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = addon.enabled,
                            onCheckedChange = { scope.launch { viewModel.toggleAddon(addon.id, it) } }
                        )
                        if (!addon.required) {
                            IconButton(onClick = { scope.launch { viewModel.removeAddon(addon.id) } }) {
                                Icon(imageVector = Icons.Default.Delete, contentDescription = "Remove")
                            }
                        }
                    }
                }
            }
        }
    }
}

