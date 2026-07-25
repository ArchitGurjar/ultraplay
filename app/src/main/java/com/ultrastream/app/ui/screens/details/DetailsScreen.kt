@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.ultrastream.app.ui.screens.details

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as lazyColumnItems
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.ultrastream.app.data.models.StreamItem
import com.ultrastream.app.data.models.Subtitle
import com.ultrastream.app.ui.components.EpisodeCard
import com.ultrastream.app.ui.components.bottomsheets.SeasonsSheet
import com.ultrastream.app.ui.components.bottomsheets.StreamsSheet
import com.ultrastream.app.ui.components.bottomsheets.SubtitlesSheet
import com.ultrastream.app.ui.theme.*
import com.ultrastream.app.utils.M3UExporter
import com.ultrastream.app.utils.SubtitleEvent
import com.ultrastream.app.utils.SubtitleHolder
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun DetailsScreen(
    id: String,
    type: String,
    viewModel: DetailsViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onPlay: (stream: StreamItem, title: String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    var showSeasonsSheet by remember { mutableStateOf(false) }
    var showStreamsSheet by remember { mutableStateOf(false) }
    var showActionSheet by remember { mutableStateOf(false) }
    var showSubtitlesSheet by remember { mutableStateOf(false) }
    var selectedStream by remember { mutableStateOf<StreamItem?>(null) }
    var subtitlesList by remember { mutableStateOf<List<Subtitle>>(emptyList()) }
    var streamsRequested by remember { mutableStateOf(false) }
    var episodesLoading by remember { mutableStateOf(true) } // ✅ added for skeleton

    val meta = uiState.meta
    val filteredEpisodes by viewModel.filteredEpisodes.collectAsState()
    val availableSeasons by viewModel.availableSeasons.collectAsState()
    val selectedSeason by viewModel.selectedSeason.collectAsState()
    val isAllSeasons by viewModel.isAllSeasons.collectAsState()
    val isSeries = meta?.type == "series" || meta?.type == "anime"

    LaunchedEffect(id, type) {
        viewModel.loadMeta(id, type)
    }

    LaunchedEffect(meta) {
        if (isSeries) {
            val seasons = meta?.videos?.mapNotNull { it.season }?.distinct()?.sorted() ?: emptyList()
            if (seasons.isNotEmpty() && uiState.selectedSeason == null) {
                viewModel.selectSeasonAndLoad(seasons.first())
            }
        }
    }

    // ✅ Show skeleton while episodes are loading
    LaunchedEffect(filteredEpisodes) {
        episodesLoading = filteredEpisodes.isEmpty()
    }

    LaunchedEffect(uiState.streamsLoading, streamsRequested) {
        if (!uiState.streamsLoading && streamsRequested) {
            if (uiState.streams.isNotEmpty()) {
                showStreamsSheet = true
            } else {
                // ✅ Show "No streams" message via toast or we'll handle it in the sheet condition
                Toast.makeText(context, "No streams available. Try installing Torrentio addon.", Toast.LENGTH_LONG).show()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(BackgroundDark)) {
        if (meta != null) {
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                // Hero Section (unchanged, omitted for brevity)
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
                                        colors = listOf(Color.Transparent, BackgroundDark.copy(alpha = 0.8f), BackgroundDark),
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
                                    IconButton(onClick = { viewModel.toggleWatchlist(meta) }) {
                                        Icon(
                                            imageVector = if (uiState.inWatchlist) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                                            contentDescription = "Watchlist",
                                            tint = if (uiState.inWatchlist) AccentBlue else Color.White
                                        )
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
                                            contentDescription = "Library",
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
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(meta.year ?: "", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                                Text("•", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                                Text(meta.runtime ?: "N/A", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                                Text("•", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                                Text(
                                    "⭐ ${meta.imdbRating ?: "N/A"}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = AccentBlue,
                                    fontWeight = FontWeight.Bold
                                )
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

                            // MOVIE ACTION BUTTON
                            if (!isSeries) {
                                Button(
                                    onClick = {
                                        viewModel.loadStreams(meta.id, meta.type, null, null)
                                        streamsRequested = true
                                    },
                                    modifier = Modifier.fillMaxWidth().height(56.dp),
                                    shape = RoundedCornerShape(50),
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue, contentColor = Color.Black)
                                ) {
                                    Icon(Icons.Default.Satellite, contentDescription = null, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(if (uiState.streamsLoading) "Loading Streams..." else "Find Streams", fontWeight = FontWeight.Bold)
                                }
                            }

                            // External Links (unchanged)
                            Column(modifier = Modifier.padding(top = 16.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        val imdbId = meta.imdbId
                                        if (!imdbId.isNullOrBlank()) {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.imdb.com/title/$imdbId")))
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                    shape = RoundedCornerShape(50),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                                ) {
                                    Icon(Icons.Default.Movie, contentDescription = null, tint = Color.White)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("View on IMDb", fontWeight = FontWeight.Bold, color = Color.White)
                                }
                                if (meta.videos?.any { it.url != null } == true) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedButton(
                                        onClick = {
                                            val trailerUrl = meta.videos?.firstOrNull { it.url != null }?.url
                                            if (!trailerUrl.isNullOrBlank()) {
                                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(trailerUrl)))
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth().height(48.dp),
                                        shape = RoundedCornerShape(50),
                                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Watch Trailer", fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }

                // Series Season Chips & Episodes
                if (isSeries) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = isAllSeasons,
                                onClick = { viewModel.toggleAllSeasons() },
                                label = { Text("All Seasons") }
                            )
                            availableSeasons.forEach { season ->
                                FilterChip(
                                    selected = season == selectedSeason && !isAllSeasons,
                                    onClick = { viewModel.selectSeasonAndLoad(season) },
                                    label = { Text("S$season") }
                                )
                            }
                        }
                    }

                    item {
                        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                            // ✅ Skeleton while loading episodes
                            if (episodesLoading) {
                                repeat(3) {
                                    SkeletonEpisodeCard()
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                            } else {
                                filteredEpisodes.forEach { video ->
                                    val epNum = video.episode ?: 0
                                    val seasonNum = video.season ?: 0
                                    val isWatched = uiState.watchProgress?.percent?.let { it >= 100 } ?: false
                                    val progressPercent = uiState.watchProgress?.percent ?: 0

                                    EpisodeCard(
                                        video = video,
                                        isWatched = isWatched,
                                        progressPercent = progressPercent,
                                        onClick = {
                                            val currentMeta = uiState.meta
                                            if (currentMeta != null) {
                                                viewModel.selectEpisode(epNum)
                                                viewModel.loadStreams(currentMeta.id, currentMeta.type, seasonNum, epNum)
                                                streamsRequested = true
                                            } else {
                                                Toast.makeText(context, "Metadata not loaded", Toast.LENGTH_SHORT).show()
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

    // Streams Sheet – now with empty state handling
    if (showStreamsSheet) {
        if (uiState.streams.isNotEmpty()) {
            StreamsSheet(
                streams = uiState.streams,
                onDismiss = {
                    showStreamsSheet = false
                    streamsRequested = false
                },
                onStreamClick = { stream ->
                    showStreamsSheet = false
                    selectedStream = stream
                    showActionSheet = true
                }
            )
        } else {
            // Show a placeholder or toast (handled in LaunchedEffect)
            // We'll use a simple box to show a message.
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Text(
                        text = "No streams found. Please install a stream addon like Torrentio.",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    // Action Sheet, Subtitles Sheet, Seasons Sheet (unchanged)
    // ... (keep the rest as is)
}

// ✅ Skeleton Episode Card (shimmer placeholder)
@Composable
fun SkeletonEpisodeCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(90.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .width(140.dp)
                    .fillMaxHeight()
                    .background(Color.Gray.copy(alpha = 0.2f))
            )
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(16.dp)
                        .background(Color.Gray.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .height(12.dp)
                        .background(Color.Gray.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.4f)
                        .height(12.dp)
                        .background(Color.Gray.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                )
            }
        }
    }
}
