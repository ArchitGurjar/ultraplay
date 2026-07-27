package com.ultrastream.app.utils

import com.ultrastream.app.data.models.StreamItem

data class NextEpisodeInfo(
    val metaId: String,
    val type: String,
    val season: Int,
    val episode: Int,
    val title: String
)

object StreamDataHolder {
    var currentStreams: List<StreamItem> = emptyList()
    var currentStream: StreamItem? = null
    var nextEpisode: NextEpisodeInfo? = null

    fun setStream(stream: StreamItem, next: NextEpisodeInfo? = null) {
        currentStreams = listOf(stream)
        currentStream = stream
        nextEpisode = next
    }

    fun setStreams(streams: List<StreamItem>, title: String) {
        currentStreams = streams
        currentStream = streams.firstOrNull()
        nextEpisode = null
    }

    fun clear() {
        currentStreams = emptyList()
        currentStream = null
        nextEpisode = null
    }
}

