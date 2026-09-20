package com.sudantha2.youtube.ui.feed

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sudantha2.youtube.data.repo.FeedRepository
import com.sudantha2.youtube.ui.common.vmFactory
import com.sudantha2.youtube.ui.components.ShimmerFeed
import com.sudantha2.youtube.ui.components.VideoFeedList

/** Subscriptions / Library / Liked — one renderer, three browseIds. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    kind: FeedKind,
    repository: FeedRepository,
    onVideoClick: (String) -> Unit,
) {
    val vm: FeedViewModel = viewModel(
        key = kind.name,
        factory = vmFactory { FeedViewModel(kind, repository) },
    )

    val items by vm.items.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()

    val title = when (kind) {
        FeedKind.SUBSCRIPTIONS -> "Subscriptions"
        FeedKind.LIBRARY -> "Library"
        FeedKind.LIKED -> "Liked videos"
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text(title) })

        when {
            loading -> ShimmerFeed()
            items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (kind == FeedKind.SUBSCRIPTIONS) {
                        "Sign in to see your subscriptions"
                    } else {
                        "Nothing here yet"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            else -> VideoFeedList(
                items = items,
                hasMore = false,
                appending = false,
                onLoadMore = {},
                onVideoClick = onVideoClick,
            )
        }
    }
}
