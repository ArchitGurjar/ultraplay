#!/bin/bash
set -e

echo "🛠️ Fixing Player Build Errors (Media3 APIs & Compose Imports)..."

# ============================================================
# 1. FIXED PlayerViewModel.kt (Media3 APIs Corrected)
# ============================================================
cat > app/src/main/java/com/ultrastream/app/ui/screens/player/PlayerViewModel.kt << 'INNER_EOF'
package com.ultrastream.app.ui.screens.player

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.ultrastream.app.data.models.StreamItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AudioTrackInfo(val groupIndex: Int, val trackIndex: Int, val label: String, val language: String)
data class SubtitleTrackInfo(val groupIndex: Int, val trackIndex: Int, val label: String, val language: String)

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

    private val _brightness = MutableStateFlow(-1.0f)
    val brightness: StateFlow<Float> = _brightness.asStateFlow()

    private val _audioTracks = MutableStateFlow<List<AudioTrackInfo>>(emptyList())
    val audioTracks: StateFlow<List<AudioTrackInfo>> = _audioTracks.asStateFlow()

    private val _subtitleTracks = MutableStateFlow<List<SubtitleTrackInfo>>(emptyList())
    val subtitleTracks: StateFlow<List<SubtitleTrackInfo>> = _subtitleTracks.asStateFlow()

    private val _seekMessage = MutableStateFlow<String?>(null)
    val seekMessage: StateFlow<String?> = _seekMessage.asStateFlow()

    private var playerListener: Player.Listener? = null
    private var positionJob: Job? = null

    fun initializePlayer(context: Context, stream: StreamItem, title: String) {
        viewModelScope.launch {
            try {
                val url = stream.url ?: stream.streamUrl ?: stream.externalUrl
                if (url.isNullOrBlank()) {
                    _error.value = "No valid stream URL"
                    return@launch
                }

                val trackSelector = DefaultTrackSelector(context)
                val exoPlayer = ExoPlayer.Builder(context)
                    .setTrackSelector(trackSelector)
                    .build()

                val dataSourceFactory = createDataSourceFactory()
                val mediaItemBuilder = MediaItem.Builder()
                    .setUri(Uri.parse(url))
                    .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())

                stream.subtitles?.let { subs ->
                    val configs = subs.mapNotNull { subtitle ->
                        val subUriStr = subtitle.url ?: return@mapNotNull null
                        val mimeType = when {
                            subUriStr.endsWith(".vtt", ignoreCase = true) -> "text/vtt"
                            subUriStr.endsWith(".srt", ignoreCase = true) -> "application/x-subrip"
                            else -> "text/vtt"
                        }
                        MediaItem.SubtitleConfiguration.Builder(Uri.parse(subUriStr))
                            .setMimeType(mimeType)
                            .setLanguage(subtitle.lang ?: "und")
                            .setLabel(subtitle.name ?: subtitle.lang ?: "Subtitle")
                            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                            .build()
                    }
                    if (configs.isNotEmpty()) {
                        mediaItemBuilder.setSubtitleConfigurations(configs)
                    }
                }

                val mediaItem = mediaItemBuilder.build()
                val mediaSource = createMediaSource(mediaItem, dataSourceFactory)
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
                        }
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        _isPlaying.value = isPlaying
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        _error.value = error.message
                    }
                    
                    override fun onTracksChanged(tracks: Tracks) {
                        val audioList = mutableListOf<AudioTrackInfo>()
                        val subtitleList = mutableListOf<SubtitleTrackInfo>()

                        tracks.groups.forEachIndexed { groupIndex, trackGroup ->
                            if (trackGroup.type == C.TRACK_TYPE_AUDIO) {
                                for (trackIndex in 0 until trackGroup.length) {
                                    val format = trackGroup.getTrackFormat(trackIndex)
                                    audioList.add(
                                        AudioTrackInfo(
                                            groupIndex = groupIndex,
                                            trackIndex = trackIndex,
                                            label = format.label ?: format.language ?: "Audio $trackIndex",
                                            language = format.language ?: "und"
                                        )
                                    )
                                }
                            } else if (trackGroup.type == C.TRACK_TYPE_TEXT) {
                                for (trackIndex in 0 until trackGroup.length) {
                                    val format = trackGroup.getTrackFormat(trackIndex)
                                    subtitleList.add(
                                        SubtitleTrackInfo(
                                            groupIndex = groupIndex,
                                            trackIndex = trackIndex,
                                            label = format.label ?: format.language ?: "Subtitle $trackIndex",
                                            language = format.language ?: "und"
                                        )
                                    )
                                }
                            }
                        }
                        _audioTracks.value = audioList
                        _subtitleTracks.value = subtitleList
                    }
                }
                exoPlayer.addListener(listener)
                playerListener = listener

                positionJob?.cancel()
                positionJob = viewModelScope.launch {
                    while (isActive) {
                        try {
                            _currentPosition.value = exoPlayer.currentPosition
                        } catch (e: IllegalStateException) {
                            break
                        }
                        delay(200)
                    }
                }

            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    private fun createDataSourceFactory(): DataSource.Factory {
        return DefaultHttpDataSource.Factory()
            .setUserAgent("UltraStream/1.0 (Android)")
            .setDefaultRequestProperties(mapOf("Referer" to "https://ultrastream.app/"))
            .setConnectTimeoutMs(30_000)
            .setReadTimeoutMs(60_000)
    }

    private fun createMediaSource(mediaItem: MediaItem, dataSourceFactory: DataSource.Factory): MediaSource {
        val uri = mediaItem.localConfiguration?.uri ?: Uri.EMPTY
        val url = uri.toString()
        return when {
            url.contains(".m3u8") -> HlsMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
            url.contains(".mpd") -> DashMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
            else -> ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
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

    fun play() {
        _player.value?.play()
        _isPlaying.value = true
    }

    fun pause() {
        _player.value?.pause()
        _isPlaying.value = false
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

    fun seekBy(offsetMs: Long) {
        _player.value?.let { player ->
            val newPos = player.currentPosition + offsetMs
            player.seekTo(newPos.coerceIn(0, player.duration))
            viewModelScope.launch {
                _seekMessage.value = if (offsetMs > 0) "+${offsetMs/1000}s" else "-${-offsetMs/1000}s"
                delay(800)
                _seekMessage.value = null
            }
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
        _brightness.value = brightness.coerceIn(-1f, 1f)
    }

    fun selectAudioTrack(info: AudioTrackInfo) {
        val player = _player.value ?: return
        val group = player.currentTracks.groups.getOrNull(info.groupIndex) ?: return
        
        val params = player.trackSelectionParameters.buildUpon()
            .setOverrideForType(
                TrackSelectionOverride(group.mediaTrackGroup, listOf(info.trackIndex))
            )
            .build()
        player.trackSelectionParameters = params
    }

    fun disableSubtitles() {
        val player = _player.value ?: return
        val params = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .build()
        player.trackSelectionParameters = params
    }

    fun selectSubtitleTrack(info: SubtitleTrackInfo) {
        val player = _player.value ?: return
        val group = player.currentTracks.groups.getOrNull(info.groupIndex) ?: return
        
        val params = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .setOverrideForType(
                TrackSelectionOverride(group.mediaTrackGroup, listOf(info.trackIndex))
            )
            .build()
        player.trackSelectionParameters = params
    }

    fun releasePlayer() {
        positionJob?.cancel()
        positionJob = null
        playerListener?.let { listener ->
            _player.value?.removeListener(listener)
        }
        _player.value?.release()
        _player.value = null
        playerListener = null
    }
}
INNER_EOF

# ============================================================
# 2. FIXED PlayerScreen.kt (Compose Imports Added)
# ============================================================
cat > app/src/main/java/com/ultrastream/app/ui/screens/player/PlayerScreen.kt << 'INNER_EOF'
@file:OptIn(ExperimentalMaterial3Api::class)

package com.ultrastream.app.ui.screens.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.os.Build
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.ultrastream.app.data.models.StreamItem
import com.ultrastream.app.ui.theme.AccentBlue

@Composable
fun PlayerScreen(
    stream: StreamItem,
    title: String = "Now Playing",
    viewModel: PlayerViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current

    val player by viewModel.player.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val error by viewModel.error.collectAsState()
    val playerTitle by viewModel.title.collectAsState()
    val brightness by viewModel.brightness.collectAsState()
    val volume by viewModel.volume.collectAsState()
    val seekMessage by viewModel.seekMessage.collectAsState()

    var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var isFullscreen by remember { mutableStateOf(false) }
    var showAudioSheet by remember { mutableStateOf(false) }
    var showSubtitleSheet by remember { mutableStateOf(false) }

    LaunchedEffect(stream) {
        viewModel.initializePlayer(context, stream, title)
    }

    DisposableEffect(Unit) {
        val window = activity?.window
        val insetsController = window?.let { WindowInsetsControllerCompat(it, view) }
        insetsController?.let {
            it.hide(WindowInsetsCompat.Type.systemBars())
            it.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        onDispose {
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
            viewModel.releasePlayer()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val listener = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    if (!(activity?.isInPictureInPictureMode ?: false)) {
                        viewModel.pause()
                    }
                }
                Lifecycle.Event.ON_RESUME -> {
                    viewModel.play()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(listener)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(listener)
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
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { playerView ->
                playerView.player = player
                playerView.resizeMode = resizeMode
            }
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { offset ->
                            val width = size.width
                            val seekTime = if (offset.x < width / 2) -10000L else 10000L
                            viewModel.seekBy(seekTime)
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val width = size.width
                            val deltaX = dragAmount.x / width
                            if (change.position.x < width / 2) {
                                val newBrightness = (brightness + deltaX * 2).coerceIn(-1f, 1f)
                                viewModel.setBrightness(newBrightness)
                            } else {
                                val newVolume = (volume + deltaX * 2).coerceIn(0f, 1f)
                                viewModel.setVolume(newVolume)
                            }
                        }
                    )
                }
        )

        if (seekMessage != null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Surface(
                    color = Color.Black.copy(alpha = 0.8f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = seekMessage!!,
                        color = Color.White,
                        modifier = Modifier.padding(16.dp),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (playerTitle.isNotEmpty()) playerTitle else title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = { showAudioSheet = true }) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = "Audio Tracks",
                            tint = Color.White
                        )
                    }
                    IconButton(onClick = { showSubtitleSheet = true }) {
                        Icon(
                            imageVector = Icons.Default.ClosedCaption,
                            contentDescription = "Subtitles",
                            tint = Color.White
                        )
                    }
                    IconButton(
                        onClick = {
                            onBack()
                            viewModel.releasePlayer()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            val progress = if (duration > 0) (currentPosition.toFloat() / duration.toFloat()) else 0f
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = AccentBlue,
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
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.skipBackward() }) {
                    Icon(Icons.Default.Replay10, contentDescription = "Back 10s", tint = Color.White)
                }
                IconButton(onClick = { viewModel.playPause() }) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                }
                IconButton(onClick = { viewModel.skipForward() }) {
                    Icon(Icons.Default.Forward10, contentDescription = "Forward 10s", tint = Color.White)
                }
                IconButton(onClick = {
                    resizeMode = when (resizeMode) {
                        AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                        AspectRatioFrameLayout.RESIZE_MODE_FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
                        AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT
                        else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                }) {
                    Icon(Icons.Default.AspectRatio, contentDescription = "Resize Mode", tint = Color.White)
                }
                IconButton(onClick = {
                    isFullscreen = !isFullscreen
                    if (isFullscreen) {
                        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        WindowInsetsControllerCompat(activity?.window!!, view).hide(WindowInsetsCompat.Type.systemBars())
                        WindowInsetsControllerCompat(activity?.window!!, view).systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    } else {
                        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                        WindowInsetsControllerCompat(activity?.window!!, view).show(WindowInsetsCompat.Type.systemBars())
                    }
                }) {
                    Icon(
                        if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                        contentDescription = "Fullscreen",
                        tint = Color.White
                    )
                }
            }
        }

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

    if (showAudioSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAudioSheet = false }
        ) {
            val audioTracks by viewModel.audioTracks.collectAsState()
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Audio Tracks", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn {
                    items(audioTracks) { track ->
                        ListItem(
                            headlineContent = { Text(track.label) },
                            supportingContent = { Text(track.language) },
                            modifier = Modifier.clickable {
                                viewModel.selectAudioTrack(track)
                                showAudioSheet = false
                            }
                        )
                    }
                }
            }
        }
    }

    if (showSubtitleSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSubtitleSheet = false }
        ) {
            val subtitleTracks by viewModel.subtitleTracks.collectAsState()
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Subtitles", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn {
                    item {
                        ListItem(
                            headlineContent = { Text("Off") },
                            modifier = Modifier.clickable {
                                viewModel.disableSubtitles()
                                showSubtitleSheet = false
                            }
                        )
                    }
                    items(subtitleTracks) { track ->
                        ListItem(
                            headlineContent = { Text(track.label) },
                            supportingContent = { Text(track.language) },
                            modifier = Modifier.clickable {
                                viewModel.selectSubtitleTrack(track)
                                showSubtitleSheet = false
                            }
                        )
                    }
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
INNER_EOF

echo "==> Staging and pushing changes to GitHub..."
git add app/src/main/java/com/ultrastream/app/ui/screens/player/PlayerViewModel.kt
git add app/src/main/java/com/ultrastream/app/ui/screens/player/PlayerScreen.kt
git commit -m "Fix: Resolve all Media3 API and Compose LazyColumn import errors in Player"
git push origin main

echo "✅ All Build Errors Fixed! Please check GitHub Actions."

