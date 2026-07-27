package com.ultrastream.app.ui.screens.addons

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import com.ultrastream.app.data.models.RecommendedAddon
import com.ultrastream.app.ui.components.RecommendedAddonCard
import com.ultrastream.app.ui.components.HScrollRow
import com.ultrastream.app.ui.theme.premiumGlass
import java.io.File
import java.io.InputStreamReader

@OptIn(ExperimentalMaterial3Api::class)
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
    var selectedProvider by remember { mutableStateOf(uiState.debridProvider) }
    // ✅ Sync local state with UI state
    LaunchedEffect(uiState.debridProvider) {
        selectedProvider = uiState.debridProvider
    }

    // File picker launcher for import
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
                    val success = viewModel.importAddonsJson(json)
                    if (success) {
                        Toast.makeText(context, "✅ Addons Imported!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "❌ Invalid JSON format.", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to read file: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 100.dp, start = 16.dp, end = 16.dp, top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Extension Addons", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
        }

        // Addon URL Installation
        item {
            OutlinedTextField(
                value = addonUrl,
                onValueChange = { addonUrl = it },
                label = { Text("Manifest URL (https:// or stremio://)") },
                singleLine = true,
                maxLines = 1,
                modifier = Modifier.fillMaxWidth().height(64.dp),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                )
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = {
                    if (addonUrl.isBlank()) return@Button
                    scope.launch {
                        val success = viewModel.installAddon(addonUrl)
                        if (success) {
                            addonUrl = ""
                            Toast.makeText(context, "✅ Addon Installed!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "❌ Install Failed", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp).premiumGlass(RoundedCornerShape(28.dp)),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = Color.White),
                shape = RoundedCornerShape(28.dp)
            ) {
                Text("Install Custom Addon", fontWeight = FontWeight.Bold)
            }
        }

        // Recommended Addons
        item {
            Text("Recommended Extensions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            val recommended = listOf(
                RecommendedAddon("Torrentio", "Torrent scraper", "https://torrentio.strem.fun/manifest.json"),
                RecommendedAddon("Cinemeta", "Metadata provider", "https://cinemeta.strem.fun/manifest.json"),
                RecommendedAddon("Juan Carlos 2", "4K sources", "https://juan-carlos.strem.fun/manifest.json"),
                RecommendedAddon("Orion", "Premium scraper", "https://orion.strem.fun/manifest.json")
            )
            HScrollRow(modifier = Modifier.padding(vertical = 8.dp)) {
                recommended.forEach { addon ->
                    RecommendedAddonCard(
                        addon = addon,
                        onInstall = { url ->
                            scope.launch {
                                addonUrl = url
                                val success = viewModel.installAddon(url)
                                if (success) {
                                    addonUrl = ""
                                    Toast.makeText(context, "✅ Addon Installed!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "❌ Install Failed", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )
                }
            }
        }

        // Debrid Settings
        item {
            Text("Real-Debrid Key", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = debridKey,
                onValueChange = { debridKey = it },
                label = { Text("Enter Debrid API Key") },
                singleLine = true,
                maxLines = 1,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    scope.launch {
                        viewModel.saveDebridKey(debridKey)
                        Toast.makeText(context, "Saved!", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp).premiumGlass(RoundedCornerShape(28.dp)),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = Color.White),
                shape = RoundedCornerShape(28.dp)
            ) {
                Text("Sync Debrid Key", fontWeight = FontWeight.Bold)
            }
        }

        // Installed Addons List
        item {
            Text("Installed Extensions (${uiState.addons.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        items(uiState.addons.size) { index ->
            val addon = uiState.addons[index]
            Card(
                modifier = Modifier.fillMaxWidth().premiumGlass(RoundedCornerShape(20.dp)),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(addon.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.White)
                        Text(
                            text = addon.url,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = addon.enabled,
                            onCheckedChange = { scope.launch { viewModel.toggleAddon(addon.id, it) } }
                        )
                        if (!addon.required) {
                            IconButton(onClick = { scope.launch { viewModel.removeAddon(addon.id) } }) {
                                Icon(imageVector = Icons.Default.Delete, contentDescription = "Remove", tint = Color.Red.copy(alpha = 0.7f))
                            }
                        }
                    }
                }
            }
        }

        // ✅ NEW: Import/Export Addons
        item {
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        val json = viewModel.exportAddonsJson()
                        if (json.isNotBlank()) {
                            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(android.content.Intent.EXTRA_TEXT, json)
                            }
                            context.startActivity(android.content.Intent.createChooser(intent, "Export Addons"))
                            clipboardManager.setText(AnnotatedString(json))
                            Toast.makeText(context, "Copied to clipboard & shared", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.weight(1f).height(56.dp).premiumGlass(RoundedCornerShape(28.dp)),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = Color.White)
                ) {
                    Text("Export JSON")
                }
                Button(
                    onClick = {
                        importLauncher.launch(arrayOf("application/json", "text/plain"))
                    },
                    modifier = Modifier.weight(1f).height(56.dp).premiumGlass(RoundedCornerShape(28.dp)),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = Color.White)
                ) {
                    Text("Import JSON")
                }
            }
        }
    }
}

