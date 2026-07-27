package com.ultrastream.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ultrastream.app.data.dao.HistoryDao
import com.ultrastream.app.data.dao.WatchProgressDao
import com.ultrastream.app.data.models.Addon
import com.ultrastream.app.data.models.HistoryItem
import com.ultrastream.app.data.models.MetaItem
import com.ultrastream.app.data.models.RecommendedAddon
import com.ultrastream.app.data.repository.AddonRepository
import com.ultrastream.app.data.repository.MetaRepository
import com.ultrastream.app.data.preferences.PreferencesManager
import com.ultrastream.app.domain.usecase.GetHomeCatalogsUseCase
import com.ultrastream.app.domain.usecase.UpdateWatchProgressUseCase
import com.ultrastream.app.utils.AppRefreshManager
import com.ultrastream.app.utils.ParentalFilter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val addonRepository: AddonRepository,
    private val getHomeCatalogsUseCase: GetHomeCatalogsUseCase,
    private val updateWatchProgressUseCase: UpdateWatchProgressUseCase,
    private val metaRepository: MetaRepository,
    private val historyDao: HistoryDao,
    private val watchProgressDao: WatchProgressDao,
    private val preferencesManager: PreferencesManager,
    private val appRefreshManager: AppRefreshManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadHomeData()
        observeRefresh()
    }

    private fun observeRefresh() {
        viewModelScope.launch {
            appRefreshManager.refreshFlow.collect {
                loadHomeData()
            }
        }
    }

    fun loadHomeData() {
        viewModelScope.launch {
            try {
                addonRepository.ensureDefaultAddons()
                _uiState.value = _uiState.value.copy(isLoading = true, catalogRows = emptyList())

                val continueWatching = updateWatchProgressUseCase.getContinueWatching()
                val addons = addonRepository.getEnabledAddons()
                val recommendations = buildRecommendations()

                val parentalControl = preferencesManager.getParentalControl().first()
                val parentalRating = preferencesManager.getParentalRating().first()

                val filteredRecommendations = recommendations.filter { ParentalFilter.shouldShow(it, parentalControl, parentalRating) }

                // Build progress map for all items
                val progressList = watchProgressDao.getAll()
                val progressMap = progressList.associate { it.id to it.percent }

                _uiState.value = _uiState.value.copy(
                    continueWatching = continueWatching,
                    addons = addons,
                    recommendedAddons = getRecommendedAddons(addons),
                    recommendations = filteredRecommendations,
                    progressMap = progressMap,
                    error = null
                )

                // Fetch catalogs incrementally
                getHomeCatalogsUseCase.getCatalogsFlow().collect { (rowId, items) ->
                    val currentList = _uiState.value.catalogRows.toMutableList()
                    currentList.add(rowId to items)
                    _uiState.value = _uiState.value.copy(
                        catalogRows = currentList,
                        isLoading = false
                    )
                }
                
                if (_uiState.value.catalogRows.isEmpty()) {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load home"
                )
            }
        }
    }

    private suspend fun buildRecommendations(): List<MetaItem> {
        val history = historyDao.getAll().take(5)
        if (history.isEmpty()) return emptyList()
        val result = mutableListOf<MetaItem>()
        for (item in history) {
            val meta = metaRepository.getMeta(item.id, item.type)
            if (meta != null) result.add(meta)
        }
        return result.distinctBy { it.id }
    }

    private fun getRecommendedAddons(installed: List<Addon>): List<RecommendedAddon> {
        val builtIn = listOf(
            RecommendedAddon("Torrentio", "Torrent scraper for movies & series", "https://torrentio.strem.fun/manifest.json"),
            RecommendedAddon("Cinemeta", "Metadata provider for movies & series", "https://cinemeta.strem.fun/manifest.json"),
            RecommendedAddon("Juan Carlos 2", "Streaming addon with 4K sources", "https://juan-carlos.strem.fun/manifest.json"),
            RecommendedAddon("Orion", "Alternative scraper for premium content", "https://orion.strem.fun/manifest.json")
        )
        return builtIn.map { addon ->
            addon.copy(isInstalled = installed.any { it.url == addon.url || it.id == addon.name.lowercase() })
        }
    }

    fun refresh() = loadHomeData()

    data class HomeUiState(
        val isLoading: Boolean = false,
        val addons: List<Addon> = emptyList(),
        val continueWatching: List<Pair<HistoryItem, Int>> = emptyList(),
        val catalogRows: List<Pair<String, List<MetaItem>>> = emptyList(),
        val recommendedAddons: List<RecommendedAddon> = emptyList(),
        val recommendations: List<MetaItem> = emptyList(),
        val progressMap: Map<String, Int> = emptyMap(),
        val error: String? = null
    )
}