package com.wikimedia.wikiwatch.presentation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wikimedia.wikiwatch.data.SearchResult
import com.wikimedia.wikiwatch.data.WikipediaRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SearchViewModel : ViewModel() {
    private val _results = MutableStateFlow<List<SearchResult>>(emptyList())
    val results: StateFlow<List<SearchResult>> = _results

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private var searchJob: Job? = null

    fun search(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            _results.value = emptyList()
            _isLoading.value = false
            return
        }
        searchJob = viewModelScope.launch {
            try {
                _isLoading.value = true
                val searchResults = WikipediaRepository.search(query)
                Log.d("WikiWatch", "ViewModel received ${searchResults.size} results for '$query'")
                _results.value = searchResults
                _isLoading.value = false
            } catch (e: CancellationException) {
                // Expected when typing fast, ignore
                throw e
            } catch (e: Exception) {
                Log.e("WikiWatch", "Search failed: ${e.message}")
                _isLoading.value = false
            }
        }
    }
}

