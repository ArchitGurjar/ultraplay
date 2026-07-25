package com.ultrastream.app.data.models

data class Episode(
    val season: Int,
    val episode: Int,
    val name: String?,
    val title: String?,
    val description: String?,
    val thumbnail: String?
)

