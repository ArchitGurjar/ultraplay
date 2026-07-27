package com.ultrastream.app.utils


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