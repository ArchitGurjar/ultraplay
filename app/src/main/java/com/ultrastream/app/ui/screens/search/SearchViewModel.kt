package com.ultrastream.app.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.ultrastream.app.data.models.Catalog
import com.ultrastream.app.data.models.MetaItem
import com.ultrastream.app.data.models.Video
import com.ultrastream.app.data.repository.AddonRepository
import com.ultrastream.app.data.repository.MetaRepository
import com.ultrastream.app.network.StremioApi
import com.ultrastream.app.utils.buildAddonBaseUrl
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.URLEncoder
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val addonRepository: AddonRepository,
    private val metaRepository: MetaRepository,
    private val stremioApi: StremioApi
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
    private val catalogListType = Types.newParameterizedType(List::class.java, Catalog::class.java)
    private val catalogAdapter = moshi.adapter<List<Catalog>>(catalogListType)

    fun search(query: String, filter: String = "all", sort: String = "popular") {
        if (query.length < 2) {
            _uiState.value = _uiState.value.copy(results = emptyList(), isSearching = false)
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSearching = true)
            val results = mutableListOf<MetaItem>()

            try {
                val addons = addonRepository.getEnabledAddons()
                val types = when (filter) {
                    "all" -> listOf("movie", "series", "anime", "tv")
                    else -> listOf(filter)
                }

                for (addon in addons) {
                    val baseUrl = buildAddonBaseUrl(addon.url)
                    val catalogs = catalogAdapter.fromJson(addon.catalogs) ?: emptyList()

                    for (type in types) {
                        val searchableCatalog = catalogs.firstOrNull { cat ->
                            cat.type == type && (cat.extraSupported?.contains("search") == true ||
                                cat.extra?.any { it.name == "search" } == true)
                        } ?: continue

                        val encodedQuery = URLEncoder.encode(query, "UTF-8")
                        val searchUrl = "$baseUrl/catalog/$type/${searchableCatalog.id}/search=$encodedQuery.json"
                        try {
                            val response = stremioApi.getCatalog(searchUrl)
                            response.metas?.forEach { meta ->
                                results.add(
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
                                )
                            }
                        } catch (e: Exception) {
                            // ignore this addon/type combo
                        }
                    }
                }

                val unique = results.distinctBy { it.id }
                val sorted = when (sort) {
                    "rating" -> unique.sortedByDescending { it.imdbRating?.toDoubleOrNull() ?: 0.0 }
                    "year" -> unique.sortedByDescending { it.year?.toIntOrNull() ?: 0 }
                    else -> unique
                }

                _uiState.value = _uiState.value.copy(results = sorted, isSearching = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    results = emptyList(),
                    isSearching = false,
                    error = e.message
                )
            }
        }
    }

    fun clearSearch() {
        _uiState.value = _uiState.value.copy(results = emptyList(), isSearching = false)
    }

    data class SearchUiState(
        val isSearching: Boolean = false,
        val results: List<MetaItem> = emptyList(),
        val error: String? = null
    )
}

