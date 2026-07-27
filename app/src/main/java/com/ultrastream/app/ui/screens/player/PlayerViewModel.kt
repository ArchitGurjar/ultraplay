package com.ultrastream.app.ui.screens.player

import android.content.Context
import android.media.AudioManager
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.session.MediaSession
import com.ultrastream.app.data.models.StreamItem
import com.ultrastream.app.data.models.Subtitle
import com.ultrastream.app.data.preferences.PreferencesManager
import com.ultrastream.app.data.repository.MetaRepository
import com.ultrastream.app.domain.usecase.GetStreamsUseCase
import com.ultrastream.app.utils.LinkVerifier
import com.ultrastream.app.utils.PlayerContext
import com.ultrastream.app.utils.StreamParser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AudioTrackInfo(val groupIndex: Int, val trackIndex: Int, val label: String, val language: String)
data class SubtitleTrackInfo(val groupIndex: Int, val trackIndex: Int, val label: String, val language: String)
data class Quality(val label: String, val resolution: String?, val bitrate: Int?)

sealed class PlayerEvent {
    object PlaybackEnded : PlayerEvent()
}

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager,
    private val metaRepository: MetaRepository,
    private val getStreamsUseCase: GetStreamsUseCase,
    private val linkVerifier: LinkVerifier
) : ViewModel() {

    private val _player = MutableStateFlow<ExoPlayer?>(null)
    val player: StateFlow<ExoPlayer?> = _player.asStateFlow()

    private val _events = MutableSharedFlow<PlayerEvent>()
    val events: SharedFlow<PlayerEvent> = _events.asSharedFlow()

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

    private val _availableQualities = MutableStateFlow<List<Quality>>(emptyList())
    val availableQualities: StateFlow<List<Quality>> = _availableQualities.asStateFlow()

    private val _seekMessage = MutableStateFlow<String?>(null)
    val seekMessage: StateFlow<String?> = _seekMessage.asStateFlow()

    private val _isLocked = MutableStateFlow(false)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    private val _isFullscreen = MutableStateFlow(false)
    val isFullscreen: StateFlow<Boolean> = _isFullscreen.asStateFlow()

    // New states for quality/language
    private val _isLoadingStreams = MutableStateFlow(false)
    val isLoadingStreams: StateFlow<Boolean> = _isLoadingStreams.asStateFlow()

    private val _availableLanguages = MutableStateFlow<List<String>>(emptyList())
    val availableLanguages: StateFlow<List<String>> = _availableLanguages.asStateFlow()

    private val _availableQualitiesFromStreams = MutableStateFlow<List<String>>(emptyList())
    val availableQualitiesFromStreams: StateFlow<List<String>> = _availableQualitiesFromStreams.asStateFlow()

    private val _currentQuality = MutableStateFlow(PlayerContext.currentQuality)
    val currentQuality: StateFlow<String> = _currentQuality.asStateFlow()

    private val _currentLanguage = MutableStateFlow(PlayerContext.currentLanguage)
    val currentLanguage: StateFlow<String> = _currentLanguage.asStateFlow()

    // Internal storage
    private var lastStreams: List<StreamItem> = emptyList()
    private var lastTitle: String = ""
    private var lastContext: Context? = null
    private var lastSubtitle: Subtitle? = null
    private var currentMetaId: String = ""
    private var currentMetaType: String = ""
    private var currentSeason: Int? = null
    private var currentEpisode: Int? = null
    private var nextEpisodePreFetched: StreamItem? = null
    private var preFetchJob: Job? = null

    private var playerListener: Player.Listener? = null
    private var positionJob: Job? = null
    private var mediaSession: MediaSession? = null
    private var nextEpisodeListener: ((StreamItem, String) -> Unit)? = null

    private val qualityOrder = listOf("2160p", "1080p", "720p", "480p", "360p")
    private val parser = StreamParser()

    fun setNextEpisodeListener(listener: (StreamItem, String) -> Unit) {
        nextEpisodeListener = listener
    }

    suspend fun initializePlayer(
        context: Context,
        streams: List<StreamItem>,
        title: String,
        externalSubtitle: Subtitle? = null,
        metaId: String? = null,
        metaType: String? = null,
        season: Int? = null,
        episode: Int? = null
    ) {
        lastContext = context
        lastStreams = streams
        lastTitle = title
        lastSubtitle = externalSubtitle
        currentMetaId = metaId ?: PlayerContext.metaId
        currentMetaType = metaType ?: PlayerContext.metaType
        currentSeason = season ?: PlayerContext.season
        currentEpisode = episode ?: PlayerContext.episode

        // Set initial quality/language from context (if not already set)
        if (_currentQuality.value.isBlank()) {
            // Try to extract quality from first stream
            val firstStream = streams.firstOrNull()
            if (firstStream != null) {
                val quality = parser.extractQuality(firstStream)
                if (quality != null) {
                    _currentQuality.value = quality
                    PlayerContext.currentQuality = quality
                }
            }
        }
        if (_currentLanguage.value.isBlank()) {
            _currentLanguage.value = "Hindi"
            PlayerContext.currentLanguage = "Hindi"
        }

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        _volume.value = if (maxVol > 0) currentVol.toFloat() / maxVol.toFloat() else 0.5f

        try {
            if (streams.isEmpty()) {
                _error.value = "No streams to play"
                return
            }

            // Verify and filter streams
            val verifiedStreams = verifyStreams(streams)
            if (verifiedStreams.isEmpty()) {
                _error.value = "No working streams found. Please try another source."
                return
            }

            // Extract available qualities from verified streams
            val qualities = extractQualities(verifiedStreams)
            _availableQualitiesFromStreams.value = qualities

            // Extract languages
            extractLanguages(verifiedStreams)

            // Build player with verified streams
            buildPlayer(context, verifiedStreams, title, externalSubtitle)

            // Pre‑fetch next episode if available
            preFetchNextEpisode()

        } catch (e: Exception) {
            _error.value = e.message
        }
    }

    private suspend fun verifyStreams(streams: List<StreamItem>): List<StreamItem> {
        val verified = mutableListOf<StreamItem>()
        for (stream in streams) {
            val url = stream.url ?: stream.streamUrl ?: stream.externalUrl
            if (url.isNullOrBlank()) continue
            if (url.startsWith("magnet:", ignoreCase = true)) {
                verified.add(stream)
                continue
            }
            if (linkVerifier.verifyLink(url)) {
                verified.add(stream)
            }
        }
        return verified
    }

    private fun extractQualities(streams: List<StreamItem>): List<String> {
        val qualitySet = mutableSetOf<String>()
        streams.forEach { stream ->
            val text = buildString {
                append(stream.title ?: "")
                append(" ")
                append(stream.name ?: "")
                append(" ")
                append(stream.description ?: "")
            }
            val parsed = parser.parseMetadata(text)
            qualitySet.addAll(parsed.quals)
        }
        return qualitySet.sortedWith(compareBy { 
            qualityOrder.indexOf(it)
        }).filter { it.isNotEmpty() }
    }

    private suspend fun buildPlayer(
        context: Context,
        streams: List<StreamItem>,
        title: String,
        externalSubtitle: Subtitle?
    ) {
        val trackSelector = DefaultTrackSelector(context)
        val dataSourceFactory = createDataSourceFactory()
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        val exoPlayer = ExoPlayer.Builder(context)
            .setTrackSelector(trackSelector)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()

        val mediaItems = streams.map { stream ->
            val url = stream.url ?: stream.streamUrl ?: stream.externalUrl ?: ""
            val mediaItemBuilder = MediaItem.Builder()
                .setUri(Uri.parse(url))
                .setMediaMetadata(MediaMetadata.Builder().setTitle(stream.title ?: title).build())

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

            if (externalSubtitle != null && !externalSubtitle.url.isNullOrBlank()) {
                val mimeType = when {
                    externalSubtitle.url.endsWith(".vtt", ignoreCase = true) -> "text/vtt"
                    externalSubtitle.url.endsWith(".srt", ignoreCase = true) -> "application/x-subrip"
                    else -> "text/vtt"
                }
                val config = MediaItem.SubtitleConfiguration.Builder(Uri.parse(externalSubtitle.url))
                    .setMimeType(mimeType)
                    .setLanguage(externalSubtitle.lang ?: "und")
                    .setLabel(externalSubtitle.name ?: externalSubtitle.lang ?: "External Subtitle")
                    .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                    .build()
                mediaItemBuilder.setSubtitleConfigurations(listOf(config))
            }
            mediaItemBuilder.build()
        }

        exoPlayer.setMediaItems(mediaItems)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true

        mediaSession = MediaSession.Builder(context, exoPlayer).build()

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
                        viewModelScope.launch {
                            if (preferencesManager.getAutoPlayNext().firstOrNull() == true) {
                                playNextEpisode()
                                _events.emit(PlayerEvent.PlaybackEnded)
                            }
                        }
                    }
                }
            }

            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                _title.value = mediaMetadata.title?.toString() ?: title
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
                val qualityList = mutableListOf<Quality>()

                tracks.groups.forEachIndexed { groupIndex, trackGroup ->
                    when (trackGroup.type) {
                        C.TRACK_TYPE_AUDIO -> {
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
                        }
                        C.TRACK_TYPE_TEXT -> {
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
                        C.TRACK_TYPE_VIDEO -> {
                            for (trackIndex in 0 until trackGroup.length) {
                                val format = trackGroup.getTrackFormat(trackIndex)
                                val resolution = if (format.height != C.LENGTH_UNSET) {
                                    "${format.height}p"
                                } else null
                                qualityList.add(
                                    Quality(
                                        label = format.label ?: "Quality",
                                        resolution = resolution,
                                        bitrate = format.bitrate
                                    )
                                )
                            }
                        }
                    }
                }
                _audioTracks.value = audioList
                _subtitleTracks.value = subtitleList
                _availableQualities.value = qualityList
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
    }

    private fun createDataSourceFactory(): DataSource.Factory {
        return DefaultHttpDataSource.Factory()
            .setUserAgent("UltraStream/1.0 (Android)")
            .setDefaultRequestProperties(mapOf("Referer" to "https://ultrastream.app/"))
            .setConnectTimeoutMs(30_000)
            .setReadTimeoutMs(60_000)
    }

    // ── Quality / Language Switching ──

    fun changeQuality(quality: String) {
        viewModelScope.launch {
            if (_isLoadingStreams.value) return@launch
            _isLoadingStreams.value = true
            _error.value = null
            try {
                val streams = fetchStreamsForCurrentEpisode(quality, _currentLanguage.value)
                if (streams.isNotEmpty()) {
                    val verified = verifyStreams(streams)
                    if (verified.isNotEmpty()) {
                        _currentQuality.value = quality
                        PlayerContext.currentQuality = quality
                        val context = lastContext ?: return@launch
                        // Update available languages based on new streams
                        extractLanguages(verified)
                        // Update available qualities
                        _availableQualitiesFromStreams.value = extractQualities(verified)
                        buildPlayer(context, verified, lastTitle, lastSubtitle)
                        _isLoadingStreams.value = false
                        return@launch
                    }
                }
                // If we reach here, quality not found – try fallback
                val fallbackQuality = getFallbackQuality(quality)
                if (fallbackQuality != null) {
                    changeQuality(fallbackQuality) // Recursive fallback
                } else {
                    _error.value = "Quality $quality not available. No fallback found."
                    _isLoadingStreams.value = false
                }
            } catch (e: Exception) {
                _error.value = "Failed to change quality: ${e.message}"
                _isLoadingStreams.value = false
            }
        }
    }

    fun changeLanguage(language: String) {
        viewModelScope.launch {
            if (_isLoadingStreams.value) return@launch
            _isLoadingStreams.value = true
            _error.value = null
            try {
                val streams = fetchStreamsForCurrentEpisode(_currentQuality.value, language)
                if (streams.isNotEmpty()) {
                    val verified = verifyStreams(streams)
                    if (verified.isNotEmpty()) {
                        _currentLanguage.value = language
                        PlayerContext.currentLanguage = language
                        val context = lastContext ?: return@launch
                        // Update available languages based on new streams
                        extractLanguages(verified)
                        // Update available qualities
                        _availableQualitiesFromStreams.value = extractQualities(verified)
                        buildPlayer(context, verified, lastTitle, lastSubtitle)
                        _isLoadingStreams.value = false
                        return@launch
                    }
                }
                _error.value = "Language $language not available for current quality."
                _isLoadingStreams.value = false
            } catch (e: Exception) {
                _error.value = "Failed to change language: ${e.message}"
                _isLoadingStreams.value = false
            }
        }
    }

    private suspend fun fetchStreamsForCurrentEpisode(quality: String, language: String): List<StreamItem> {
        if (currentMetaId.isBlank() || currentSeason == null || currentEpisode == null) {
            return emptyList()
        }
        return getStreamsUseCase(
            metaId = currentMetaId,
            metaType = currentMetaType,
            season = currentSeason,
            episode = currentEpisode,
            quality = quality,
            language = language
        )
    }

    private fun getFallbackQuality(quality: String): String? {
        val index = qualityOrder.indexOf(quality)
        if (index < 0) return qualityOrder.lastOrNull()
        // Try next lower quality
        val nextIndex = index + 1
        return if (nextIndex < qualityOrder.size) qualityOrder[nextIndex] else null
    }

    private fun extractLanguages(streams: List<StreamItem>) {
        val langSet = mutableSetOf<String>()
        streams.forEach { stream ->
            val text = buildString {
                append(stream.title ?: "")
                append(" ")
                append(stream.name ?: "")
                append(" ")
                append(stream.description ?: "")
            }
            val parsed = parser.parseMetadata(text)
            langSet.addAll(parsed.langs)
            if (parsed.hasHindi) langSet.add("Hindi")
        }
        // Prefer Hindi first, then English, then others
        val sorted = langSet.sortedWith(compareBy<String> { 
            when (it.lowercase()) {
                "hindi" -> 0
                "english" -> 1
                else -> 2
            }
        }.thenBy { it })
        _availableLanguages.value = sorted
        // If current language is not in available list, switch to first available
        if (_currentLanguage.value !in sorted && sorted.isNotEmpty()) {
            _currentLanguage.value = sorted.first()
            PlayerContext.currentLanguage = sorted.first()
        }
    }

    // ── Auto‑play Next (Pre‑fetch) ──

    private fun preFetchNextEpisode() {
        if (currentMetaId.isBlank() || currentSeason == null || currentEpisode == null) return
        val nextEpNum = currentEpisode!! + 1
        preFetchJob?.cancel()
        preFetchJob = viewModelScope.launch {
            try {
                val streams = getStreamsUseCase(
                    metaId = currentMetaId,
                    metaType = currentMetaType,
                    season = currentSeason,
                    episode = nextEpNum,
                    quality = _currentQuality.value,
                    language = _currentLanguage.value
                )
                if (streams.isNotEmpty()) {
                    val verified = verifyStreams(streams)
                    if (verified.isNotEmpty()) {
                        nextEpisodePreFetched = verified.firstOrNull()
                    }
                }
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    private fun playNextEpisode() {
        val nextStream = nextEpisodePreFetched
        if (nextStream != null) {
            val nextTitle = "${lastTitle} - Next Episode"
            nextEpisodeListener?.invoke(nextStream, nextTitle)
        } else {
            // Fallback: fetch on demand
            viewModelScope.launch {
                if (currentMetaId.isBlank() || currentSeason == null || currentEpisode == null) return@launch
                val nextEpNum = currentEpisode!! + 1
                val streams = getStreamsUseCase(
                    metaId = currentMetaId,
                    metaType = currentMetaType,
                    season = currentSeason,
                    episode = nextEpNum,
                    quality = _currentQuality.value,
                    language = _currentLanguage.value
                )
                if (streams.isNotEmpty()) {
                    val verified = verifyStreams(streams)
                    if (verified.isNotEmpty()) {
                        val nextTitle = "${lastTitle} - Next Episode"
                        nextEpisodeListener?.invoke(verified.first(), nextTitle)
                    }
                }
            }
        }
    }

    // ── Public controls ──

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

    fun toggleLock() {
        _isLocked.value = !_isLocked.value
    }

    fun toggleFullscreen() {
        _isFullscreen.value = !_isFullscreen.value
    }

    fun retryPlayback() {
        viewModelScope.launch {
            _error.value = null
            val context = lastContext ?: return@launch
            if (lastStreams.isNotEmpty()) {
                initializePlayer(context, lastStreams, lastTitle, lastSubtitle, currentMetaId, currentMetaType, currentSeason, currentEpisode)
            } else {
                _error.value = "No streams to retry"
            }
        }
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
        mediaSession?.release()
        mediaSession = null
        preFetchJob?.cancel()
        preFetchJob = null
    }
}