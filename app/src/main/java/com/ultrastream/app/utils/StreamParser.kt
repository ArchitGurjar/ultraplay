package com.ultrastream.app.utils

import com.ultrastream.app.data.models.StreamItem
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StreamParser @Inject constructor() {

    // ─── Episode Validation ───────────────────────────────────────────

    /**
     * Strictly checks if a stream matches the given season and episode.
     * - Supports: S01E01, 1x01, EP 1, Episode 1
     * - Ignores technical numbers (720, 1080, 264, 265, etc.)
     * - Filters out season packs unless they explicitly contain the target episode
     */
    fun isValidEpisode(text: String, targetSeason: Int, targetEpisode: Int): Boolean {
        val upper = text.uppercase()
        var hasExplicit = false
        var matchFound = false

        // 1. EP, E, EPISODE patterns
        val epRegex = Regex("(?:^|[^A-Z])(?:E|EP|EPISODE)[-\\s_]*(\\d{1,4})(?:[^A-Z]|$)")
        epRegex.findAll(upper).forEach {
            hasExplicit = true
            if (it.groupValues[1].toIntOrNull() == targetEpisode) matchFound = true
        }

        // 2. S01E01 patterns
        val sxeRegex = Regex("S(\\d{1,2})[-\\s_]*E(\\d{1,4})")
        sxeRegex.findAll(upper).forEach {
            hasExplicit = true
            val s = it.groupValues[1].toIntOrNull()
            val e = it.groupValues[2].toIntOrNull()
            if (s == targetSeason && e == targetEpisode) matchFound = true
        }

        // 3. 1x01 patterns (avoid 1920x1080)
        val axbRegex = Regex("(?:^|[^A-Z0-9])(\\d{1,2})x(\\d{1,4})(?:[^A-Z0-9]|$)")
        axbRegex.findAll(upper).forEach {
            if (it.groupValues[1].toIntOrNull()?.let { num -> num < 100 } == true) {
                hasExplicit = true
                val s = it.groupValues[1].toIntOrNull()
                val e = it.groupValues[2].toIntOrNull()
                if (s == targetSeason && e == targetEpisode) matchFound = true
            }
        }

        // If we found explicit markers, enforce match
        if (hasExplicit && !matchFound) return false

        // 4. Fallback: isolated numbers (e.g., "Show - 05") – only if no explicit pattern found
        if (!hasExplicit) {
            val isoRegex = Regex("(?:^|[\\s\\-_\\[\\]])(\\d{1,4})(?:[\\s\\-_\\[\\]]|$)")
            var foundAny = false
            var isoMatch = false
            isoRegex.findAll(upper).forEach {
                val num = it.groupValues[1].toIntOrNull() ?: return@forEach
                // Skip technical numbers
                if (num in listOf(720, 1080, 2160, 480, 264, 265, 10)) return@forEach
                if (num in 1900..2100) return@forEach // years
                foundAny = true
                if (num == targetEpisode) isoMatch = true
            }
            if (foundAny && !isoMatch) return false
        }

        return true
    }

    // ─── Sorting ──────────────────────────────────────────────────────

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

            val qualRegex = Regex("4k|2160p|1080p|720p|hdr|dolby", RegexOption.IGNORE_CASE)
            val qualA = qualRegex.findAll(textA).count()
            val qualB = qualRegex.findAll(textB).count()
            qualB.compareTo(qualA)
        }
    }

    // ─── Metadata Parsing ─────────────────────────────────────────────

    data class ParsedInfo(
        val size: String?,
        val seeds: String?,
        val langs: List<String>,
        val quals: List<String>,
        val isLive: Boolean,
        val hasHindi: Boolean,
        val cleanText: String,
        val parsedSeason: Int?,
        val parsedEpisode: Int?,
        val parsedYear: String?
    )

    fun parseMetadata(rawText: String): ParsedInfo {
        val sizeMatch = Regex("\\b(\\d+(?:\\.\\d+)?)\\s*(GB|MB)\\b", RegexOption.IGNORE_CASE).find(rawText)
        val size = sizeMatch?.value?.uppercase()
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

        // Extract season, episode, year for badges
        val yearMatch = Regex("\\b(19\\d{2}|20[0-2]\\d)\\b").find(rawText)
        val parsedYear = yearMatch?.groupValues?.get(1)

        var parsedSeason: Int? = null
        var parsedEpisode: Int? = null
        val sxeMatch = Regex("\\b(\\d{1,2})x(\\d{1,4})\\b", RegexOption.IGNORE_CASE).find(rawText)
        if (sxeMatch != null && sxeMatch.groupValues[1].toIntOrNull() != 1920) {
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
            .replace(Regex("\\p{So}"), "")  // Remove symbols & emojis
            .replace(Regex("\\p{C}"), "")   // Remove control characters
            .trim()
        return ParsedInfo(
            size = size,
            seeds = seeds,
            langs = langs,
            quals = quals,
            isLive = isLive,
            hasHindi = hasHindi,
            cleanText = cleanText.ifEmpty { "Direct Video Stream" },
            parsedSeason = parsedSeason,
            parsedEpisode = parsedEpisode,
            parsedYear = parsedYear
        )
    }

    // ─── Quality Extraction ──────────────────────────────────────────

    /**
     * Extracts the quality (e.g., "1080p", "720p") from a StreamItem.
     * Returns the first quality found in order of preference:
     * 4K/2160p > 1080p > 720p > 480p > 360p > any other quality.
     */
    fun extractQuality(stream: StreamItem): String? {
        val text = buildString {
            append(stream.title ?: "")
            append(" ")
            append(stream.name ?: "")
            append(" ")
            append(stream.description ?: "")
        }
        val parsed = parseMetadata(text)
        
        // Quality order: highest to lowest
        val qualityOrder = listOf("2160p", "1080p", "720p", "480p", "360p")
        
        // First try to find a quality from the list in order
        qualityOrder.forEach { q ->
            if (parsed.quals.any { it.contains(q, ignoreCase = true) }) {
                return q
            }
        }
        
        // If no standard quality found, return the first quality badge (if any)
        return parsed.quals.firstOrNull()
    }
}