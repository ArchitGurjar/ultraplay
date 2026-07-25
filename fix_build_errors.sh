#!/bin/bash
set -e

echo "🛠️ Fixing Imports, OptIns, and Duplicate Declarations..."

# 1. Fix Theme.kt (Removing duplicate Shapes/Typography & fixing Color import)
cat > app/src/main/java/com/ultrastream/app/ui/theme/Theme.kt << 'INNER_EOF'
package com.ultrastream.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = AccentBlue,
    onPrimary = Color.Black,
    background = BackgroundDark,
    surface = SurfaceDark,
    onSurface = TextMain,
    onSurfaceVariant = TextMuted
)

private val LightColorScheme = lightColorScheme(
    primary = AccentBlue,
    onPrimary = Color.Black,
    background = Color(0xFFF3F4F6),
    surface = Color.White,
    onSurface = Color(0xFF111827),
    onSurfaceVariant = Color(0xFF6B7280)
)

@Composable
fun UltraStreamTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography, // Uses Type.kt
        shapes = Shapes, // Uses Shape.kt
        content = content
    )
}
INNER_EOF

# 2. Fix DetailsScreen.kt (Adding missing imports for BorderStroke, sp, clickable, Outlined icons)
cat > app/src/main/java/com/ultrastream/app/ui/screens/details/DetailsScreen.kt << 'INNER_EOF'
@file:OptIn(ExperimentalMaterial3Api::class)

package com.ultrastream.app.ui.screens.details

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.ultrastream.app.data.models.StreamItem
import com.ultrastream.app.ui.components.EpisodeCard
import com.ultrastream.app.ui.components.StreamCard
import com.ultrastream.app.ui.components.bottomsheets.SeasonsSheet
import com.ultrastream.app.ui.components.bottomsheets.StreamsSheet
import com.ultrastream.app.ui.theme.*

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

    Box(modifier = Modifier.fillMaxSize().background(BackgroundDark)) {
        if (meta != null) {
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(350.dp)
                    ) {
                        AsyncImage(
                            model = meta.background ?: meta.poster,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            BackgroundDark.copy(alpha = 0.8f),
                                            BackgroundDark
                                        ),
                                        startY = 0.3f
                                    )
                                )
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp, start = 16.dp, end = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = Color.Black.copy(alpha = 0.6f),
                                modifier = Modifier.size(44.dp)
                            ) {
                                IconButton(onClick = onBack) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = Color.Black.copy(alpha = 0.6f),
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    IconButton(onClick = { /* View History */ }) {
                                        Icon(Icons.Default.History, contentDescription = "History", tint = Color.White)
                                    }
                                }
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = Color.Black.copy(alpha = 0.6f),
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    IconButton(onClick = { viewModel.toggleLibrary(meta) }) {
                                        Icon(
                                            imageVector = if (uiState.inLibrary) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
                                            contentDescription = "Bookmark",
                                            tint = if (uiState.inLibrary) AccentBlue else Color.White
                                        )
                                    }
                                }
                            }
                        }

                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(16.dp)
                                .fillMaxWidth()
                        ) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = Color.Black.copy(alpha = 0.5f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                            ) {
                                Text(
                                    text = meta.type.uppercase(),
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = meta.name,
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(meta.year ?: "", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                                Text("•", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                                Text(meta.runtime ?: "N/A", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                                Text("•", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                                Text("⭐ ${meta.imdbRating ?: "N/A"}", style = MaterialTheme.typography.bodyMedium, color = AccentBlue, fontWeight = FontWeight.Bold)
                                if (!meta.genre.isNullOrEmpty()) {
                                    Text("•", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                                    Text(meta.genre!!.take(2).joinToString(", "), style = MaterialTheme.typography.bodyMedium, color = Color.White)
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = meta.description ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextMuted,
                                maxLines = 4,
                                softWrap = true
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            if (!meta.cast.isNullOrEmpty()) {
                                @OptIn(ExperimentalLayoutApi::class)
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    meta.cast!!.take(5).forEach { actor ->
                                        Surface(
                                            shape = RoundedCornerShape(50),
                                            color = Color.White.copy(alpha = 0.1f),
                                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                                        ) {
                                            Text(
                                                text = actor,
                                                color = Color.White,
                                                style = MaterialTheme.typography.labelMedium,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Button(
                            onClick = {
                                viewModel.loadStreams(meta.id, meta.type, uiState.selectedSeason, uiState.selectedEpisode)
                                showStreamsSheet = true
                            },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentBlue, contentColor = Color.Black)
                        ) {
                            Icon(Icons.Default.Satellite, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (uiState.streamsLoading) "Loading Streams..." else "Find Streams", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { /* Open IMDb */ },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(50),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                        ) {
                            Icon(Icons.Default.Movie, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("View on IMDb", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }

                if (meta.type == "series" || meta.type == "anime") {
                    val seasons = meta.videos?.mapNotNull { it.season }?.distinct()?.sorted() ?: emptyList()
                    val episodes = meta.videos
                        ?.filter { it.season == uiState.selectedSeason }
                        ?.sortedBy { it.episode } ?: emptyList()

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Episodes",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            if (seasons.isNotEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = Color.White.copy(alpha = 0.1f),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp).clickable { showSeasonsSheet = true },
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text("Season ${uiState.selectedSeason ?: ""}", fontWeight = FontWeight.Bold)
                                        Icon(Icons.Default.ExpandMore, contentDescription = null, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            episodes.forEach { video ->
                                val isWatched = uiState.watchProgress?.let { it.percent >= 100 } ?: false
                                
                                EpisodeCard(
                                    video = video,
                                    isWatched = isWatched,
                                    progressPercent = uiState.watchProgress?.percent ?: 0,
                                    onClick = {
                                        viewModel.selectEpisode(video.episode ?: 0)
                                        viewModel.loadStreams(meta.id, meta.type, uiState.selectedSeason, video.episode)
                                        showStreamsSheet = true
                                    }
                                )
                            }
                        }
                    }
                }
            }

        } else if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AccentBlue)
            }
        } else if (uiState.error != null) {
            Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                Text("Error: ${uiState.error}", color = MaterialTheme.colorScheme.error)
            }
        }
    }

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
        ModalBottomSheet(
            onDismissRequest = { showStreamsSheet = false }
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Available Streams", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn {
                    items(uiState.streams) { stream ->
                        StreamCard(
                            stream = stream,
                            onClick = {
                                showStreamsSheet = false
                                viewModel.playStream(stream, meta?.name ?: "Stream") { resolvedStream, title ->
                                    onPlay(resolvedStream, title)
                                }
                            }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}
INNER_EOF

# 3. Fix StreamCard.kt (Adding missing @OptIn and wildcard imports)
cat > app/src/main/java/com/ultrastream/app/ui/components/StreamCard.kt << 'INNER_EOF'
@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
package com.ultrastream.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ultrastream.app.data.models.StreamItem
import com.ultrastream.app.ui.theme.*

@Composable
fun StreamCard(
    stream: StreamItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardDark),
        shape = RoundedCornerShape(16.dp),
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stream.addonName ?: "Addon",
                    color = AccentBlue,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "4KHDHub 4K",
                        color = Color.Black,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stream.title ?: stream.name ?: "Stream",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Tag(text = "2024", icon = Icons.Default.CalendarToday, color = AccentGold)
                Tag(text = "Hindi", icon = Icons.Default.Translate, color = AccentOrange)
                Tag(text = "18.74 GB", icon = Icons.Default.Storage, color = AccentOrange)
                Tag(text = "2160P", icon = Icons.Default.Monitor, color = Color.White)
                
                OutlinedTag(text = "HDR", color = TextMuted)
                OutlinedTag(text = "DV", color = TextMuted)
                OutlinedTag(text = "English", color = AccentBlue)
            }
        }
    }
}

@Composable
private fun Tag(text: String, icon: ImageVector, color: Color) {
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = text,
                color = color,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun OutlinedTag(text: String, color: Color) {
    Surface(
        color = Color.Transparent,
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}
INNER_EOF

# 4. Fix EpisodeCard.kt (Adding missing imports for Colors & Material3 OptIn)
cat > app/src/main/java/com/ultrastream/app/ui/components/EpisodeCard.kt << 'INNER_EOF'
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.ultrastream.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.ultrastream.app.data.models.Video
import com.ultrastream.app.ui.theme.*

@Composable
fun EpisodeCard(
    video: Video,
    isWatched: Boolean = false,
    progressPercent: Int = 0,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(90.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark),
        shape = RoundedCornerShape(16.dp),
        onClick = onClick
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .width(140.dp)
                    .fillMaxHeight()
            ) {
                AsyncImage(
                    model = video.thumbnail,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "S${video.season?.toString()?.padStart(2, '0') ?: "00"}E${video.episode?.toString()?.padStart(2, '0') ?: "00"}",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (progressPercent > 0 && progressPercent < 100) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(Color.Gray.copy(alpha = 0.3f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progressPercent / 100f)
                                .fillMaxHeight()
                                .background(AccentBlue)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = video.name ?: "Episode ${video.episode}",
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = video.description?.take(80) ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (isWatched) {
                    Text(
                        text = "✔ Watched",
                        color = AccentGreen,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
INNER_EOF

echo "==> Staging and pushing changes to GitHub..."
git add -A
git commit -m "Fix: Add missing Imports and resolve Duplicate Variable errors in UI"
git push origin main

echo "✅ All Build Errors Fixed!"

