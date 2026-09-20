package com.sudantha2.youtube.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sudantha2.youtube.core.di.ServiceLocator
import com.sudantha2.youtube.data.model.CommentItem
import kotlinx.coroutines.launch

/**
 * Comments bottom sheet.
 *
 * Pagination reuses the derivedStateOf gate pattern from the home feed.
 * Posting goes through InnerTube `comment/create_comment` with the session
 * cookies + SAPISIDHASH — requires sign-in.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsSheet(
    videoId: String,
    initialToken: String,
    onDismiss: () -> Unit,
) {
    val repo = ServiceLocator.feedRepository
    val scope = rememberCoroutineScope()

    var comments by remember { mutableStateOf<List<CommentItem>>(emptyList()) }
    var continuation by remember { mutableStateOf<String?>(initialToken) }
    var loading by remember { mutableStateOf(true) }
    var inFlight by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }

    val listState: LazyListState = rememberLazyListState()
    val nearEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            last >= comments.size - 4
        }
    }

    // Initial page.
    LaunchedEffect(initialToken) {
        loading = true
        runCatching {
            val page = repo.comments(initialToken)
            comments = page.comments
            continuation = page.continuation
        }
        loading = false
    }

    // Next pages.
    LaunchedEffect(nearEnd) {
        if (!nearEnd || inFlight) return@LaunchedEffect
        val token = continuation ?: return@LaunchedEffect
        inFlight = true
        runCatching {
            val page = repo.comments(token)
            comments = comments + page.comments
            continuation = page.continuation
        }
        inFlight = false
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = "Comments",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 20.dp),
        )

        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .heightIn(min = 200.dp),
        ) {
            items(comments, key = { it.id }, contentType = { "comment" }) { c ->
                CommentRow(c)
            }
            if (loading) {
                item(key = "loading", contentType = "footer") {
                    Text("Loading comments…", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // Composer.
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .padding(bottom = 24.dp),
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                placeholder = { Text("Add a comment…") },
                modifier = Modifier.weight(1f),
                maxLines = 3,
            )
            IconButton(
                onClick = {
                    val text = draft.trim()
                    if (text.isEmpty()) return@IconButton
                    val sent = text
                    draft = ""
                    scope.launch {
                        // InnerTube comment/create_comment — session cookies +
                        // SAPISIDHASH are attached by InnerTubeApi automatically.
                        ServiceLocator.innerTubeApi.createComment(videoId, sent)
                    }
                },
                enabled = draft.isNotBlank(),
            ) {
                Icon(Icons.Outlined.Send, "Send")
            }
        }
    }
}

@Composable
private fun CommentRow(comment: CommentItem) {
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = comment.author,
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                text = comment.published,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(text = comment.text, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = "▲ ${comment.likeCount}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
