package com.ultrastream.app.data.repository

import com.ultrastream.app.data.dao.CachedMetaDao
import com.ultrastream.app.data.models.CachedMeta
import com.ultrastream.app.data.models.StreamItem
import com.ultrastream.app.data.models.Subtitle
import com.ultrastream.app.data.preferences.PreferencesManager
import com.ultrastream.app.network.StremioApi
import com.ultrastream.app.utils.DebridHelper
import com.ultrastream.app.utils.LinkVerifier
import com.ultrastream.app.utils.StreamParser
import com.ultrastream.app.utils.buildAddonBaseUrl
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StreamRepository @Inject constructor(
    private val stremioApi: StremioApi,
    private val debridHelper: DebridHelper,
    private val linkVerifier: LinkVerifier,
    private val streamParser: StreamParser,
    private val preferencesManager: PreferencesManager,
    private val cachedMetaDao: CachedMetaDao,
    private val moshi: Moshi
) {

    // ─── Existing Methods ──────────────────────────────────────────────

    suspend fun getStreams(
        metaId: String,
        metaType: String,
        season: Int? = null,
        episode: Int? = null,
        addonUrls: List<String>,
        hindiPriority: Boolean,
        debridKey: String?
    ): List<StreamItem> {
        val idWithExtra = if (season != null && episode != null) {
            "$metaId:$season:$episode"
        } else {
            metaId
        }

        val allStreams = mutableListOf<StreamItem>()
        for (url in addonUrls) {
            try {
                val baseUrl = buildAddonBaseUrl(url)
                val fullUrl = if (season != null && episode != null) {
                    "$baseUrl/stream/$metaType/$idWithExtra.json"
                } else {
                    "$baseUrl/stream/$metaType/$metaId.json"
                }
                val finalUrl = debridHelper.applyDebridParams(fullUrl, debridKey ?: "")
                val response = stremioApi.getStreams(finalUrl)
                val addonName = extractAddonName(url)
                val streams = response.streams?.mapNotNull { netStream ->
                    val streamItem = convertStream(netStream, addonName)
                    if (season != null && episode != null) {
                        val textToCheck = buildString {
                            append(streamItem.title ?: "")
                            append(" ")
                            append(streamItem.name ?: "")
                            append(" ")
                            append(streamItem.description ?: "")
                        }
                        if (!streamParser.isValidEpisode(textToCheck, season, episode)) {
                            return@mapNotNull null
                        }
                    }
                    streamItem
                } ?: emptyList()
                allStreams.addAll(streams)
            } catch (e: Exception) {
                // Skip this addon
            }
        }
        return streamParser.sortStreams(allStreams, hindiPriority)
    }

    suspend fun getStreamsFlow(
        metaId: String,
        metaType: String,
        season: Int? = null,
        episode: Int? = null,
        addonUrls: List<String>,
        hindiPriority: Boolean,
        debridKey: String?
    ): Flow<List<StreamItem>> = flow {
        val cacheKey = "streams:$metaId:$metaType:$season:$episode"
        val listType = Types.newParameterizedType(List::class.java, StreamItem::class.java)
        val adapter = moshi.adapter<List<StreamItem>>(listType)

        // 1. Emit from cache immediately
        cachedMetaDao.getByKey(cacheKey)?.let { cached ->
            try {
                adapter.fromJson(cached.json)?.let {
                    emit(it)
                }
            } catch (e: Exception) {}
        }

        val allStreams = mutableListOf<StreamItem>()

        for (url in addonUrls) {
            try {
                val baseUrl = buildAddonBaseUrl(url)
                val idWithExtra = if (season != null && episode != null) {
                    "$metaId:$season:$episode"
                } else {
                    metaId
                }
                val fullUrl = if (season != null && episode != null) {
                    "$baseUrl/stream/$metaType/$idWithExtra.json"
                } else {
                    "$baseUrl/stream/$metaType/$metaId.json"
                }
                val finalUrl = debridHelper.applyDebridParams(fullUrl, debridKey ?: "")
                val response = stremioApi.getStreams(finalUrl)
                val addonName = extractAddonName(url)
                val streams = response.streams?.mapNotNull { netStream ->
                    val streamItem = convertStream(netStream, addonName)
                    if (season != null && episode != null) {
                        val textToCheck = buildString {
                            append(streamItem.title ?: "")
                            append(" ")
                            append(streamItem.name ?: "")
                            append(" ")
                            append(streamItem.description ?: "")
                        }
                        if (!streamParser.isValidEpisode(textToCheck, season, episode)) {
                            return@mapNotNull null
                        }
                    }
                    streamItem
                } ?: emptyList()

                if (streams.isNotEmpty()) {
                    allStreams.addAll(streams)
                    val sorted = streamParser.sortStreams(allStreams, hindiPriority)
                    emit(sorted)
                }
            } catch (e: Exception) {
                // Skip this addon
            }
        }

        // 2. Update cache with final streams
        if (allStreams.isNotEmpty()) {
            try {
                val sorted = streamParser.sortStreams(allStreams, hindiPriority)
                val json = adapter.toJson(sorted)
                cachedMetaDao.insert(CachedMeta(cacheKey, json))
            } catch (e: Exception) {}
        }
    }

    // ─── New Method: getStreamsForEpisode with Quality & Language Filters ──

    suspend fun getStreamsForEpisode(
        metaId: String,
        metaType: String,
        season: Int,
        episode: Int,
        addonUrls: List<String>,
        hindiPriority: Boolean,
        debridKey: String?,
        qualityFilter: String? = null,
        languageFilter: String? = null
    ): List<StreamItem> {
        val idWithExtra = "$metaId:$season:$episode"
        val allStreams = mutableListOf<StreamItem>()

        for (url in addonUrls) {
            try {
                val baseUrl = buildAddonBaseUrl(url)
                val fullUrl = "$baseUrl/stream/$metaType/$idWithExtra.json"
                val finalUrl = debridHelper.applyDebridParams(fullUrl, debridKey ?: "")
                val response = stremioApi.getStreams(finalUrl)
                val addonName = extractAddonName(url)
                val streams = response.streams?.mapNotNull { netStream ->
                    val streamItem = convertStream(netStream, addonName)

                    // 1. Exact episode match
                    val textToCheck = buildString {
                        append(streamItem.title ?: "")
                        append(" ")
                        append(streamItem.name ?: "")
                        append(" ")
                        append(streamItem.description ?: "")
                    }
                    if (!streamParser.isValidEpisode(textToCheck, season, episode)) {
                        return@mapNotNull null
                    }

                    // 2. Quality filter
                    if (qualityFilter != null) {
                        val parsed = streamParser.parseMetadata(textToCheck)
                        val hasQuality = parsed.quals.any { it.contains(qualityFilter, ignoreCase = true) }
                        if (!hasQuality) return@mapNotNull null
                    }

                    // 3. Language filter
                    if (languageFilter != null) {
                        val parsed = streamParser.parseMetadata(textToCheck)
                        val hasLanguage = parsed.langs.any { it.contains(languageFilter, ignoreCase = true) } ||
                                (languageFilter.equals("Hindi", ignoreCase = true) && parsed.hasHindi)
                        if (!hasLanguage) return@mapNotNull null
                    }

                    streamItem
                } ?: emptyList()
                allStreams.addAll(streams)
            } catch (e: Exception) {
                // Skip this addon
            }
        }
        return streamParser.sortStreams(allStreams, hindiPriority)
    }

    suspend fun resolveStream(stream: StreamItem, debridKey: String?): StreamItem {
        val provider = when (preferencesManager.getDebridProvider()?.firstOrNull() ?: "realdebrid") {
            "alldebrid" -> DebridHelper.DebridProvider.ALL_DEBRID
            "premiumize" -> DebridHelper.DebridProvider.PREMIUMIZE
            else -> DebridHelper.DebridProvider.REAL_DEBRID
        }
        val resolvedUrl = debridHelper.resolveStreamUrl(stream.url ?: "", debridKey, provider)
        return stream.copy(url = resolvedUrl)
    }

    // ─── Helpers ────────────────────────────────────────────────────────

    private fun convertStream(stream: com.ultrastream.app.network.Stream, addonName: String): StreamItem {
        return StreamItem(
            url = stream.url,
            streamUrl = stream.streamUrl,
            externalUrl = stream.externalUrl,
            title = stream.title,
            name = stream.name,
            description = stream.description,
            infoHash = stream.infoHash,
            addonName = addonName,
            subtitles = stream.subtitles?.map {
                Subtitle(
                    url = it.url,
                    file = it.file,
                    lang = it.lang,
                    name = it.name
                )
            },
            isLive = stream.isLive
        )
    }

    private fun extractAddonName(url: String): String {
        val parts = url.split("/")
        return parts.getOrElse(2) { "addon" }
    }

    // Note: buildAddonBaseUrl is imported from utils package
}