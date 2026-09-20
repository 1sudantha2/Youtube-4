package com.sudantha2.youtube.ui.feed

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sudantha2.youtube.data.model.VideoItem
import com.sudantha2.youtube.data.repo.FeedRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class FeedKind { SUBSCRIPTIONS, LIBRARY, LIKED }

/**
 * Generic browse feed (Subscriptions FEsubscriptions, Library FElibrary,
 * Liked VLLM). Same pagination shape as Home; split out so each screen owns
 * its own list state and back-stack survival.
 */
@Stable
class FeedViewModel(
    private val kind: FeedKind,
    private val repository: FeedRepository,
) : ViewModel() {

    private val _items = MutableStateFlow<List<VideoItem>>(emptyList())
    val items: StateFlow<List<VideoItem>> = _items.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val page = when (kind) {
                    FeedKind.SUBSCRIPTIONS -> repository.subscriptions()
                    FeedKind.LIBRARY -> repository.library()
                    FeedKind.LIKED -> repository.likedVideos()
                }
                _items.value = page.items
            } catch (_: Exception) {
                _items.value = emptyList()
            } finally {
                _loading.value = false
            }
        }
    }
}
