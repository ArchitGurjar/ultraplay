package com.ultrastream.app.ui.screens.search

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ultrastream.app.ui.components.FilterChipGroup
import com.ultrastream.app.ui.components.PosterCard
import com.ultrastream.app.utils.SearchQueryHolder
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SearchScreen(
    viewModel: SearchViewModel = hiltViewModel(),
    onItemClick: (id: String, type: String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var query by remember { mutableStateOf(SearchQueryHolder.query) }
    var filter by remember { mutableStateOf("all") }
    var sort by remember { mutableStateOf("popular") }

    val scope = rememberCoroutineScope()
    var searchJob by remember { mutableStateOf<Job?>(null) }

    // Auto-search on first composition
    LaunchedEffect(Unit) {
        if (SearchQueryHolder.shouldAutoSearch && query.isNotBlank()) {
            viewModel.search(query, filter, sort)
            SearchQueryHolder.shouldAutoSearch = false
            SearchQueryHolder.query = ""
        }
    }

    // When filter or sort changes, trigger search with current query
    LaunchedEffect(filter, sort) {
        if (query.isNotBlank()) {
            viewModel.search(query, filter, sort)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { newQuery ->
                query = newQuery
                // Cancel previous job
                searchJob?.cancel()
                if (newQuery.length < 2) {
                    viewModel.clearSearch()
                    return@OutlinedTextField
                }
                // Debounce 600ms
                searchJob = scope.launch {
                    delay(600)
                    viewModel.search(newQuery, filter, sort)
                }
            },
            label = { Text("Search movies, series, anime, TV...") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(8.dp))
        FilterChipGroup(
            chips = listOf("All", "Movie", "Series", "Anime", "TV"),
            selected = filter,
            onSelect = {
                filter = it.lowercase()
                // LaunchedEffect will re-trigger search
            }
        )
        Spacer(modifier = Modifier.height(8.dp))
        FilterChipGroup(
            chips = listOf("Popular", "Rating", "Year"),
            selected = sort,
            onSelect = {
                sort = it.lowercase()
                // LaunchedEffect will re-trigger search
            }
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (uiState.isSearching) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 120.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.results.size) { index ->
                    val item = uiState.results[index]
                    PosterCard(
                        meta = item,
                        onClick = { onItemClick(item.id, item.type) },
                        modifier = Modifier.fillMaxWidth()
                        // progress not shown on search results (web also doesn't)
                    )
                }
            }
        }
    }
}