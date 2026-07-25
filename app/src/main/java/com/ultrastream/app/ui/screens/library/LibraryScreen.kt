package com.ultrastream.app.ui.screens.library

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
import com.ultrastream.app.ui.components.GridSection
import com.ultrastream.app.ui.components.SectionHeader
import com.ultrastream.app.data.models.MetaItem

@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel = hiltViewModel(),
    onItemClick: (id: String, type: String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        if (uiState.isLoading) {
            item {
                Box(modifier = Modifier.fillParentMaxWidth(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        } else {
            // Library
            item {
                SectionHeader(title = "Library")
                if (uiState.library.isEmpty()) {
                    Text("No saved items", modifier = Modifier.padding(horizontal = 16.dp))
                } else {
                    val metaItems = uiState.library.map { lib ->
                        MetaItem(
                            id = lib.id,
                            type = lib.type,
                            name = lib.name,
                            poster = lib.poster,
                            background = lib.background,
                            imdbRating = lib.imdbRating,
                            year = lib.year,
                            releaseInfo = lib.releaseInfo,
                            released = lib.released,
                            description = lib.description,
                            genre = lib.genre?.split(","),
                            runtime = lib.runtime,
                            cast = lib.cast?.split(","),
                            imdbId = lib.imdbId,
                            videos = null
                        )
                    }
                    GridSection(items = metaItems, onItemClick = onItemClick)
                }
            }

            // Watchlist
            item {
                SectionHeader(title = "Watchlist")
                if (uiState.watchlist.isEmpty()) {
                    Text("No watchlist items", modifier = Modifier.padding(horizontal = 16.dp))
                } else {
                    val metaItems = uiState.watchlist.map { wl ->
                        MetaItem(
                            id = wl.id,
                            type = wl.type,
                            name = wl.name,
                            poster = wl.poster,
                            background = wl.background,
                            imdbRating = wl.imdbRating,
                            year = wl.year,
                            releaseInfo = wl.releaseInfo,
                            released = wl.released,
                            description = wl.description,
                            genre = wl.genre?.split(","),
                            runtime = wl.runtime,
                            cast = wl.cast?.split(","),
                            imdbId = wl.imdbId,
                            videos = null
                        )
                    }
                    GridSection(items = metaItems, onItemClick = onItemClick)
                }
            }

            // History
            item {
                SectionHeader(title = "History")
                if (uiState.history.isEmpty()) {
                    Text("No history", modifier = Modifier.padding(horizontal = 16.dp))
                } else {
                    val metaItems = uiState.history.map { hist ->
                        MetaItem(
                            id = hist.id,
                            type = "movie", // default
                            name = hist.name,
                            poster = hist.poster,
                            background = null,
                            imdbRating = null,
                            year = null,
                            releaseInfo = null,
                            released = null,
                            description = null,
                            genre = null,
                            runtime = null,
                            cast = null,
                            imdbId = null,
                            videos = null
                        )
                    }
                    GridSection(items = metaItems, onItemClick = onItemClick)
                }
            }
        }
    }
}

