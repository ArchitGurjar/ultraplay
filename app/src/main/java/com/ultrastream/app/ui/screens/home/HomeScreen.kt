package com.ultrastream.app.ui.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import android.widget.Toast
import com.ultrastream.app.data.models.MetaItem
import com.ultrastream.app.ui.components.*
import com.ultrastream.app.ui.theme.AccentBlue

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onItemClick: (id: String, type: String) -> Unit,
    onSeeAll: (rowId: String, items: List<MetaItem>, title: String) -> Unit,
    onAnalyticsClick: () -> Unit,
    onInstallAddon: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 100.dp)
    ) {
        // ✅ Error state – show full-screen error with retry
        if (uiState.error != null) {
            item {
                Box(
                    modifier = Modifier
                        .fillParentMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Error: ${uiState.error}",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.refresh() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Retry")
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Retry")
                        }
                    }
                }
            }
            return@LazyColumn
        }

        // Top Bar
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Bolt, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "UltraStream",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    // ✅ Refresh button
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color.White.copy(alpha = 0.7f))
                    }
                    IconButton(onClick = { Toast.makeText(context, "No new updates", Toast.LENGTH_SHORT).show() }) {
                        Icon(Icons.Rounded.Notifications, contentDescription = "Notifications", tint = Color.White.copy(alpha = 0.7f))
                    }
                    IconButton(onClick = onAnalyticsClick) {
                        Icon(Icons.Rounded.BarChart, contentDescription = "Analytics", tint = Color.White.copy(alpha = 0.7f))
                    }
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(AccentBlue),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("A", color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp)
                    }
                }
            }
        }

        // Continue Watching
        item {
            SectionHeader(title = "Continue Watching")
            if (uiState.continueWatching.isEmpty()) {
                Text(
                    "Start watching to see history",
                    modifier = Modifier.padding(horizontal = 24.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            } else {
                HScrollRow {
                    uiState.continueWatching.forEach { (history, progress) ->
                        ContinueWatchingCard(
                            history = history,
                            progressPercent = progress,
                            onClick = { onItemClick(history.id, history.type) }
                        )
                    }
                }
            }
        }

        // Recommendations
        if (uiState.recommendations.isNotEmpty()) {
            item {
                SectionHeader(title = "🎯 Because you watched")
                HScrollRow {
                    uiState.recommendations.forEach { meta ->
                        PosterCard(
                            meta = meta,
                            onClick = { onItemClick(meta.id, meta.type) },
                            showProgress = true,
                            progressPercent = uiState.progressMap[meta.id] ?: 0
                        )
                    }
                }
            }
        }

        // Recommended Addons
        item {
            SectionHeader(title = "Recommended Addons")
            if (uiState.recommendedAddons.isEmpty()) {
                Text(
                    "No addons recommended",
                    modifier = Modifier.padding(horizontal = 24.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            } else {
                HScrollRow {
                    uiState.recommendedAddons.forEach { addon ->
                        RecommendedAddonCard(
                            addon = addon,
                            onInstall = onInstallAddon
                        )
                    }
                }
            }
        }

        // Catalog rows with skeleton loading
        if (uiState.isLoading && uiState.catalogRows.isEmpty()) {
            // Show skeleton rows
            items(5) {
                CatalogRowShimmer()
            }
        } else {
            items(uiState.catalogRows.size, key = { index -> uiState.catalogRows[index].first }) { index ->
                val (rowId, items) = uiState.catalogRows[index]
                val parts = rowId.split("_")
                val displayName = when {
                    parts.size >= 3 -> {
                        val type = parts[1]
                        val name = parts.drop(2).joinToString(" ")
                        "$type $name".replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                    }
                    else -> "Discovery"
                }
                SectionHeader(
                    title = displayName,
                    actionText = "See All",
                    onActionClick = { onSeeAll(rowId, items, displayName) }
                )
                if (items.isEmpty()) {
                    Text(
                        "No content available",
                        modifier = Modifier.padding(horizontal = 24.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                } else {
                    HScrollRow {
                        items.forEach { meta ->
                            PosterCard(
                                meta = meta,
                                onClick = { onItemClick(meta.id, meta.type) },
                                showProgress = true,
                                progressPercent = uiState.progressMap[meta.id] ?: 0
                            )
                        }
                    }
                }
            }
        }
    }
}