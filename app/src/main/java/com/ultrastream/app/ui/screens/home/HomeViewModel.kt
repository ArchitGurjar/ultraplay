package com.ultrastream.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.ultrastream.app.data.models.Addon
import com.ultrastream.app.data.models.Catalog
import com.ultrastream.app.data.models.HistoryItem
import com.ultrastream.app.data.models.MetaItem
import com.ultrastream.app.data.models.Video
import com.ultrastream.app.data.repository.AddonRepository
import com.ultrastream.app.data.repository.MetaRepository
import com.ultrastream.app.data.repository.StreamRepository
import com.ultrastream.app.data.dao.HistoryDao
import com.ultrastream.app.data.dao.WatchProgressDao
import com.ultrastream.app.network.StremioApi
import com.ultrastream.app.utils.buildAddonBaseUrl
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val addonRepository: AddonRepository,
    private val metaRepository: MetaRepository,
    private val streamRepository: StreamRepository,
    private val historyDao: HistoryDao,
    private val watchProgressDao: WatchProgressDao,
    private val stremioApi: StremioApi
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
    private val catalogListType = Types.newParameterizedType(List::class.java, Catalog::class.java)
    private val catalogAdapter = moshi.adapter<List<Catalog>>(catalogListType)

    init {
        loadHomeData()
    }

    fun loadHomeData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val historyItems = historyDao.getAll().take(10)
            val continueWatching = historyItems.mapNotNull { history ->
                val progress = watchProgressDao.getById(history.id)
                if (progress != null && progress.percent > 0) history to progress.percent else null
            }

            val addons = addonRepository.getEnabledAddons()
            val catalogRows = ConcurrentHashMap<String, List<MetaItem>>()

            val fetchJobs = mutableListOf<Deferred<Unit>>()
            for (addon in addons) {
                try {
                    val catalogs = catalogAdapter.fromJson(addon.catalogs) ?: emptyList()
                    val baseUrl = buildAddonBaseUrl(addon.url)

                    for (cat in catalogs) {
                        val rowId = "${addon.id}_${cat.type}_${cat.id}"
                        fetchJobs.add(
                            viewModelScope.async {
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
                                        } catch (e: Exception) { null }
                                    } ?: emptyList()
                                    catalogRows[rowId] = items.take(20)
                                } catch (e: Exception) {
                                    // skip this catalog
                                }
                            }
                        )
                    }
                } catch (e: Exception) {
                    // skip addon
                }
            }
            fetchJobs.awaitAll()

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                continueWatching = continueWatching,
                addons = addons,
                catalogRows = catalogRows.toSortedMap(compareBy { it })
            )
        }
    }

    fun refresh() = loadHomeData()

    data class HomeUiState(
        val isLoading: Boolean = false,
        val addons: List<Addon> = emptyList(),
        val continueWatching: List<Pair<HistoryItem, Int>> = emptyList(),
        val catalogRows: Map<String, List<MetaItem>> = emptyMap()
    )
}

