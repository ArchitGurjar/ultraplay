package com.ultrastream.app.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ultrastream.app.data.models.MetaItem
import com.ultrastream.app.data.preferences.PreferencesManager
import com.ultrastream.app.domain.usecase.SearchUseCase
import com.ultrastream.app.utils.ParentalFilter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchUseCase: SearchUseCase,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    fun search(query: String, filter: String = "all", sort: String = "popular") {
        if (query.length < 2) {
            _uiState.value = _uiState.value.copy(results = emptyList(), isSearching = false)
            return
        }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(600) // Debounce
            _uiState.value = _uiState.value.copy(isSearching = true)
            try {
                val results = searchUseCase(query, filter, sort)
                val parentalControl = preferencesManager.getParentalControl().first()
                val parentalRating = preferencesManager.getParentalRating().first()
                val filtered = results.filter { ParentalFilter.shouldShow(it, parentalControl, parentalRating) }
                _uiState.value = _uiState.value.copy(results = filtered, isSearching = false, error = null)
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

