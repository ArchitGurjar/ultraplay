@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.ultrastream.app.ui.components.bottomsheets

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ultrastream.app.data.models.PlaylistEpisode
import com.ultrastream.app.data.models.SmartPlaylist

@Composable
fun SmartPlaylistDetailSheet(
    playlist: SmartPlaylist,
    episodes: List<PlaylistEpisode>,
    onDismiss: () -> Unit,
    onRetryMissing: () -> Unit,
    onManualPick: (PlaylistEpisode) -> Unit,
    onPlayEpisode: (PlaylistEpisode) -> Unit,
    onPlayAll: () -> Unit,
    isLoading: Boolean = false
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header with Play All button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${playlist.metaName} - Season ${playlist.season}",
                    style = MaterialTheme.typography.headlineSmall
                )
                // ✅ Play All button with loading state
                Button(
                    onClick = onPlayAll,
                    modifier = Modifier.height(40.dp),
                    shape = MaterialTheme.shapes.small,
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Play All")
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Play All")
                    }
                }
            }

            // Progress info
            Text(
                text = "Progress: ${playlist.fetched}/${playlist.total} - ${playlist.status}",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Retry Missing button (if episodes are missing)
            if (playlist.fetched < playlist.total) {
                Button(
                    onClick = onRetryMissing,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Retry Missing Episodes")
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Episode list
            LazyColumn {
                items(episodes) { episode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "E${episode.epNum} - ${episode.epName}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = if (episode.isMissing) "❌ Missing" else "✅ Found",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (episode.isMissing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            )
                        }
                        Row {
                            if (!episode.isMissing && episode.stream != null) {
                                IconButton(onClick = { onPlayEpisode(episode) }) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "Play")
                                }
                            }
                            if (episode.isMissing) {
                                IconButton(onClick = { onManualPick(episode) }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Manual Pick")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}