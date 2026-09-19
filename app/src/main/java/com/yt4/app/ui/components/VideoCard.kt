package com.yt4.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import com.yt4.app.data.model.VideoItem

/**
 * One feed card.
 *
 * Image pipeline contract (section 2.C):
 *  • [Precision.EXACT] — decode at the target view's pixel size, never
 *    larger. A 320×180 card on a 3× display decodes ~960×540, not the
 *    1280×720 source.
 *  • allowHardware(true) — decoded pixels live in a HARDWARE Bitmap (GPU
 *    memory). They don't count against the Java heap and never trigger a
 *    GC pause mid-scroll.
 *  • memoryCacheKey = videoId — cache identity is stable across
 *    recompositions/navigation, so the same thumbnail is decoded exactly
 *    once per session.
 *
 * The composable itself is trivially stable: it consumes a single
 * @Immutable [VideoItem] and a lambda — Compose skips it entirely when the
 * same item instance is seen again during pagination.
 */
@Composable
fun VideoCard(
    item: VideoItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Box(Modifier.fillMaxWidth()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(item.thumbnailUrl)
                    .memoryCacheKey(item.id)
                    .precision(Precision.EXACT)     // decode to target size exactly
                    .allowHardware(true)            // Bitmap.Config.HARDWARE
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .crossfade(false)               // no extra bitmap pass
                    .build(),
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color.Black),
            )

            // Duration chip / LIVE badge — tiny, no layout cost worth saving.
            val badge = when {
                item.isLive -> "LIVE"
                item.durationText != null -> item.durationText
                else -> null
            }
            if (badge != null) {
                Text(
                    text = badge,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .background(
                            if (item.isLive) Color(0xFFCC0000) else Color(0xCC000000),
                            RoundedCornerShape(3.dp),
                        )
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Channel initial as the avatar: zero network cost, instant paint.
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = item.author.firstOrNull()?.uppercase().orEmpty(),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = item.metaLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
