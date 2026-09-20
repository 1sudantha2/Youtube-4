package com.sudantha2.youtube.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import com.sudantha2.youtube.data.model.VideoItem

/** Compact watch-next card — same HARDWARE-bitmap, EXACT-precision pipeline. */
@Composable
fun WatchNextCard(
    item: VideoItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(160.dp)
            .clickable(onClick = onClick),
    ) {
        Box(Modifier.aspectRatio(16f / 9f)) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(item.thumbnailUrl)
                    .memoryCacheKey(item.id)
                    .precision(Precision.EXACT)
                    .allowHardware(true)
                    .crossfade(false)
                    .build(),
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.Black),
            )
        }
        Text(
            text = item.title,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            text = item.metaLine,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
