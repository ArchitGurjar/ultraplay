cd /sdcard/ultrastream/MyNewApp

# 1. MainActivity.kt को ठीक करना (Bottom Nav list syntax fixed)
cat > app/src/main/java/com/ultrastream/app/MainActivity.kt << 'EOF'
package com.ultrastream.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import com.ultrastream.app.ui.navigation.Screen
import com.ultrastream.app.ui.screens.addons.AddonsScreen
import com.ultrastream.app.ui.screens.details.DetailsScreen
import com.ultrastream.app.ui.screens.home.HomeScreen
import com.ultrastream.app.ui.screens.library.LibraryScreen
import com.ultrastream.app.ui.screens.player.PlayerScreen
import com.ultrastream.app.ui.screens.profile.ProfileScreen
import com.ultrastream.app.ui.screens.search.SearchScreen
import com.ultrastream.app.ui.theme.UltraStreamTheme
import java.net.URLEncoder
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            UltraStreamTheme {
                UltraStreamNavHost()
            }
        }
    }
}

data class NavItem(val screen: Screen, val title: String, val iconRes: Int)

@Composable
fun UltraStreamNavHost() {
    val navController = rememberNavController()
    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                val items = listOf(
                    NavItem(Screen.Home, "Home", R.drawable.ic_home),
                    NavItem(Screen.Library, "Library", R.drawable.ic_library),
                    NavItem(Screen.Search, "Search", R.drawable.ic_search),
                    NavItem(Screen.Addons, "Addons", R.drawable.ic_addon),
                    NavItem(Screen.Profile, "Profile", R.drawable.ic_profile)
                )
                items.forEach { item ->
                    NavigationBarItem(
                        icon = { Icon(imageVector = ImageVector.vectorResource(id = item.iconRes), contentDescription = item.title) },
                        label = { Text(item.title) },
                        selected = currentDestination?.hierarchy?.any { it.route == item.screen.route } == true,
                        onClick = {
                            navController.navigate(item.screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                HomeScreen { id, type ->
                    navController.navigate(Screen.Details.pass(id, type))
                }
            }
            composable(Screen.Library.route) {
                LibraryScreen { id, type ->
                    navController.navigate(Screen.Details.pass(id, type))
                }
            }
            composable(Screen.Search.route) {
                SearchScreen { id, type ->
                    navController.navigate(Screen.Details.pass(id, type))
                }
            }
            composable(Screen.Addons.route) {
                AddonsScreen()
            }
            composable(Screen.Profile.route) {
                ProfileScreen()
            }
            composable(Screen.Details.route) { backStackEntry ->
                val id = backStackEntry.arguments?.getString("id") ?: ""
                val type = backStackEntry.arguments?.getString("type") ?: ""
                DetailsScreen(
                    id = id,
                    type = type,
                    onBack = { navController.popBackStack() },
                    onPlay = { url, title ->
                        val encodedUrl = URLEncoder.encode(url, StandardCharsets.UTF_8.toString())
                        navController.navigate(Screen.Player.pass(encodedUrl))
                    }
                )
            }
            composable(Screen.Player.route) { backStackEntry ->
                val encodedUrl = backStackEntry.arguments?.getString("url") ?: ""
                val url = URLDecoder.decode(encodedUrl, StandardCharsets.UTF_8.toString())
                PlayerScreen(url = url, title = "Now Playing") {
                    navController.popBackStack()
                }
            }
        }
    }
}
EOF

# 2. DetailsScreen.kt को ठीक करना (String template fix)
cat > app/src/main/java/com/ultrastream/app/ui/screens/details/DetailsScreen.kt << 'EOF'
package com.ultrastream.app.ui.screens.details

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ultrastream.app.ui.components.bottomsheets.StreamsSheet

@Composable
fun DetailsScreen(
    id: String,
    type: String,
    viewModel: DetailsViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onPlay: (url: String, title: String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var showStreamsSheet by remember { mutableStateOf(false) }

    LaunchedEffect(id, type) {
        viewModel.loadMeta(id, type)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp)
    ) {
        val meta = uiState.meta
        if (meta != null) {
            item {
                Text(meta.name, style = MaterialTheme.typography.headlineMedium)
                if (meta.year != null) {
                    Text(meta.year, style = MaterialTheme.typography.bodyMedium)
                }
                if (meta.imdbRating != null) {
                    Text("⭐ ${meta.imdbRating}", style = MaterialTheme.typography.bodyMedium)
                }
                Text(meta.description ?: "", style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(8.dp))
                Row {
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
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        viewModel.loadStreams(meta.id, meta.type)
                        showStreamsSheet = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (uiState.streamsLoading) "Loading..." else "Find Streams")
                }
            }
            if (meta.videos != null && meta.videos.isNotEmpty()) {
                item {
                    Text("Episodes", style = MaterialTheme.typography.titleLarge)
                    meta.videos.forEach { video ->
                        Text("S${video.season}E${video.episode} - ${video.name ?: "Episode"}")
                    }
                }
            }
        } else if (uiState.isLoading) {
            item {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        } else if (uiState.error != null) {
            item {
                Text("Error: ${uiState.error}", color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (showStreamsSheet && uiState.streams.isNotEmpty()) {
        StreamsSheet(
            streams = uiState.streams,
            onDismiss = { showStreamsSheet = false },
            onStreamClick = { stream ->
                val streamUrl = stream.url ?: stream.streamUrl ?: stream.externalUrl
                if (!streamUrl.isNullOrBlank()) {
                    showStreamsSheet = false
                    onPlay(streamUrl, meta?.name ?: "Stream")
                }
            }
        )
    }
}
EOF

# 3. FilterChipGroup.kt को ठीक करना (Missing lazy imports)
cat > app/src/main/java/com/ultrastream/app/ui/components/FilterChipGroup.kt << 'EOF'
package com.ultrastream.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun FilterChipGroup(
    chips: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(chips) { chip ->
            val isSelected = chip.lowercase() == selected.lowercase()
            AssistChip(
                onClick = { onSelect(chip) },
                label = { Text(chip) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                    labelColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                )
            )
        }
    }
}
EOF

# 4. GridSection.kt को ठीक करना (Missing fillMaxWidth import)
cat > app/src/main/java/com/ultrastream/app/ui/components/GridSection.kt << 'EOF'
package com.ultrastream.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ultrastream.app.data.models.MetaItem

@Composable
fun GridSection(
    items: List<MetaItem>,
    onItemClick: (id: String, type: String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 120.dp),
        modifier = modifier,
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(items.size) { index ->
            val item = items[index]
            PosterCard(
                meta = item,
                onClick = { onItemClick(item.id, item.type) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
EOF

# 5. AddonsScreen.kt को ठीक करना (Delete icon import)
cat > app/src/main/java/com/ultrastream/app/ui/screens/addons/AddonsScreen.kt << 'EOF'
package com.ultrastream.app.ui.screens.addons

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch

@Composable
fun AddonsScreen(
    viewModel: AddonsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
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
        item {
            OutlinedTextField(
                value = addonUrl,
                onValueChange = { addonUrl = it },
                label = { Text("Manifest URL") },
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    scope.launch {
                        val success = viewModel.installAddon(addonUrl)
                        if (success) addonUrl = ""
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Install Addon")
            }
        }
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
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Debrid Key")
            }
        }
        item {
            Text("Installed Addons", style = MaterialTheme.typography.titleMedium)
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
                    Column {
                        Text(addon.name, style = MaterialTheme.typography.titleSmall)
                        Text(addon.url, style = MaterialTheme.typography.bodySmall)
                    }
                    Row {
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
EOF

# 6. PlayerScreen.kt को ठीक करना (Color and Icons imports fixed)
cat > app/src/main/java/com/ultrastream/app/ui/screens/player/PlayerScreen.kt << 'EOF'
package com.ultrastream.app.ui.screens.player

import android.app.Activity
import android.media.AudioManager
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forward
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.ui.PlayerView

@Composable
fun PlayerScreen(
    url: String,
    title: String = "Now Playing",
    viewModel: PlayerViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity

    val player by viewModel.player.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val error by viewModel.error.collectAsState()
    val playerTitle by viewModel.title.collectAsState()
    val brightness by viewModel.brightness.collectAsState()
    val volume by viewModel.volume.collectAsState()

    LaunchedEffect(url) {
        viewModel.initializePlayer(context, url, title)
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.releasePlayer()
        }
    }

    LaunchedEffect(brightness) {
        activity?.window?.let { window ->
            val layoutParams = window.attributes
            layoutParams.screenBrightness = brightness
            window.attributes = layoutParams
        }
    }

    LaunchedEffect(volume) {
        val audioManager = context.getSystemService(AudioManager::class.java)
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val targetVol = (volume * maxVol).toInt()
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    useController = false
                    resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { playerView ->
                playerView.player = player
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (playerTitle.isNotEmpty()) playerTitle else title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium
                )
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            val progress = if (duration > 0) (currentPosition.toFloat() / duration.toFloat()) else 0f
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.Gray.copy(alpha = 0.3f)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatTime(currentPosition),
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelSmall
                )
                Text(
                    text = formatTime(duration),
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelSmall
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                IconButton(onClick = { viewModel.skipBackward() }) {
                    Icon(Icons.Default.Replay, contentDescription = "Back 10s", tint = Color.White)
                }
                IconButton(onClick = { viewModel.playPause() }) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = Color.White
                    )
                }
                IconButton(onClick = { viewModel.skipForward() }) {
                    Icon(Icons.Default.Forward, contentDescription = "Forward 10s", tint = Color.White)
                }
                IconButton(onClick = { /* toggle fullscreen */ }) {
                    Icon(Icons.Default.Fullscreen, contentDescription = "Fullscreen", tint = Color.White)
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val width = size.width
                            val deltaX = dragAmount.x / width
                            if (change.position.x < width / 2) {
                                val newBrightness = (brightness + deltaX).coerceIn(0f, 1f)
                                viewModel.setBrightness(newBrightness)
                            } else {
                                val newVolume = (volume + deltaX).coerceIn(0f, 1f)
                                viewModel.setVolume(newVolume)
                            }
                        }
                    )
                }
        )

        if (error != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Text(
                        text = "Error: $error",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }
}

private fun formatTime(millis: Long): String {
    if (millis <= 0) return "0:00"
    val seconds = millis / 1000
    val minutes = seconds / 60
    val secs = seconds % 60
    return if (minutes >= 60) {
        val hours = minutes / 60
        "%d:%02d:%02d".format(hours, minutes % 60, secs)
    } else {
        "%d:%02d".format(minutes, secs)
    }
}
EOF

# 7. PlayerViewModel.kt को ठीक करना (Listener type safe check)
cat > app/src/main/java/com/ultrastream/app/ui/screens/player/PlayerViewModel.kt << 'EOF'
package com.ultrastream.app.ui.screens.player

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor() : ViewModel() {

    private val _player = MutableStateFlow<ExoPlayer?>(null)
    val player: StateFlow<ExoPlayer?> = _player.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title.asStateFlow()

    private val _speed = MutableStateFlow(1.0f)
    val speed: StateFlow<Float> = _speed.asStateFlow()

    private val _volume = MutableStateFlow(1.0f)
    val volume: StateFlow<Float> = _volume.asStateFlow()

    private val _brightness = MutableStateFlow(1.0f)
    val brightness: StateFlow<Float> = _brightness.asStateFlow()

    private var playerListener: Player.Listener? = null

    fun initializePlayer(context: Context, url: String, title: String) {
        viewModelScope.launch {
            try {
                val trackSelector = DefaultTrackSelector(context)
                val exoPlayer = ExoPlayer.Builder(context)
                    .setTrackSelector(trackSelector)
                    .build()

                val dataSourceFactory = DefaultHttpDataSource.Factory()
                val mediaSource = createMediaSource(url, dataSourceFactory)
                exoPlayer.setMediaSource(mediaSource)
                exoPlayer.prepare()
                exoPlayer.playWhenReady = true

                _player.value = exoPlayer
                _title.value = title

                val listener = object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        when (playbackState) {
                            Player.STATE_READY -> {
                                _duration.value = exoPlayer.duration
                                _isPlaying.value = exoPlayer.isPlaying
                            }
                            Player.STATE_ENDED -> {
                                _isPlaying.value = false
                            }
                            else -> {}
                        }
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        _isPlaying.value = isPlaying
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        _error.value = error.message
                    }
                }
                exoPlayer.addListener(listener)
                playerListener = listener

                viewModelScope.launch {
                    while (true) {
                        _currentPosition.value = exoPlayer.currentPosition
                        kotlinx.coroutines.delay(200)
                    }
                }

            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    private fun createMediaSource(url: String, dataSourceFactory: DefaultHttpDataSource.Factory): MediaSource {
        val uri = android.net.Uri.parse(url)
        return when {
            url.contains(".m3u8") -> HlsMediaSource.Factory(dataSourceFactory).createMediaSource(MediaItem.fromUri(uri))
            url.contains(".mpd") -> DashMediaSource.Factory(dataSourceFactory).createMediaSource(MediaItem.fromUri(uri))
            else -> androidx.media3.exoplayer.source.ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(uri))
        }
    }

    fun playPause() {
        _player.value?.let { player ->
            if (player.isPlaying) {
                player.pause()
                _isPlaying.value = false
            } else {
                player.play()
                _isPlaying.value = true
            }
        }
    }

    fun skipForward(seconds: Long = 10) {
        _player.value?.let { player ->
            val newPos = player.currentPosition + seconds * 1000
            player.seekTo(newPos.coerceAtMost(player.duration))
        }
    }

    fun skipBackward(seconds: Long = 10) {
        _player.value?.let { player ->
            val newPos = player.currentPosition - seconds * 1000
            player.seekTo(newPos.coerceAtLeast(0))
        }
    }

    fun seekTo(position: Long) {
        _player.value?.seekTo(position.coerceIn(0, _duration.value))
    }

    fun setSpeed(speed: Float) {
        _player.value?.setPlaybackSpeed(speed)
        _speed.value = speed
    }

    fun setVolume(volume: Float) {
        _player.value?.volume = volume.coerceIn(0f, 1f)
        _volume.value = volume
    }

    fun setBrightness(brightness: Float) {
        _brightness.value = brightness.coerceIn(0f, 1f)
    }

    fun releasePlayer() {
        playerListener?.let { listener ->
            _player.value?.removeListener(listener)
        }
        _player.value?.release()
        _player.value = null
        playerListener = null
    }
}
EOF

# 8. बदलावों को गिट पर ऐड और पुश करना
git add .
git commit -m "Fix: Resolve all Compose syntax, missing imports and listener type mismatches"
git push origin main

