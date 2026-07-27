package com.ultrastream.app.data.models

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class FullBackupData(
    val addons: List<Addon>,
    val library: List<LibraryItem>,
    val watchlist: List<WatchlistItem>,
    val history: List<HistoryItem>,
    val progress: List<WatchProgress>,
    val watchedEpisodes: List<WatchedEpisode>,
    val playlists: List<SmartPlaylist>,
    val profiles: List<Profile>,
    val cachedMeta: List<CachedMeta> = emptyList(),
    val settings: SettingsBackup
)

@JsonClass(generateAdapter = true)
data class SettingsBackup(
    val theme: String,
    val hindiPriority: Boolean,
    val autoPlayNext: Boolean,
    val parentalControl: Boolean,
    val parentalRating: String,
    val subtitleLanguage: String,
    val debridKey: String,
    val debridProvider: String
)
