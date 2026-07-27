package com.ultrastream.app.data.repository

import com.ultrastream.app.data.dao.CachedMetaDao
import com.ultrastream.app.data.models.CachedMeta
import com.ultrastream.app.data.models.MetaItem
import com.ultrastream.app.data.models.Video
import com.ultrastream.app.network.Meta
import com.ultrastream.app.network.StremioApi
import com.ultrastream.app.utils.buildAddonBaseUrl
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MetaRepository @Inject constructor(
    private val cachedMetaDao: CachedMetaDao,
    private val addonRepository: AddonRepository,
    private val stremioApi: StremioApi,
    private val moshi: Moshi
) {

    suspend fun getMeta(id: String, type: String): MetaItem? {
        val cacheKey = "$id:$type"
        val cached = cachedMetaDao.getByKey(cacheKey)
        val ttl = 24 * 60 * 60 * 1000L // 24 hours
        if (cached != null && (System.currentTimeMillis() - cached.timestamp < ttl)) {
            return try {
                moshi.adapter(MetaItem::class.java).fromJson(cached.json)
            } catch (e: Exception) {
                null
            }
        }

        val addons = addonRepository.getEnabledAddons()
        var mergedMeta: Meta? = null
        val allVideos = mutableListOf<Video>()

        for (addon in addons) {
            val base = buildAddonBaseUrl(addon.url)
            val fullUrl = "$base/meta/$type/$id.json"
            val meta = try {
                stremioApi.getMeta(fullUrl).meta
            } catch (e: Exception) {
                null
            }
            if (meta != null) {
                if (mergedMeta == null) {
                    mergedMeta = meta.copy(videos = null)
                } else {
                    mergedMeta = mergedMeta.copy(
                        name = mergedMeta.name.takeIf { it.isNotBlank() } ?: meta.name,
                        poster = mergedMeta.poster ?: meta.poster,
                        background = mergedMeta.background ?: meta.background,
                        imdbRating = mergedMeta.imdbRating ?: meta.imdbRating,
                        year = mergedMeta.year ?: meta.year,
                        releaseInfo = mergedMeta.releaseInfo ?: meta.releaseInfo,
                        released = mergedMeta.released ?: meta.released,
                        description = mergedMeta.description ?: meta.description,
                        genre = mergedMeta.genre ?: meta.genre,
                        runtime = mergedMeta.runtime ?: meta.runtime,
                        cast = mergedMeta.cast ?: meta.cast,
                        imdb_id = mergedMeta.imdb_id ?: meta.imdb_id,
                        certification = mergedMeta.certification ?: meta.certification
                    )
                }
                meta.videos?.let { netVideos ->
                    allVideos.addAll(netVideos.map { netVideo ->
                        Video(
                            season = netVideo.season,
                            episode = netVideo.episode,
                            name = netVideo.name,
                            title = netVideo.title,
                            description = netVideo.description,
                            thumbnail = netVideo.thumbnail,
                            url = netVideo.url
                        )
                    })
                }
            }
        }

        if (mergedMeta == null) return null

        val uniqueVideos = mergeVideos(allVideos, emptyList()) ?: emptyList()
        val finalMeta = mergedMeta.copy(
            videos = uniqueVideos.map { video ->
                com.ultrastream.app.network.Video(
                    season = video.season,
                    episode = video.episode,
                    name = video.name,
                    title = video.title,
                    description = video.description,
                    thumbnail = video.thumbnail,
                    url = video.url
                )
            }
        )

        val metaItem = convertToMetaItem(finalMeta)
        val json = moshi.adapter(MetaItem::class.java).toJson(metaItem)
        cachedMetaDao.insert(CachedMeta(cacheKey, json))
        return metaItem
    }

    suspend fun getMetaFlow(id: String, type: String): Flow<MetaItem> = flow {
        val cacheKey = "$id:$type"
        var bestMeta: MetaItem? = null

        // 1. Emit from cache immediately
        cachedMetaDao.getByKey(cacheKey)?.let { cached ->
            try {
                moshi.adapter(MetaItem::class.java).fromJson(cached.json)?.let {
                    bestMeta = it
                    emit(it)
                }
            } catch (e: Exception) {
                // Ignore cache error
            }
        }

        // 2. Fetch from addons incrementally
        val addons = addonRepository.getEnabledAddons()
        for (addon in addons) {
            try {
                val base = buildAddonBaseUrl(addon.url)
                val fullUrl = "$base/meta/$type/$id.json"
                val response = stremioApi.getMeta(fullUrl)
                response.meta?.let { netMeta ->
                    val converted = convertToMetaItem(netMeta)
                    if (bestMeta == null) {
                        bestMeta = converted
                    } else {
                        bestMeta = mergeMeta(bestMeta!!, converted)
                    }
                    bestMeta?.let { emit(it) }
                }
            } catch (e: Exception) {
                // Skip addon if it fails
            }
        }

        // 3. Update cache with final merged meta
        bestMeta?.let {
            try {
                val json = moshi.adapter(MetaItem::class.java).toJson(it)
                cachedMetaDao.insert(CachedMeta(cacheKey, json))
            } catch (e: Exception) {
                // Ignore cache error
            }
        }
    }

    private fun mergeMeta(current: MetaItem, new: MetaItem): MetaItem {
        return current.copy(
            name = current.name.takeIf { it.isNotBlank() } ?: new.name,
            poster = current.poster ?: new.poster,
            background = current.background ?: new.background,
            imdbRating = current.imdbRating ?: new.imdbRating,
            year = current.year ?: new.year,
            releaseInfo = current.releaseInfo ?: new.releaseInfo,
            released = current.released ?: new.released,
            description = current.description.takeIf { !it.isNullOrBlank() } ?: new.description,
            genre = current.genre ?: new.genre,
            runtime = current.runtime ?: new.runtime,
            cast = current.cast ?: new.cast,
            imdbId = current.imdbId ?: new.imdbId,
            certification = current.certification ?: new.certification,
            videos = mergeVideos(current.videos, new.videos)
        )
    }

    private fun mergeVideos(current: List<Video>?, new: List<Video>?): List<Video>? {
        if (current == null) return new
        if (new == null) return current
        
        val map = mutableMapOf<String, Video>()
        (current + new).forEach { video ->
            val key = "${video.season}:${video.episode}"
            val existing = map[key]
            if (existing == null) {
                map[key] = video
            } else {
                // Keep the one that has metadata (name/title)
                val currentHasMeta = !video.name.isNullOrBlank() || !video.title.isNullOrBlank()
                val existingHasMeta = !existing.name.isNullOrBlank() || !existing.title.isNullOrBlank()
                
                if (currentHasMeta && !existingHasMeta) {
                    map[key] = video
                } else if (currentHasMeta && existingHasMeta) {
                    // Both have meta, keep the one with longer description
                    if ((video.description?.length ?: 0) > (existing.description?.length ?: 0)) {
                        map[key] = video
                    }
                }
            }
        }
        return map.values.sortedWith(compareBy({ it.season }, { it.episode }))
    }

    private fun convertToMetaItem(meta: Meta): MetaItem {
        return MetaItem(
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
            certification = meta.certification,
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
    }

    suspend fun getCatalog(url: String, cacheKey: String): List<MetaItem> {
        val cached = cachedMetaDao.getByKey(cacheKey)
        val ttl = 24 * 60 * 60 * 1000L // 24 hours
        if (cached != null && (System.currentTimeMillis() - cached.timestamp < ttl)) {
            val listType = Types.newParameterizedType(List::class.java, MetaItem::class.java)
            return try {
                moshi.adapter<List<MetaItem>>(listType).fromJson(cached.json) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }

        return try {
            val response = stremioApi.getCatalog(url)
            val items = response.metas?.map { meta ->
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
                    certification = meta.certification,
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
            } ?: emptyList()
            
            if (items.isNotEmpty()) {
                val listType = Types.newParameterizedType(List::class.java, MetaItem::class.java)
                val json = moshi.adapter<List<MetaItem>>(listType).toJson(items)
                cachedMetaDao.insert(CachedMeta(cacheKey, json))
            }
            items
        } catch (e: Exception) {
            emptyList()
        }
    }
}

