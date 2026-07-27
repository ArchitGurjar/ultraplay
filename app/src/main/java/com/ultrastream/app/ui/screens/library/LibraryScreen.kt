package com.ultrastream.app.ui.screens.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ultrastream.app.data.models.MetaItem
import com.ultrastream.app.data.models.PlaylistEpisode
import com.ultrastream.app.data.models.SmartPlaylist
import com.ultrastream.app.data.models.StreamItem
import com.ultrastream.app.ui.components.GridSection
import com.ultrastream.app.ui.components.HScrollRow
import com.ultrastream.app.ui.components.SectionHeader
import com.ultrastream.app.ui.components.SmartPlaylistCard
import com.ultrastream.app.ui.components.bottomsheets.SmartPlaylistDetailSheet
import kotlinx.coroutines.launch

@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel = hiltViewModel(),
    onItemClick: (id: String, type: String) -> Unit,
    onPlayStream: (List<StreamItem>, String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedPlaylist by remember { mutableStateOf<SmartPlaylist?>(null) }
    var showPlaylistDetail by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Observe playStream and playAllStreams to navigate to player
    LaunchedEffect(uiState.playStream, uiState.playAllStreams) {
        uiState.playStream?.let { (stream, title) ->
            onPlayStream(listOf(stream), title)
            viewModel.clearPlayStream()
        }
        uiState.playAllStreams?.let { (streams, title) ->
            onPlayStream(streams, title)
            viewModel.clearPlayAllStreams()
        }
    }

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
            // Smart Playlists
            item {
                SectionHeader(title = "Smart Playlists")
                if (uiState.smartPlaylists.isEmpty()) {
                    Text(
                        "No smart playlists created",
                        modifier = Modifier.padding(horizontal = 24.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                } else {
                    HScrollRow {
                        uiState.smartPlaylists.forEach { playlist ->
                            SmartPlaylistCard(
                                playlist = playlist,
                                onClick = {
                                    selectedPlaylist = playlist
                                    showPlaylistDetail = true
                                },
                                onExportM3u = { pl ->
                                    scope.launch { viewModel.exportPlaylistM3U(pl) }
                                },
                                onPlayAll = { pl ->
                                    scope.launch { viewModel.playAll(pl) }
                                }
                            )
                        }
                    }
                }
            }

            // Library
            item {
                SectionHeader(title = "Library")
                if (uiState.library.isEmpty()) {
                    Text(
                        "Your saved items will appear here",
                        modifier = Modifier.padding(horizontal = 24.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.6f)
                    )
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
                            certification = null,
                            videos = null
                        )
                    }
                    GridSection(items = metaItems, onItemClick = onItemClick, progressMap = uiState.progressMap)
                }
            }

            // Watchlist
            item {
                SectionHeader(title = "Watchlist")
                if (uiState.watchlist.isEmpty()) {
                    Text(
                        "Add items to your watchlist to track them",
                        modifier = Modifier.padding(horizontal = 24.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.6f)
                    )
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
                            certification = null,
                            videos = null
                        )
                    }
                    GridSection(items = metaItems, onItemClick = onItemClick, progressMap = uiState.progressMap)
                }
            }

            // History
            item {
                SectionHeader(title = "History")
                if (uiState.history.isEmpty()) {
                    Text(
                        "Your viewing history is currently empty",
                        modifier = Modifier.padding(horizontal = 24.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                } else {
                    val metaItems = uiState.history.map { hist ->
                        MetaItem(
                            id = hist.id,
                            type = "movie",
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
                            certification = null,
                            videos = null
                        )
                    }
                    GridSection(items = metaItems, onItemClick = onItemClick, progressMap = uiState.progressMap)
                }
            }
        }
    }

    // Smart Playlist Detail Sheet
    if (showPlaylistDetail && selectedPlaylist != null) {
        val playlist = selectedPlaylist!!
        val episodes = viewModel.parsePlaylistEpisodes(playlist)
        SmartPlaylistDetailSheet(
            playlist = playlist,
            episodes = episodes,
            onDismiss = { showPlaylistDetail = false },
            onRetryMissing = {
                scope.launch {
                    viewModel.retryMissingEpisodes(playlist)
                    showPlaylistDetail = false
                }
            },
            onManualPick = { episode ->
                scope.launch {
                    viewModel.manualPickEpisode(playlist, episode)
                }
            },
            onPlayEpisode = { episode ->
                scope.launch {
                    viewModel.playEpisode(episode)
                    showPlaylistDetail = false
                }
            },
            // ✅ Play All callback
            onPlayAll = {
                scope.launch {
                    viewModel.playAll(playlist)
                    showPlaylistDetail = false
                }
            },
            isLoading = uiState.isPlayAllLoading
        )
    }
}