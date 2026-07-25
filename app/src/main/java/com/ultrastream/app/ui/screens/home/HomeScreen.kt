package com.ultrastream.app.ui.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ultrastream.app.data.models.MetaItem
import com.ultrastream.app.ui.components.ContinueWatchingCard
import com.ultrastream.app.ui.components.HScrollRow
import com.ultrastream.app.ui.components.PosterCard
import com.ultrastream.app.ui.components.RecommendedAddonCard
import com.ultrastream.app.ui.components.SectionHeader
import com.ultrastream.app.ui.components.SkeletonPosterCard

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onItemClick: (id: String, type: String) -> Unit,
    onSeeAll: (rowId: String, items: List<MetaItem>, title: String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        // Continue Watching with skeleton
        item(key = "continue_watching") {
            SectionHeader(title = "Continue Watching")
            if (uiState.isLoading) {
                HScrollRow {
                    repeat(3) { SkeletonContinueWatchingCard() }
                }
            } else if (uiState.continueWatching.isEmpty()) {
                Text("No history yet", modifier = Modifier.padding(horizontal = 16.dp))
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

        // Recommendations (Because you watched)
        if (uiState.recommendations.isNotEmpty()) {
            item(key = "recommendations") {
                SectionHeader(title = "🎯 Because you watched")
                HScrollRow {
                    uiState.recommendations.forEach { meta ->
                        PosterCard(
                            meta = meta,
                            onClick = { onItemClick(meta.id, meta.type) }
                        )
                    }
                }
            }
        }

        // Recommended Addons
        item(key = "recommended_addons") {
            SectionHeader(title = "Recommended Addons")
            if (uiState.isLoading) {
                HScrollRow {
                    repeat(2) { SkeletonRecommendedAddonCard() }
                }
            } else if (uiState.recommendedAddons.isEmpty()) {
                Text("No recommendations", modifier = Modifier.padding(horizontal = 16.dp))
            } else {
                HScrollRow {
                    uiState.recommendedAddons.forEach { addon ->
                        RecommendedAddonCard(
                            addon = addon,
                            onInstall = { url ->
                                // TODO: trigger install via ViewModel
                            }
                        )
                    }
                }
            }
        }

        // Catalog rows with See All and skeletons
        if (uiState.isLoading) {
            // Show 3 skeleton catalog rows
            repeat(3) {
                item {
                    SectionHeader(title = "Loading...")
                    HScrollRow {
                        repeat(4) { SkeletonPosterCard() }
                    }
                }
            }
        } else {
            uiState.catalogRows.forEach { (rowId, items) ->
                item {
                    val parts = rowId.split("_")
                    val displayName = when {
                        parts.size >= 3 -> {
                            val type = parts[1]
                            val name = parts.drop(2).joinToString(" ")
                            "$type $name".replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                        }
                        else -> "Catalog"
                    }
                    SectionHeader(
                        title = displayName,
                        actionText = "See All",
                        onActionClick = { onSeeAll(rowId, items, displayName) }
                    )
                    if (items.isEmpty()) {
                        Text("No items", modifier = Modifier.padding(horizontal = 16.dp))
                    } else {
                        HScrollRow {
                            items.forEach { meta ->
                                PosterCard(
                                    meta = meta,
                                    onClick = { onItemClick(meta.id, meta.type) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // If error, show retry
        if (uiState.error != null) {
            item {
                Box(
                    modifier = Modifier.fillParentMaxWidth().padding(16.dp),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                        Text("Error: ${uiState.error}", color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { viewModel.refresh() }) {
                            Text("Retry")
                        }
                    }
                }
            }
        }
    }
}
