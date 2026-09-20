package com.sudantha2.youtube.data.model

import androidx.compose.runtime.Immutable

/**
 * Every data class crossing the Compose boundary is annotated @Immutable:
 * all fields are vals of stable types, so the Compose compiler treats them
 * as stable and skips recomposition when the same instance is seen again.
 * Text fields arrive pre-formatted from the server ("1.2M views") so the UI
 * never pays for number formatting or locale logic mid-frame.
 */

@Immutable
data class VideoItem(
    val id: String,
    val title: String,
    val author: String,
    val views: String,          // pre-formatted, e.g. "1.2M views"
    val published: String,      // e.g. "3 days ago"
    val durationText: String?,  // null for live streams
    val thumbnailUrl: String?,
    val isLive: Boolean = false,
) {
    val metaLine: String
        get() = buildString {
            append(author)
            if (views.isNotEmpty()) append(" • ").append(views)
            if (published.isNotEmpty()) append(" • ").append(published)
        }
}

/** One page of a feed, plus the opaque token to fetch the next one. */
@Immutable
data class FeedPage(
    val items: List<VideoItem>,
    val continuation: String?,
)

/** A comment thread row. */
@Immutable
data class CommentItem(
    val id: String,
    val author: String,
    val text: String,
    val likeCount: String,
    val published: String,
)

@Immutable
data class CommentsPage(
    val comments: List<CommentItem>,
    val continuation: String?,
)

/** Watch-next results + entry point into the comments section. */
@Immutable
data class WatchNextResult(
    val watchNext: List<VideoItem>,
    val commentsToken: String?,
)

// ── Playback ────────────────────────────────────────────────────────────────

@Immutable
data class VideoStreamOption(
    val url: String,
    val height: Int,        // 144…1080
    val label: String,      // "1080p60"
    val bitrate: Int,       // kbps
    val isMuxed: Boolean,   // true → audio included (fallback format)
)

@Immutable
data class AudioStreamOption(
    val url: String,
    val bitrate: Int,       // kbps
    val label: String,      // "opus 160kbps" / "aac 128kbps"
)

/** Everything the player needs, extracted by NewPipeExtractor. */
@Immutable
data class StreamBundle(
    val videoId: String,
    val title: String,
    val author: String,
    val lengthSeconds: Long,
    /** Video-only DASH tracks, sorted by height desc (1080p → 144p). */
    val videoOnly: List<VideoStreamOption>,
    /** Muxed progressive tracks (auto-fallback targets), sorted desc. */
    val muxed: List<VideoStreamOption>,
    /** Audio tracks sorted by bitrate desc; index 0 is the best. */
    val audio: List<AudioStreamOption>,
) {
    val defaultAudio: AudioStreamOption? get() = audio.firstOrNull()
}

/** User's quality intent. */
@Immutable
data class QualitySelection(
    /** 0 = Auto (highest ≤1080p with stall-driven fallback). */
    val targetHeight: Int = 0,
) {
    val isAuto: Boolean get() = targetHeight == 0

    companion object {
        val Auto = QualitySelection(0)
    }
}
