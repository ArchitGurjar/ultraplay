package com.ultrastream.app.utils

import com.ultrastream.app.data.models.StreamItem

/**
 * Holds the current playback context to pass data between screens.
 * Used for auto-play next, quality/language selection, and player initialization.
 */
object PlayerContext {
    // Current stream and metadata
    var stream: StreamItem? = null
    var title: String = ""
    var metaId: String = ""
    var metaType: String = ""
    var season: Int? = null
    var episode: Int? = null
    var playlistId: String? = null

    // Quality and language preferences – these are set dynamically
    // Empty initial value; will be set from the selected stream's quality
    var currentQuality: String = ""
    var currentLanguage: String = "Hindi"

    fun clear() {
        stream = null
        title = ""
        metaId = ""
        metaType = ""
        season = null
        episode = null
        playlistId = null
        currentQuality = ""
        currentLanguage = "Hindi"
    }
}