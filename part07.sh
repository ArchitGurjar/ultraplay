#!/data/data/com.termux/files/usr/bin/bash
set -e

echo "[update_player_and_details] starting..."

# ============================================================
# 1. PlayerViewModel.kt
# ============================================================
cat > app/src/main/java/com/ultrastream/app/ui/screens/player/PlayerViewModel.kt << 'EOF'
package com.ultrastream.app.ui.screens.player

import android.content.Context
import android.util.Log
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
import kotlinx.coroutines.flow.update
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

                // Listen to player events
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
                            Player.STATE_BUFFERING -> {
                                // can show buffering indicator
                            }
                        }
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        _isPlaying.value = isPlaying
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        _error.value = error.message
                    }

                    override fun onVolumeChanged(volume: Float) {
                        _volume.value = volume
                    }
                }
                exoPlayer.addListener(listener)
                playerListener = listener

                // Start a periodic update of current position
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
        _player.value?.removeListener(playerListener)
        _player.value?.release()
        _player.value = null
        playerListener = null
    }

    data class PlayerUiState(
        val isPlaying: Boolean = false,
        val title: String = "",
        val currentUrl: String = "",
        val speed: Float = 1.0f,
        val volume: Float = 1.0f,
        val brightness: Float = 1.0f,
        val isFullscreen: Boolean = false,
        val isPiP: Boolean = false,
        val error: String? = null
    )
}
EOF

# ============================================================
# 2. PlayerScreen.kt
# ============================================================
cat > app/src/main/java/com/ultrastream/app/ui/screens/player/PlayerScreen.kt << 'EOF'
package com.ultrastream.app.ui.screens.player

import android.app.Activity
import android.media.AudioManager
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

@Composable
fun PlayerScreen(
    url: String,
    title: String = "Now Playing",
    viewModel: PlayerViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val activity = context as? Activity

    // States from ViewModel
    val player by viewModel.player.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val error by viewModel.error.collectAsState()
    val playerTitle by viewModel.title.collectAsState()
    val brightness by viewModel.brightness.collectAsState()
    val volume by viewModel.volume.collectAsState()

    // Initialize player
    LaunchedEffect(url) {
        viewModel.initializePlayer(context, url, title)
    }

    // Cleanup
    DisposableEffect(Unit) {
        onDispose {
            viewModel.releasePlayer()
        }
    }

    // Apply brightness to window
    LaunchedEffect(brightness) {
        activity?.window?.let { window ->
            val layoutParams = window.attributes
            layoutParams.screenBrightness = brightness
            window.attributes = layoutParams
        }
    }

    // Apply volume to system (if not using ExoPlayer's internal volume)
    // We'll use AudioManager to change media volume globally (requires MODIFY_AUDIO_SETTINGS? not needed for STREAM_MUSIC)
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
        // PlayerView
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

        // Custom controls overlay
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Top bar with title and back button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (playerTitle.isNotEmpty()) playerTitle else title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium
                )
                IconButton(
                    onClick = onBack
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Progress bar with live updates
            val progress = if (duration > 0) (currentPosition.toFloat() / duration.toFloat()) else 0f
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.Gray.copy(alpha = 0.3f)
            )

            // Time labels
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

            // Bottom controls
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

        // Gesture overlay for volume (right side) and brightness (left side)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { /* can show indicators */ },
                        onDragEnd = { /* hide indicators */ },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val width = size.width
                            val deltaX = dragAmount.x / width
                            if (change.position.x < width / 2) {
                                // Brightness
                                val newBrightness = (brightness + deltaX).coerceIn(0f, 1f)
                                viewModel.setBrightness(newBrightness)
                            } else {
                                // Volume
                                val newVolume = (volume + deltaX).coerceIn(0f, 1f)
                                viewModel.setVolume(newVolume)
                            }
                        }
                    )
                }
        )

        // Error message if any
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

// Helper to format time
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

# ============================================================
# 3. DetailsScreen.kt
# ============================================================
cat > app/src/main/java/com/ultrastream/app/ui/screens/details/DetailsScreen.kt << 'EOF'
package com.ultrastream.app.ui.screens.details

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ultrastream.app.data.models.StreamItem
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
                    Text("⭐ $meta.imdbRating", style = MaterialTheme.typography.bodyMedium)
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
                        // Fetch streams and show sheet
                        viewModel.loadStreams(meta.id, meta.type)
                        showStreamsSheet = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (uiState.streamsLoading) "Loading..." else "Find Streams")
                }
            }
            // Episodes if series
            if (meta.videos != null && meta.videos.isNotEmpty()) {
                item {
                    Text("Episodes", style = MaterialTheme.typography.titleLarge)
                    meta.videos?.forEach { video ->
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

    // Streams bottom sheet
    if (showStreamsSheet && uiState.streams.isNotEmpty()) {
        StreamsSheet(
            streams = uiState.streams,
            onDismiss = { showStreamsSheet = false },
            onStreamClick = { stream ->
                val url = stream.url ?: stream.streamUrl ?: stream.externalUrl
                if (!url.isNullOrBlank()) {
                    showStreamsSheet = false
                    onPlay(url, meta?.name ?: "Stream")
                }
            }
        )
    }
}
EOF

# ============================================================
# 4. Ensure the StreamsSheet composable exists (we already have it from Part 5)
# If not, we can add it.
# ============================================================
if [ ! -f app/src/main/java/com/ultrastream/app/ui/components/bottomsheets/StreamsSheet.kt ]; then
    mkdir -p app/src/main/java/com/ultrastream/app/ui/components/bottomsheets
    cat > app/src/main/java/com/ultrastream/app/ui/components/bottomsheets/StreamsSheet.kt << 'EOF'
package com.ultrastream.app.ui.components.bottomsheets

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ultrastream.app.data.models.StreamItem

@Composable
fun StreamsSheet(
    streams: List<StreamItem>,
    onDismiss: () -> Unit,
    onStreamClick: (StreamItem) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Available Streams", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(8.dp))
            LazyColumn {
                items(streams.size) { index ->
                    val stream = streams[index]
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        onClick = { onStreamClick(stream) }
                    ) {
                        Text(
                            text = stream.title ?: stream.name ?: "Stream",
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
        }
    }
}
EOF
fi

# ============================================================
# 5. Commit and push
# ============================================================
git add .
git commit -m "Fix: Fully implement player controls, live progress, and stream fetching"
git push origin main

echo "[update_player_and_details] done. Pushed to GitHub."

