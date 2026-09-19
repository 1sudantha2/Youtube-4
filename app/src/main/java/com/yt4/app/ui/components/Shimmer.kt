package com.yt4.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Shimmer skeleton system, engineered for zero-recomposition animation:
 *
 *  1. One shared [rememberInfiniteTransition] drives a single float `phase`
 *     for the whole skeleton screen — one animation clock, not N.
 *  2. The phase is passed around as `State<Float>`, never unwrapped in
 *     composition. It is read INSIDE [Modifier.shimmer]'s draw lambda, so
 *     every frame only re-DRAWS (RenderNode display-list patch) and never
 *     re-RUNS composition/measure/layout. This is the pattern that keeps
 *     skeleton screens at a locked 60/120 fps on budget GPUs.
 */

@Composable
fun rememberShimmerPhase(): State<Float> {
    val transition = rememberInfiniteTransition(label = "shimmer")
    return transition.animateFloat(
        initialValue = -0.6f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerPhase",
    )
}

/**
 * Overlay a moving highlight band. `phase` is read in the DRAW phase only —
 * recomposition count stays at 0 for the lifetime of the skeleton.
 */
fun Modifier.shimmer(
    phase: State<Float>,
    base: Color,
    highlight: Color,
): Modifier = this.drawWithContent {
    drawContent()
    val p = phase.value // ← deferred state read: draw-only invalidation
    val band = size.width * 0.55f
    val startX = (p * (size.width + band)) - band
    drawRect(
        brush = Brush.linearGradient(
            colors = listOf(
                base.copy(alpha = 0f),
                highlight.copy(alpha = 0.55f),
                base.copy(alpha = 0f),
            ),
            start = Offset(startX, 0f),
            end = Offset(startX + band, size.height),
        ),
    )
}

/** Full-screen skeleton matching the VideoCard layout exactly. */
@Composable
fun ShimmerFeed(itemCount: Int = 5, modifier: Modifier = Modifier) {
    val phase = rememberShimmerPhase()
    val base = MaterialTheme.colorScheme.surfaceVariant
    val highlight = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        repeat(itemCount) {
            ShimmerVideoCard(phase = phase, base = base, highlight = highlight)
        }
    }
}

@Composable
fun ShimmerVideoCard(
    phase: State<Float>,
    base: Color,
    highlight: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // 16:9 thumbnail block.
        Spacer(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(base)
                .shimmer(phase, base, highlight),
        )
        // Meta row: avatar + two text lines.
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(base)
                    .shimmer(phase, base, highlight),
            )
            Spacer(Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier
                        .fillMaxWidth(0.88f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(base)
                        .shimmer(phase, base, highlight),
                )
                Box(
                    Modifier
                        .fillMaxWidth(0.55f)
                        .height(12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(base)
                        .shimmer(phase, base, highlight),
                )
            }
        }
    }
}
