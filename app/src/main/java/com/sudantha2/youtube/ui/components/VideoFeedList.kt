package com.sudantha2.youtube.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sudantha2.youtube.data.model.VideoItem

private const val CONTENT_TYPE_VIDEO = "video"
private const val CONTENT_TYPE_FOOTER = "footer"

/** How many items before the end we trigger the next page. */
private const val PREFETCH_DISTANCE = 6

/**
 * The zero-jitter feed list. Anti-jank measures, in order:
 *
 *  1. `key = { it.id }` — stable identity per item. Pagination inserts at
 *     the tail; Compose diffs by key and reuses every existing holder.
 *  2. `contentType = { "video" }` — homogeneous recycling pools. A
 *     recycled holder never needs re-inflation for a different shape.
 *  3. Load-more trigger wrapped in [derivedStateOf] keyed on the list
 *     state: the boolean flips only when the *derived* value changes, so
 *     per-frame scroll offsets never invalidate composition. The gate is
 *     evaluated during layout snapshot reads, not during composition of
 *     every item.
 *  4. No `animateItemPlacement`/item animations: they force extra layout
 *     passes on insert and buy nothing for an infinite feed.
 */
@Composable
fun VideoFeedList(
    items: List<VideoItem>,
    hasMore: Boolean,
    appending: Boolean,
    onLoadMore: () -> Unit,
    onVideoClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    // Deferred gate: recomputed only when visible-range/layout info changes.
    val shouldPrefetch by remember(items, hasMore) {
        derivedStateOf {
            if (!hasMore) return@derivedStateOf false
            val layoutInfo = listState.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisible >= items.size - PREFETCH_DISTANCE
        }
    }

    // Fires only on false→true transitions of the derived flag.
    LaunchedEffect(shouldPrefetch) {
        if (shouldPrefetch) onLoadMore()
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
    ) {
        items(
            items = items,
            key = { it.id },              // stable identity → max recycling
            contentType = { CONTENT_TYPE_VIDEO },
        ) { item ->
            VideoCard(
                item = item,
                onClick = { onVideoClick(item.id) },
            )
        }

        if (appending) {
            item(key = "feed_footer", contentType = CONTENT_TYPE_FOOTER) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(strokeWidth = 2.dp)
                }
            }
        }
    }
}
