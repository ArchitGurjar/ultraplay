package com.ultrastream.app.data.repository

import com.ultrastream.app.data.models.StreamItem
import com.ultrastream.app.data.models.Subtitle
import com.ultrastream.app.network.StremioApi
import com.ultrastream.app.network.Stream
import com.ultrastream.app.utils.DebridHelper
import com.ultrastream.app.utils.StreamParser
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StreamRepository @Inject constructor(
    private val stremioApi: StremioApi,
    private val debridHelper: DebridHelper,
    private val streamParser: StreamParser
) {

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

        return coroutineScope {
            val deferred = addonUrls.map { url ->
                async {
                    try {
                        var baseUrl = url
                        if (baseUrl.endsWith("/manifest.json")) {
                            baseUrl = baseUrl.substring(0, baseUrl.length - "/manifest.json".length)
                        } else if (baseUrl.endsWith("manifest.json")) {
                            baseUrl = baseUrl.substring(0, baseUrl.length - "manifest.json".length)
                        }
                        if (baseUrl.endsWith("/")) {
                            baseUrl = baseUrl.substring(0, baseUrl.length - 1)
                        }

                        val fullUrl = if (season != null && episode != null) {
                            "$baseUrl/stream/$metaType/$idWithExtra.json"
                        } else {
                            "$baseUrl/stream/$metaType/$metaId.json"
                        }
                        val finalUrl = debridHelper.applyDebridParams(fullUrl, debridKey ?: "")
                        val response = stremioApi.getStreams(finalUrl)
                        response.streams?.mapNotNull { stream ->
                            val addonName = extractAddonName(url)
                            val streamItem = convertStream(stream, addonName)
                            if (season != null && episode != null) {
                                if (!streamParser.isValidEpisode(streamItem, season, episode)) {
                                    return@mapNotNull null
                                }
                            }
                            streamItem
                        } ?: emptyList()
                    } catch (e: Exception) {
                        emptyList()
                    }
                }
            }
            val results = deferred.awaitAll()
            val all = results.flatten()
            streamParser.sortStreams(all, hindiPriority)
        }
    }

    suspend fun resolveStream(stream: StreamItem, debridKey: String?): StreamItem {
        val resolvedUrl = debridHelper.resolveStreamUrl(stream.url ?: "", debridKey)
        return stream.copy(url = resolvedUrl)
    }

    private fun convertStream(stream: Stream, addonName: String): StreamItem {
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
}

