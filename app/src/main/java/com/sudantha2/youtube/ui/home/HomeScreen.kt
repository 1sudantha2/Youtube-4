package com.sudantha2.youtube.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sudantha2.youtube.data.repo.FeedRepository
import com.sudantha2.youtube.ui.common.vmFactory
import com.sudantha2.youtube.ui.components.ShimmerFeed
import com.sudantha2.youtube.ui.components.VideoFeedList

/**
 * Home tab (`FEwhat_to_watch`).
 *
 * The screen reads exactly three StateFlows; each subtree recomposes only
 * when its own slice changes:
 *   • initialLoading → shimmer (animation lives in the draw phase)
 *   • items          → the LazyColumn body
 *   • appending      → the list footer
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    repository: FeedRepository,
    onVideoClick: (String) -> Unit,
    onRefreshFeeds: () -> Unit = {},
) {
    val vm: HomeViewModel = viewModel(factory = vmFactory { HomeViewModel(repository) })

    val items by vm.items.collectAsStateWithLifecycle()
    val initialLoading by vm.initialLoading.collectAsStateWithLifecycle()
    val appending by vm.appending.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("You-tube", style = MaterialTheme.typography.titleLarge) },
            actions = {
                IconButton(onClick = {
                    vm.refresh()
                    onRefreshFeeds()
                }) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "Refresh")
                }
            },
        )

        when {
            initialLoading -> ShimmerFeed()
            else -> VideoFeedList(
                items = items,
                hasMore = vm.hasMore,
                appending = appending,
                onLoadMore = vm::loadMoreIfNeeded,
                onVideoClick = onVideoClick,
            )
        }
    }
}
