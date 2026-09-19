package com.yt4.app.ui.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.media3.common.Player
import kotlinx.coroutines.delay

/**
 * Transport controls, engineered so the 500 ms position poll recomposes
 * THIS function only — never the SurfaceView subtree, never the feed below.
 * (Position is inherently fast-changing state; isolating the reader is the
 * fix, not avoiding the read.)
 */
@Composable
fun PlaybackControls(player: Player?, modifier: Modifier = Modifier) {
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(1L) }
    var isPlaying by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }

    LaunchedEffect(player) {
        val p = player ?: return@LaunchedEffect
        while (true) {
            if (!dragging) {
                positionMs = p.currentPosition.coerceAtLeast(0L)
                durationMs = p.duration.takeIf { it > 0 } ?: 1L
                isPlaying = p.isPlaying
            }
            delay(500) // one poll per half-second; no per-frame work
        }
    }

    Column(modifier.fillMaxWidth()) {
        Slider(
            value = positionMs.toFloat(),
            onValueChange = {
                dragging = true
                positionMs = it.toLong()
            },
            onValueChangeFinished = {
                player?.seekTo(positionMs)
                dragging = false
            },
            valueRange = 0f..durationMs.toFloat(),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(formatTime(positionMs), style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
            IconButton(onClick = {
                val p = player ?: return@IconButton
                if (p.isPlaying) p.pause() else p.play()
            }) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                )
            }
            Text(formatTime(durationMs), style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
