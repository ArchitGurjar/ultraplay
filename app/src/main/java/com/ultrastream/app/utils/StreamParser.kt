package com.ultrastream.app.utils

import com.ultrastream.app.data.models.StreamItem
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StreamParser @Inject constructor() {

    fun isValidEpisode(stream: StreamItem, targetSeason: Int, targetEpisode: Int): Boolean {
        val text = buildString {
            append(stream.title ?: "")
            append(" ")
            append(stream.name ?: "")
            append(" ")
            append(stream.description ?: "")
        }.uppercase()

        // Check for explicit episode patterns
        var hasExplicit = false
        var matchFound = false

        // EP, E, EPISODE
        val epRegex = Regex("(?:^|[^A-Z])(?:E|EP|EPISODE)[-\\s_]*(\\d{1,4})(?:[^A-Z]|$)")
        epRegex.findAll(text).forEach {
            hasExplicit = true
            if (it.groupValues[1].toIntOrNull() == targetEpisode) {
                matchFound = true
            }
        }

        // S01E01 format
        val sxeRegex = Regex("S(\\d{1,2})[-\\s_]*E(\\d{1,4})")
        sxeRegex.findAll(text).forEach {
            hasExplicit = true
            val s = it.groupValues[1].toIntOrNull()
            val e = it.groupValues[2].toIntOrNull()
            if (s == targetSeason && e == targetEpisode) {
                matchFound = true
            }
        }

        // 1x01 format
        val axbRegex = Regex("(?:^|[^A-Z0-9])(\\d{1,2})x(\\d{1,4})(?:[^A-Z0-9]|$)")
        axbRegex.findAll(text).forEach {
            // avoid resolutions like 1920x1080
            if (it.groupValues[1].toIntOrNull()?.let { num -> num < 100 } == true) {
                hasExplicit = true
                val s = it.groupValues[1].toIntOrNull()
                val e = it.groupValues[2].toIntOrNull()
                if (s == targetSeason && e == targetEpisode) {
                    matchFound = true
                }
            }
        }

        // If explicit episode marker found but no match, reject
        if (hasExplicit && !matchFound) return false

        // Isolated numbers (fallback)
        if (!hasExplicit) {
            val isoRegex = Regex("(?:^|[\\s\\-_\\[\\]])(\\d{1,4})(?:[\\s\\-_\\[\\]]|$)")
            var foundAny = false
            var isoMatch = false
            isoRegex.findAll(text).forEach {
                val num = it.groupValues[1].toIntOrNull() ?: return@forEach
                // Ignore technical numbers
                if (num in listOf(720, 1080, 2160, 480, 264, 265, 10)) return@forEach
                if (num in 1900..2100) return@forEach // year
                foundAny = true
                if (num == targetEpisode) {
                    isoMatch = true
                }
            }
            if (foundAny && !isoMatch) return false
        }

        return true
    }

    fun sortStreams(streams: List<StreamItem>, hindiPriority: Boolean): List<StreamItem> {
        return streams.sortedWith { a, b ->
            val textA = (a.title ?: "") + (a.name ?: "") + (a.description ?: "")
            val textB = (b.title ?: "") + (b.name ?: "") + (b.description ?: "")
            val hindiRegex = Regex("hindi|hin|हिंदी|हिन्दी|dual audio.*hindi|multi audio.*hindi", RegexOption.IGNORE_CASE)
            val hasHindiA = hindiRegex.containsMatchIn(textA)
            val hasHindiB = hindiRegex.containsMatchIn(textB)

            if (hindiPriority) {
                if (hasHindiA && !hasHindiB) return@sortedWith -1
                if (!hasHindiA && hasHindiB) return@sortedWith 1
            }

            // Quality score
            val qualRegex = Regex("4k|2160p|1080p|720p|hdr|dolby", RegexOption.IGNORE_CASE)
            val qualA = qualRegex.findAll(textA).count()
            val qualB = qualRegex.findAll(textB).count()
            qualB.compareTo(qualA) // higher quality first
        }
    }

    fun parseMetadata(rawText: String): ParsedInfo {
        val sizeMatch = Regex("\\b(\\d+(?:\\.\\d+)?)\\s*(GB|MB)\\b", RegexOption.IGNORE_CASE).find(rawText)
        val size = sizeMatch?.value?.uppercase()
        val sizeValueBytes = sizeMatch?.let {
            val value = it.groupValues[1].toDoubleOrNull() ?: 0.0
            val unit = it.groupValues[2].uppercase()
            when (unit) {
                "GB" -> (value * 1024 * 1024 * 1024).toLong()
                "MB" -> (value * 1024 * 1024).toLong()
                else -> null
            }
        }
        val seedMatch = Regex("(?:seeders|seeds|s)[:\\s]*(\\d+)", RegexOption.IGNORE_CASE).find(rawText)
        val seeds = seedMatch?.groupValues?.get(1)
        val langMatch = Regex("hindi|english|tamil|telugu|malayalam|bengali|dual audio|multi audio|हिंदी|हिन्दी", RegexOption.IGNORE_CASE)
            .findAll(rawText)
            .map { it.value }
            .toSet()
        val langs = langMatch.map { it.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } }.toList()
        val qualMatch = Regex("4K|2160p|1080p|720p|480p|HDR|DV|CAM|HDTS|HDTC", RegexOption.IGNORE_CASE)
            .findAll(rawText)
            .map { it.value.uppercase() }
            .toSet()
        val quals = qualMatch.toList()
        val isLive = Regex("live|iptv|stream", RegexOption.IGNORE_CASE).containsMatchIn(rawText) && size == null && seeds == null
        val hasHindi = langs.any { it.contains("hindi", ignoreCase = true) }

        val yearMatch = Regex("\\b(19\\d{2}|20[0-2]\\d)\\b").find(rawText)
        val parsedYear = yearMatch?.value

        var parsedSeason: Int? = null
        var parsedEpisode: Int? = null

        val sxeMatch = Regex("\\b(\\d{1,2})x(\\d{1,4})\\b", RegexOption.IGNORE_CASE).find(rawText)
        if (sxeMatch != null && sxeMatch.groupValues[1].toIntOrNull()?.let { it < 100 } == true) {
            parsedSeason = sxeMatch.groupValues[1].toIntOrNull()
            parsedEpisode = sxeMatch.groupValues[2].toIntOrNull()
        } else {
            val seasonMatch = Regex("(?:^|[^A-Z])(?:S|SEASON)[-\\s_]*(\\d{1,2})\\b", RegexOption.IGNORE_CASE).find(rawText)
            parsedSeason = seasonMatch?.groupValues?.get(1)?.toIntOrNull()
            val episodeMatch = Regex("(?:^|[^A-Z])(?:E|EP|EPISODE)[-\\s_]*(\\d{1,4})\\b", RegexOption.IGNORE_CASE).find(rawText)
            parsedEpisode = episodeMatch?.groupValues?.get(1)?.toIntOrNull()
        }

        val cleanText = rawText
            .replace(Regex("\\b(\\d+(?:\\.\\d+)?\\s*(?:GB|MB))\\b", RegexOption.IGNORE_CASE), "")
            .replace(Regex("(?:seeders|seeds|s)[:\\s]*(\\d+)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\b(hindi|english|tamil|telugu|malayalam|bengali|dual audio|multi audio|हिंदी|हिन्दी)\\b", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\b(4K|2160p|1080p|720p|480p|HDR|DV|CAM|HDTS|HDTC)\\b", RegexOption.IGNORE_CASE), "")
            .replace(Regex("[\\u{1F300}-\\u{1F9FF}]", RegexOption.IGNORE_CASE), "")
            .replace(Regex("[\\u{2600}-\\u{26FF}]", RegexOption.IGNORE_CASE), "")
            .trim()
        return ParsedInfo(
            size = size,
            sizeValueBytes = sizeValueBytes,
            seeds = seeds,
            langs = langs,
            quals = quals,
            isLive = isLive,
            hasHindi = hasHindi,
            cleanText = cleanText.ifEmpty { "Direct Video Stream" },
            parsedYear = parsedYear,
            parsedSeason = parsedSeason,
            parsedEpisode = parsedEpisode
        )
    }

    data class ParsedInfo(
        val size: String?,
        val sizeValueBytes: Long?,
        val seeds: String?,
        val langs: List<String>,
        val quals: List<String>,
        val isLive: Boolean,
        val hasHindi: Boolean,
        val cleanText: String,
        val parsedYear: String?,
        val parsedSeason: Int?,
        val parsedEpisode: Int?
    )
}

