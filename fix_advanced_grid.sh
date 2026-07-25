#!/bin/bash
set -e

echo "🚀 Implementing Advanced LazyVerticalGrid for DetailsScreen..."

cat > app/src/main/java/com/ultrastream/app/ui/screens/details/DetailsScreen.kt << 'INNER_EOF'
package com.ultrastream.app.ui.screens.details

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ultrastream.app.data.models.StreamItem
import com.ultrastream.app.ui.components.bottomsheets.SeasonsSheet
import com.ultrastream.app.ui.components.bottomsheets.StreamsSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsScreen(
    id: String,
    type: String,
    viewModel: DetailsViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onPlay: (stream: StreamItem, title: String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var showSeasonsSheet by remember { mutableStateOf(false) }
    var showStreamsSheet by remember { mutableStateOf(false) }

    val meta = uiState.meta

    LaunchedEffect(id, type) {
        viewModel.loadMeta(id, type)
    }

    LaunchedEffect(meta) {
        if (meta?.type == "series" || meta?.type == "anime") {
            val seasons = meta.videos?.mapNotNull { it.season }?.distinct()?.sorted() ?: emptyList()
            if (seasons.isNotEmpty() && uiState.selectedSeason == null) {
                viewModel.selectSeason(seasons.first())
            }
        }
    }

    if (meta != null) {
        // 🚀 ADVANCED FIX: Using LazyVerticalGrid as the main root container!
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 80.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 1. Header Section (Full Width Span)
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Text(meta.name, style = MaterialTheme.typography.headlineMedium)
                    if (meta.year != null) {
                        Text(meta.year, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (meta.imdbRating != null) {
                        Text("⭐ ${meta.imdbRating}", style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(meta.description ?: "", style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { viewModel.toggleLibrary(meta) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (uiState.inLibrary) "Remove from Library" else "Add to Library")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { viewModel.toggleWatchlist(meta) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (uiState.inWatchlist) "Remove from Watchlist" else "Add to Watchlist")
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            // 2. Season Selector & Episodes Section
            if (meta.type == "series" || meta.type == "anime") {
                val seasons = meta.videos?.mapNotNull { it.season }?.distinct()?.sorted() ?: emptyList()
                val episodes = meta.videos
                    ?.filter { it.season == uiState.selectedSeason }
                    ?.sortedBy { it.episode } ?: emptyList()

                // Season Title & Button (Full Width Span)
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Season ${uiState.selectedSeason ?: ""}",
                            style = MaterialTheme.typography.titleLarge
                        )
                        if (seasons.isNotEmpty()) {
                            Button(onClick = { showSeasonsSheet = true }) {
                                Text("Change Season")
                            }
                        }
                    }
                }

                // Episode Cards (Grid Layout - Adaptive sizing)
                if (episodes.isNotEmpty()) {
                    items(episodes) { video ->
                        val epNum = video.episode ?: 0
                        val isSelected = epNum == uiState.selectedEpisode
                        Card(
                            modifier = Modifier.height(60.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant
                            ),
                            onClick = {
                                viewModel.selectEpisode(epNum)
                            }
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Text(
                                    text = "E$epNum",
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            // 3. Footer Section (Find Streams Button - Full Width Span)
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = {
                            viewModel.loadStreams(meta.id, meta.type, uiState.selectedSeason, uiState.selectedEpisode)
                            showStreamsSheet = true
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    ) {
                        Text(if (uiState.streamsLoading) "Loading Streams..." else "Find Streams")
                    }
                    Spacer(modifier = Modifier.height(32.dp)) // Extra padding for bottom navigation
                }
            }
        }
    } else if (uiState.isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else if (uiState.error != null) {
        Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            Text("Error: ${uiState.error}", color = MaterialTheme.colorScheme.error)
        }
    }

    // Bottom Sheets
    if (showSeasonsSheet) {
        val seasons = meta?.videos?.mapNotNull { it.season }?.distinct()?.sorted() ?: emptyList()
        SeasonsSheet(
            seasons = seasons,
            currentSeason = uiState.selectedSeason ?: 0,
            onDismiss = { showSeasonsSheet = false },
            onSeasonSelected = { season ->
                viewModel.selectSeason(season)
                showSeasonsSheet = false
            }
        )
    }

    if (showStreamsSheet && uiState.streams.isNotEmpty()) {
        StreamsSheet(
            streams = uiState.streams,
            onDismiss = { showStreamsSheet = false },
            onStreamClick = { stream ->
                showStreamsSheet = false
                viewModel.playStream(stream, meta?.name ?: "Stream") { resolvedStream, title ->
                    onPlay(resolvedStream, title)
                }
            }
        )
    }
}
INNER_EOF

echo "==> Staging and pushing changes to GitHub..."
git add app/src/main/java/com/ultrastream/app/ui/screens/details/DetailsScreen.kt
git commit -m "Fix: Implement advanced LazyVerticalGrid with GridItemSpan for DetailsScreen"
git push origin main

echo "🎉 Advanced UI implemented!"

