package com.ultrastream.app.domain.usecase

import android.util.Log
import com.ultrastream.app.data.models.MetaItem
import com.ultrastream.app.data.models.Video
import com.ultrastream.app.data.repository.AddonRepository
import com.ultrastream.app.network.StremioApi
import com.ultrastream.app.utils.buildAddonBaseUrl
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "GetHomeCatalogsUseCase"

@Singleton
class GetHomeCatalogsUseCase @Inject constructor(
    private val addonRepository: AddonRepository,
    private val stremioApi: StremioApi
) {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val catalogListType = Types.newParameterizedType(List::class.java, com.ultrastream.app.data.models.Catalog::class.java)
    private val catalogAdapter = moshi.adapter<List<com.ultrastream.app.data.models.Catalog>>(catalogListType)

    suspend operator fun invoke(): Map<String, List<MetaItem>> {
        val addons = addonRepository.getEnabledAddons()
        val catalogRows = mutableMapOf<String, List<MetaItem>>()

        if (addons.isEmpty()) {
            Log.w(TAG, "No addons enabled – using fallback catalogs")
            return getFallbackCatalogs()
        }

        coroutineScope {
            val deferred = addons.flatMap { addon ->
                val catalogs = catalogAdapter.fromJson(addon.catalogs) ?: emptyList()
                if (catalogs.isEmpty()) {
                    Log.w(TAG, "Addon ${addon.id} has no catalogs")
                    return@flatMap emptyList()
                }
                val baseUrl = buildAddonBaseUrl(addon.url)

                catalogs.map { cat ->
                    async {
                        val rowId = "${addon.id}_${cat.type}_${cat.id}"
                        try {
                            val url = "$baseUrl/catalog/${cat.type}/${cat.id}.json"
                            val response = stremioApi.getCatalog(url)
                            val items = response.metas?.mapNotNull { meta ->
                                try {
                                    MetaItem(
                                        id = meta.id,
                                        type = meta.type,
                                        name = meta.name,
                                        poster = meta.poster,
                                        background = meta.background,
                                        imdbRating = meta.imdbRating,
                                        year = meta.year,
                                        releaseInfo = meta.releaseInfo,
                                        released = meta.released,
                                        description = meta.description,
                                        genre = meta.genre,
                                        runtime = meta.runtime,
                                        cast = meta.cast,
                                        imdbId = meta.imdb_id,
                                        videos = meta.videos?.map {
                                            Video(
                                                season = it.season,
                                                episode = it.episode,
                                                name = it.name,
                                                title = it.title,
                                                description = it.description,
                                                thumbnail = it.thumbnail,
                                                url = it.url
                                            )
                                        }
                                    )
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error parsing meta for ${meta.id}: ${e.message}")
                                    null
                                }
                            } ?: emptyList()
                            catalogRows[rowId] = items.take(20) // Limit per row
                            Log.d(TAG, "Fetched ${items.size} items for $rowId")
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to fetch catalog $rowId: ${e.message}")
                            // Skip this catalog – will fallback later if no rows
                        }
                    }
                }
            }
            deferred.awaitAll()
        }

        // If no catalog rows, use fallback
        if (catalogRows.isEmpty()) {
            Log.w(TAG, "No catalog rows fetched – using fallback")
            return getFallbackCatalogs()
        }

        return catalogRows.toSortedMap(compareBy { it })
    }

    // ✅ Fallback: Popular catalogs from Cinemeta (if Cinemeta is installed)
    private suspend fun getFallbackCatalogs(): Map<String, List<MetaItem>> {
        val fallbackRows = mutableMapOf<String, List<MetaItem>>()
        // Try Cinemeta's top lists
        val cinemetaUrl = "https://v3-cinemeta.strem.io"
        val fallbackCatalogs = listOf(
            Triple("movie", "top", "Top Movies"),
            Triple("series", "top", "Top Series"),
            Triple("anime", "top", "Top Anime")
        )

        for ((type, id, name) in fallbackCatalogs) {
            try {
                val url = "$cinemetaUrl/catalog/$type/$id.json"
                val response = stremioApi.getCatalog(url)
                val items = response.metas?.mapNotNull { meta ->
                    try {
                        MetaItem(
                            id = meta.id,
                            type = meta.type,
                            name = meta.name,
                            poster = meta.poster,
                            background = meta.background,
                            imdbRating = meta.imdbRating,
                            year = meta.year,
                            releaseInfo = meta.releaseInfo,
                            released = meta.released,
                            description = meta.description,
                            genre = meta.genre,
                            runtime = meta.runtime,
                            cast = meta.cast,
                            imdbId = meta.imdb_id,
                            videos = meta.videos?.map {
                                Video(
                                    season = it.season,
                                    episode = it.episode,
                                    name = it.name,
                                    title = it.title,
                                    description = it.description,
                                    thumbnail = it.thumbnail,
                                    url = it.url
                                )
                            }
                        )
                    } catch (e: Exception) { null }
                } ?: emptyList()
                if (items.isNotEmpty()) {
                    fallbackRows["fallback_$type"] = items.take(20)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Fallback catalog $type failed: ${e.message}")
            }
        }
        return fallbackRows
    }
}
