package com.sudantha2.youtube.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Comment
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.C
import androidx.media3.session.MediaController
import com.sudantha2.youtube.core.di.ServiceLocator
import com.sudantha2.youtube.player.AdaptiveFallback
import com.sudantha2.youtube.player.BackgroundAudioManager
import com.sudantha2.youtube.player.PlaybackRegistry
import com.sudantha2.youtube.player.PlayerCommands
import com.sudantha2.youtube.player.compose.PlayerSurface
import com.sudantha2.youtube.ui.common.vmFactory

/**
 * Watch page: SurfaceView video, minimal controls, quality selector,
 * watch-next rail, comments.
 *
 * Source lifecycle: whenever (bundle × selection) resolves to a new
 * [ResolvedSource] we rebuild the MediaSource. On quality switches the
 * current position is preserved so playback never restarts from zero.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    videoId: String,
    controller: MediaController?,
    backgroundAudio: BackgroundAudioManager,
    onBack: () -> Unit,
    onVideoClick: (String) -> Unit,
) {
    val vm: PlayerViewModel = viewModel(factory = vmFactory {
        PlayerViewModel(ServiceLocator.playerRepository, ServiceLocator.innerTubeApi)
    })

    val state by vm.state.collectAsStateWithLifecycle()
    val selection by vm.selection.collectAsStateWithLifecycle()
    val watchNext by vm.watchNext.collectAsStateWithLifecycle()

    LaunchedEffect(videoId) { vm.load(videoId) }

    val bundle = (state as? PlayerViewModel.UiState.Ready)?.bundle
    val resolved = remember(bundle, selection) {
        bundle?.let { vm.resolveSource(it, selection) }
    }

    // Apply source: first load keeps TIME_UNSET, quality switches keep position.
    // The merged MediaSource is assembled in the service (PlayerService) — the
    // MediaController interface only carries this command + args.
    var hasAppliedOnce by remember(videoId) { mutableStateOf(false) }
    LaunchedEffect(controller, resolved) {
        val c = controller ?: return@LaunchedEffect
        val r = resolved ?: return@LaunchedEffect

        PlayerCommands.playSources(
            controller = c,
            videoUrl = r.videoUrl,
            audioUrl = r.audioUrl,
            startPositionMs = if (hasAppliedOnce) c.currentPosition else C.TIME_UNSET,
        )
        hasAppliedOnce = true

        vm.reportApplied(r)
        PlaybackRegistry.record(
            videoId = videoId,
            videoUrl = if (r.isMuxed) null else r.videoUrl,
            videoHeight = r.height,
            audioUrl = r.audioUrl,
        )
    }

    // Stall-driven auto fallback (only meaningful in Auto mode).
    DisposableEffect(controller, selection.isAuto) {
        var fallback: AdaptiveFallback? = null
        val c = controller
        if (c != null && selection.isAuto) {
            fallback = AdaptiveFallback(c) { vm.downgrade() }
            c.addListener(fallback)
        }
        onDispose { fallback?.release() }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        // ── Video surface ─────────────────────────────────────────────────
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(Color.Black),
        ) {
            PlayerSurface(
                controller = controller,
                modifier = Modifier.fillMaxSize(),
            )

            when (state) {
                PlayerViewModel.UiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                is PlayerViewModel.UiState.Error -> Text(
                    text = (state as PlayerViewModel.UiState.Error).message,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                )
                else -> Unit
            }

            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart)) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = Color.White)
            }
        }

        // ── Controls & metadata ───────────────────────────────────────────
        if (bundle != null) {
            PlaybackControls(player = controller, modifier = Modifier.padding(horizontal = 8.dp))

            Text(
                text = bundle.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Text(
                text = bundle.author,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { vm.like(videoId) }) {
                    Icon(Icons.Outlined.ThumbUp, "Like")
                }
                IconButton(onClick = { vm.dislike(videoId) }) {
                    Icon(Icons.Outlined.ThumbDown, "Dislike")
                }
                TextButton(onClick = { vm.removeLike(videoId) }) { Text("Clear") }

                Box(Modifier.weight(1f))

                var showQuality by remember { mutableStateOf(false) }
                TextButton(onClick = { showQuality = true }) {
                    Icon(Icons.Outlined.Settings, null)
                    Text(
                        text = if (selection.isAuto) "Auto" else "${selection.targetHeight}p",
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }

                val commentsToken = watchNext?.commentsToken
                if (commentsToken != null) {
                    var showComments by remember { mutableStateOf(false) }
                    IconButton(onClick = { showComments = true }) {
                        Icon(Icons.Outlined.Comment, "Comments")
                    }
                    if (showComments) {
                        CommentsSheet(
                            videoId = videoId,
                            initialToken = commentsToken,
                            onDismiss = { showComments = false },
                        )
                    }
                }

                if (showQuality) {
                    QualitySheet(
                        bundle = bundle,
                        selection = selection,
                        onSelect = { vm.select(it) },
                        onDismiss = { showQuality = false },
                    )
                }
            }

            // ── Watch next ────────────────────────────────────────────────
            val next = watchNext?.watchNext.orEmpty()
            if (next.isNotEmpty()) {
                Text(
                    "Up next",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                ) {
                    items(next, key = { it.id }, contentType = { "next" }) { item ->
                        WatchNextCard(item = item, onClick = { onVideoClick(item.id) })
                    }
                }
            }
        }
    }
}
