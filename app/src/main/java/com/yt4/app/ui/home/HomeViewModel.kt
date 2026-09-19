package com.yt4.app.ui.home

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yt4.app.data.model.VideoItem
import com.yt4.app.data.repo.FeedRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Home feed (`browseId = FEwhat_to_watch`) with continuation pagination.
 *
 * State is exposed as three cheap StateFlows instead of one fat UiState so
 * the LazyColumn only recomposes when the list actually changes — loading
 * flags flip on disjoint subtrees.
 */
@Stable
class HomeViewModel(private val repository: FeedRepository) : ViewModel() {

    private val _items = MutableStateFlow<List<VideoItem>>(emptyList())
    val items: StateFlow<List<VideoItem>> = _items.asStateFlow()

    private val _initialLoading = MutableStateFlow(true)
    val initialLoading: StateFlow<Boolean> = _initialLoading.asStateFlow()

    private val _appending = MutableStateFlow(false)
    val appending: StateFlow<Boolean> = _appending.asStateFlow()

    private var continuation: String? = null
    private var inFlight = false

    init {
        refresh()
    }

    fun refresh() {
        if (inFlight) return
        inFlight = true
        _initialLoading.value = _items.value.isEmpty()
        viewModelScope.launch {
            try {
                val page = repository.home()
                _items.value = page.items
                continuation = page.continuation
            } catch (_: Exception) {
                // Keep whatever we had; feed failures are non-fatal.
            } finally {
                _initialLoading.value = false
                inFlight = false
            }
        }
    }

    /**
     * Called from a `derivedStateOf` gate in the UI — safe to spam; the
     * inFlight guard makes concurrent calls a no-op.
     */
    fun loadMoreIfNeeded() {
        val token = continuation ?: return
        if (inFlight || _appending.value) return
        inFlight = true
        _appending.value = true
        viewModelScope.launch {
            try {
                val page = repository.homeMore(token)
                if (page.items.isNotEmpty()) {
                    _items.value = _items.value + page.items
                }
                continuation = page.continuation
            } catch (_: Exception) {
                // Retry on next threshold crossing.
            } finally {
                _appending.value = false
                inFlight = false
            }
        }
    }

    val hasMore: Boolean
        get() = continuation != null
}
