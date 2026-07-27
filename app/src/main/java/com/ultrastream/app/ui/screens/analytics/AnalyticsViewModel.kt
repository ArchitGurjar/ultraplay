package com.ultrastream.app.ui.screens.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ultrastream.app.data.dao.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val watchedEpisodeDao: WatchedEpisodeDao,
    private val watchProgressDao: WatchProgressDao,
    private val libraryDao: LibraryDao,
    private val watchlistDao: WatchlistDao,
    private val historyDao: HistoryDao
) : ViewModel() {

    private val _stats = MutableStateFlow(AnalyticsStats())
    val stats: StateFlow<AnalyticsStats> = _stats.asStateFlow()

    init {
        loadStats()
    }

    private fun loadStats() {
        viewModelScope.launch {
            val totalWatched = watchedEpisodeDao.getAllCount()
            val inProgress = watchProgressDao.getAllCount()
            val library = libraryDao.getAllCount()
            val watchlist = watchlistDao.getAllCount()
            val history = historyDao.getAllCount()
            
            val allProgress = watchProgressDao.getAll()
            val avgComp = if (allProgress.isNotEmpty()) {
                allProgress.sumOf { it.percent } / allProgress.size
            } else 0

            _stats.value = AnalyticsStats(
                totalEpisodesWatched = totalWatched,
                inProgressCount = inProgress,
                libraryCount = library,
                watchlistCount = watchlist,
                historyCount = history,
                avgCompletion = avgComp
            )
        }
    }

    data class AnalyticsStats(
        val totalEpisodesWatched: Int = 0,
        val inProgressCount: Int = 0,
        val libraryCount: Int = 0,
        val watchlistCount: Int = 0,
        val historyCount: Int = 0,
        val avgCompletion: Int = 0
    )
}
