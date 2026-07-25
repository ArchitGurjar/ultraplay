package com.ultrastream.app.utils

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UrlClassifier @Inject constructor() {

    fun classify(url: String): UrlType {
        if (url.isBlank()) return UrlType.INVALID
        val lower = url.lowercase()
        return when {
            lower.startsWith("magnet:") -> UrlType.MAGNET
            lower.contains(".m3u8") || lower.contains(".m3u") -> UrlType.HLS
            lower.contains(".mpd") -> UrlType.DASH
            lower.contains("pengu.uk") || lower.contains("streamraiwind") || lower.contains("cdn") || lower.contains("proxy") -> UrlType.PROXY
            lower.matches(Regex(".*\\.(mp4|mkv|avi|mov|wmv|flv|webm)$")) -> UrlType.DIRECT
            else -> UrlType.UNKNOWN
        }
    }

    enum class UrlType {
        HLS, DASH, PROXY, DIRECT, MAGNET, UNKNOWN, INVALID
    }
}

