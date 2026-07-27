@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.ultrastream.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ultrastream.app.data.models.SmartPlaylist
import com.ultrastream.app.ui.theme.premiumGlass

@Composable
fun SmartPlaylistCard(
    playlist: SmartPlaylist,
    onClick: () -> Unit,
    onExportM3u: (SmartPlaylist) -> Unit,
    onPlayAll: (SmartPlaylist) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .width(300.dp)
            .height(160.dp),
        shape = RoundedCornerShape(24.dp),
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Row(modifier = Modifier.fillMaxSize().premiumGlass(RoundedCornerShape(24.dp))) {
            AsyncImage(
                model = playlist.poster,
                contentDescription = playlist.metaName,
                modifier = Modifier
                    .width(110.dp)
                    .fillMaxHeight(),
                contentScale = ContentScale.Crop
            )
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f)
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = playlist.metaName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = Color.White
                    )
                    Text(
                        text = "SEASON ${playlist.season}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.6f),
                        fontWeight = FontWeight.Bold
                    )
                }

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${playlist.fetched}/${playlist.total} EPISODES",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                        Text(
                            text = playlist.status.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (playlist.status == "Complete") Color.Green else Color.Yellow,
                            fontWeight = FontWeight.Black
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    val progress = if (playlist.total > 0) playlist.fetched.toFloat() / playlist.total else 0f
                    LinearProgressIndicator(
                        progress = progress,
                        modifier = Modifier.fillMaxWidth().height(4.dp),
                        color = if (progress == 1f) Color.Green else MaterialTheme.colorScheme.primary,
                        trackColor = Color.White.copy(alpha = 0.1f),
                        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = { onPlayAll(playlist) },
                        modifier = Modifier.size(36.dp).premiumGlass(RoundedCornerShape(50))
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Play All", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    IconButton(
                        onClick = { onExportM3u(playlist) },
                        modifier = Modifier.size(36.dp).premiumGlass(RoundedCornerShape(50))
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = "Export M3U", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

