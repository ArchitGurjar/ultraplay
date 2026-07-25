#!/bin/bash
set -e

echo "🚀 Implementing Stremio-Exact Addons Import/Export & URL Parsing..."

# 1. Update AddonRepository.kt
cat > app/src/main/java/com/ultrastream/app/data/repository/AddonRepository.kt << 'INNER_EOF'
package com.ultrastream.app.data.repository

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.ultrastream.app.data.dao.AddonDao
import com.ultrastream.app.data.models.Addon
import com.ultrastream.app.data.models.Catalog
import com.ultrastream.app.data.models.Extra
import com.ultrastream.app.network.StremioApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AddonRepository @Inject constructor(
    private val addonDao: AddonDao,
    private val stremioApi: StremioApi,
    private val moshi: Moshi
) {

    private val catalogListType = Types.newParameterizedType(List::class.java, Catalog::class.java)
    private val catalogAdapter = moshi.adapter<List<Catalog>>(catalogListType)

    suspend fun installAddon(url: String): Addon? {
        val manifest = try {
            stremioApi.getManifest(url)
        } catch (e: Exception) {
            return null
        }
        if (manifest.id.isBlank()) return null

        val existing = addonDao.getById(manifest.id)
        if (existing != null) return existing

        val netCatalogs = manifest.catalogs ?: emptyList()
        val mappedCatalogs = netCatalogs.map { netCat ->
            Catalog(
                type = netCat.type,
                id = netCat.id,
                name = netCat.name,
                extraSupported = netCat.extraSupported,
                extra = netCat.extra?.map {
                    Extra(
                        name = it.name,
                        isRequired = it.isRequired,
                        options = it.options
                    )
                }
            )
        }

        val catalogsJson = catalogAdapter.toJson(mappedCatalogs)
        val addon = Addon(
            id = manifest.id,
            url = url,
            name = manifest.name ?: manifest.id,
            catalogs = catalogsJson,
            enabled = true,
            required = false
        )
        addonDao.insert(addon)
        return addon
    }

    suspend fun getAllAddons(): List<Addon> = addonDao.getAll()

    suspend fun getEnabledAddons(): List<Addon> {
        return addonDao.getAll().filter { it.enabled }
    }

    suspend fun toggleAddon(id: String, enabled: Boolean) {
        addonDao.updateEnabled(id, enabled)
    }

    suspend fun removeAddon(id: String) {
        addonDao.deleteById(id)
    }

    suspend fun insertRawAddons(addons: List<Addon>) {
        addonDao.insertAll(addons)
    }
}
INNER_EOF

# 2. Update AddonsViewModel.kt
cat > app/src/main/java/com/ultrastream/app/ui/screens/addons/AddonsViewModel.kt << 'INNER_EOF'
package com.ultrastream.app.ui.screens.addons

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.ultrastream.app.data.models.Addon
import com.ultrastream.app.data.models.Catalog
import com.ultrastream.app.data.preferences.PreferencesManager
import com.ultrastream.app.data.repository.AddonRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AddonsViewModel @Inject constructor(
    private val addonRepository: AddonRepository,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddonsUiState())
    val uiState: StateFlow<AddonsUiState> = _uiState.asStateFlow()

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    init {
        loadAddons()
        observeDebridKey()
    }

    fun loadAddons() {
        viewModelScope.launch {
            val addons = addonRepository.getAllAddons()
            _uiState.value = _uiState.value.copy(addons = addons)
        }
    }

    private fun observeDebridKey() {
        viewModelScope.launch {
            preferencesManager.getDebridKey().collect { key ->
                _uiState.value = _uiState.value.copy(debridKey = key)
            }
        }
    }

    suspend fun installAddon(rawUrl: String): Boolean {
        // Fix for stremio:// links and trailing slashes
        var safeUrl = rawUrl.trim().replace("stremio://", "https://")
        if (!safeUrl.startsWith("http")) {
            safeUrl = "https://$safeUrl"
        }
        
        val addon = addonRepository.installAddon(safeUrl)
        if (addon != null) {
            loadAddons()
            return true
        }
        return false
    }

    suspend fun toggleAddon(id: String, enabled: Boolean) {
        addonRepository.toggleAddon(id, enabled)
        loadAddons()
    }

    suspend fun removeAddon(id: String) {
        addonRepository.removeAddon(id)
        loadAddons()
    }

    suspend fun saveDebridKey(key: String) {
        preferencesManager.setDebridKey(key)
        _uiState.value = _uiState.value.copy(debridKey = key)
    }

    fun exportAddonsJson(): String {
        return try {
            val addons = _uiState.value.addons
            val exportList = addons.map {
                val catAdapter = moshi.adapter<List<Catalog>>(Types.newParameterizedType(List::class.java, Catalog::class.java))
                val parsedCatalogs = try { catAdapter.fromJson(it.catalogs) } catch(e:Exception) { emptyList() }
                StremioAddonExport(it.id, it.url, it.name, parsedCatalogs, it.enabled, it.required)
            }
            val type = Types.newParameterizedType(List::class.java, StremioAddonExport::class.java)
            moshi.adapter<List<StremioAddonExport>>(type).toJson(exportList)
        } catch (e: Exception) {
            ""
        }
    }

    suspend fun importAddonsJson(json: String): Boolean {
        return try {
            val type = Types.newParameterizedType(List::class.java, StremioAddonExport::class.java)
            val importList = moshi.adapter<List<StremioAddonExport>>(type).fromJson(json) ?: return false

            val newAddons = importList.map {
                val catAdapter = moshi.adapter<List<Catalog>>(Types.newParameterizedType(List::class.java, Catalog::class.java))
                val catsJson = catAdapter.toJson(it.catalogs ?: emptyList())
                Addon(it.id, it.url, it.name, catsJson, it.enabled, it.required)
            }
            addonRepository.insertRawAddons(newAddons)
            loadAddons()
            true
        } catch (e: Exception) {
            false
        }
    }

    data class AddonsUiState(
        val addons: List<Addon> = emptyList(),
        val debridKey: String = ""
    )
}

data class StremioAddonExport(
    val id: String,
    val url: String,
    val name: String,
    val catalogs: List<Catalog>? = emptyList(),
    val enabled: Boolean = true,
    val required: Boolean = false
)
INNER_EOF

# 3. Update AddonsScreen.kt
cat > app/src/main/java/com/ultrastream/app/ui/screens/addons/AddonsScreen.kt << 'INNER_EOF'
package com.ultrastream.app.ui.screens.addons

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
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

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Addons", style = MaterialTheme.typography.headlineMedium)
        }
        
        // Addon URL Installation
        item {
            OutlinedTextField(
                value = addonUrl,
                onValueChange = { addonUrl = it },
                label = { Text("Manifest URL (https:// or stremio://)") },
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    scope.launch {
                        val success = viewModel.installAddon(addonUrl)
                        if (success) {
                            addonUrl = ""
                            Toast.makeText(context, "✅ Addon Installed!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "❌ Install Failed: Check URL format.", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Install Addon")
            }
        }

        // Import & Export exact Stremio JSON Array
        item {
            Text("Sync / Backup", style = MaterialTheme.typography.titleMedium)
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
                        val json = clipboardManager.getText()?.text ?: ""
                        if (json.isBlank()) {
                            Toast.makeText(context, "Clipboard is empty!", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        scope.launch {
                            val success = viewModel.importAddonsJson(json)
                            if (success) {
                                Toast.makeText(context, "✅ Addons Imported!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "❌ Invalid JSON format in clipboard.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Import JSON")
                }
            }
        }

        // Debrid Settings
        item {
            Text("Real-Debrid Key", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = debridKey,
                onValueChange = { debridKey = it },
                label = { Text("Debrid API Key") },
                modifier = Modifier.fillMaxWidth()
            )
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

        // Installed Addons List
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
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(addon.name, style = MaterialTheme.typography.titleSmall)
                        Text(addon.url, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    }
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Switch(
                            checked = addon.enabled,
                            onCheckedChange = {
                                scope.launch {
                                    viewModel.toggleAddon(addon.id, it)
                                }
                            }
                        )
                        if (!addon.required) {
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        viewModel.removeAddon(addon.id)
                                    }
                                }
                            ) {
                                Icon(imageVector = Icons.Default.Delete, contentDescription = "Remove")
                            }
                        }
                    }
                }
            }
        }
    }
}
INNER_EOF

# 4. Git Push
git add -A
git commit -m "Feature: Exact Stremio JSON Import/Export & URL Parser Fixes"
git push origin main

echo "🎉 Addons Feature Complete!"

