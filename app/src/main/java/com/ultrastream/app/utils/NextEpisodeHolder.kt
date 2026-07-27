package com.ultrastream.app.utils

/**
 * Holds information about the next episode to be played automatically
 * when the current episode ends.
 */
data class NextEpisodeInfo(
    val metaId: String,
    val type: String,
    val season: Int,
    val episode: Int,
    val title: String
)

object NextEpisodeHolder {
    private var _nextEpisode: NextEpisodeInfo? = null

    fun set(next: NextEpisodeInfo?) {
        _nextEpisode = next
    }

    fun clear() {
        _nextEpisode = null
    }

    fun consume(): NextEpisodeInfo? {
        val episode = _nextEpisode
        clear()
        return episode
    }

    fun peek(): NextEpisodeInfo? = _nextEpisode
}