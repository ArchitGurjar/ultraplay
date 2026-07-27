package com.ultrastream.app.domain.usecase

import com.ultrastream.app.data.models.MetaItem
import com.ultrastream.app.data.models.Video
import com.ultrastream.app.data.repository.AddonRepository
import com.ultrastream.app.data.repository.MetaRepository
import com.ultrastream.app.data.preferences.PreferencesManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
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

@Singleton
class GetHomeCatalogsUseCase @Inject constructor(
    private val addonRepository: AddonRepository,
    private val metaRepository: MetaRepository,
    private val preferencesManager: PreferencesManager
) {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val catalogListType = Types.newParameterizedType(List::class.java, com.ultrastream.app.data.models.Catalog::class.java)
    private val catalogAdapter = moshi.adapter<List<com.ultrastream.app.data.models.Catalog>>(catalogListType)

    suspend operator fun invoke(): Map<String, List<MetaItem>> {
        val addons = addonRepository.getEnabledAddons()
        if (addons.isEmpty()) return emptyMap()

        val isParentalEnabled = preferencesManager.getParentalControl().first()
        val maxRatingStr = preferencesManager.getParentalRating().first()
        val maxRatingValue = ratingToValue(maxRatingStr)

        val catalogRows = mutableMapOf<String, List<MetaItem>>()

        coroutineScope {
            val deferred = addons.flatMap { addon ->
                val catalogs = catalogAdapter.fromJson(addon.catalogs) ?: emptyList()
                val baseUrl = buildAddonBaseUrl(addon.url)

                catalogs.map { cat ->
                    async {
                        val rowId = "${addon.id}_${cat.type}_${cat.id}"
                        try {
                            val url = "$baseUrl/catalog/${cat.type}/${cat.id}.json"
                            val items = metaRepository.getCatalog(url, rowId)
                            val filteredItems = if (isParentalEnabled) {
                                items.filter { item ->
                                    ratingToValue(item.certification ?: "G") <= maxRatingValue
                                }
                            } else items
                            catalogRows[rowId] = filteredItems.take(20) // Limit per row
                        } catch (e: Exception) {
                            // Skip this catalog
                        }
                    }
                }
            }
            deferred.awaitAll()
        }

        return catalogRows.toSortedMap(compareBy { it })
    }

    suspend fun getCatalogsFlow(): Flow<Pair<String, List<MetaItem>>> = flow {
        val addons = addonRepository.getEnabledAddons()
        if (addons.isEmpty()) return@flow

        val isParentalEnabled = preferencesManager.getParentalControl().first()
        val maxRatingStr = preferencesManager.getParentalRating().first()
        val maxRatingValue = ratingToValue(maxRatingStr)

        for (addon in addons) {
            val catalogs = catalogAdapter.fromJson(addon.catalogs) ?: emptyList()
            val baseUrl = buildAddonBaseUrl(addon.url)

            for (cat in catalogs) {
                val rowId = "${addon.id}_${cat.type}_${cat.id}"
                try {
                    val url = "$baseUrl/catalog/${cat.type}/${cat.id}.json"
                    val items = metaRepository.getCatalog(url, rowId)
                    if (items.isNotEmpty()) {
                        val filteredItems = if (isParentalEnabled) {
                            items.filter { item ->
                                ratingToValue(item.certification ?: "G") <= maxRatingValue
                            }
                        } else items
                        if (filteredItems.isNotEmpty()) {
                            emit(rowId to filteredItems.take(20))
                        }
                    }
                } catch (e: Exception) {
                    // Skip catalog
                }
            }
        }
    }

    private fun ratingToValue(rating: String): Int {
        val r = rating.uppercase().trim()
        return when {
            r.contains("NC-17") -> 5
            r.contains("R") -> 4
            r.contains("PG-13") -> 3
            r.contains("PG") -> 2
            r.contains("G") -> 1
            else -> 5 // Unknown rating is treated as NC-17
        }
    }
}

