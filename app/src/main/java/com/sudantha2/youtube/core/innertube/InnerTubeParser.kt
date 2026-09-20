package com.sudantha2.youtube.core.innertube

import com.sudantha2.youtube.data.model.CommentItem
import com.sudantha2.youtube.data.model.CommentsPage
import com.sudantha2.youtube.data.model.FeedPage
import com.sudantha2.youtube.data.model.VideoItem
import com.sudantha2.youtube.data.model.WatchNextResult
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/**
 * Maps InnerTube JSON trees to immutable domain models using direct indexed
 * lookups (no per-response generated deserializers → minimal allocation
 * churn and zero reflective metadata in the APK).
 *
 * Parsers are defensive: any missing/renamed node degrades to "skip item",
 * never a crash — InnerTube field names change without notice.
 */
object InnerTubeParser {

    // ── Feeds ──────────────────────────────────────────────────────────────

    fun parseBrowseFeed(root: JsonObject): FeedPage {
        val items = ArrayList<VideoItem>(32)
        var continuation: String? = null

        // Android client usually serves richGridRenderer; fall back to the
        // sectionListRenderer shape used by some browseIds / experiments.
        val grid = root
            .obj("contents")
            ?.obj("twoColumnBrowseResultsRenderer")
            ?.arr("tabs")
            ?.firstOrNull()
            .asObj()
            ?.obj("tabRenderer")
            ?.obj("content")
            ?.obj("richGridRenderer")

        val contents: JsonArray? = grid?.arr("contents")
            ?: root.obj("contents")
                ?.obj("sectionListRenderer")
                ?.arr("contents")

        if (contents != null) {
            for (element in contents) {
                val o = element.asObj() ?: continue
                when {
                    o.obj("richItemRenderer") != null -> {
                        val renderer = o.obj("richItemRenderer")
                            ?.obj("content")
                            ?.firstKnownVideoRenderer()
                        if (renderer != null) parseVideoRenderer(renderer)?.let(items::add)
                    }
                    o.obj("videoRenderer") != null ->
                        parseVideoRenderer(o.obj("videoRenderer"))?.let(items::add)
                    o.obj("itemSectionRenderer") != null -> {
                        o.obj("itemSectionRenderer")?.arr("contents")?.forEach { sec ->
                            val renderer = sec.asObj()?.firstKnownVideoRenderer()
                            if (renderer != null) parseVideoRenderer(renderer)?.let(items::add)
                        }
                    }
                    o.obj("continuationItemRenderer") != null -> {
                        if (continuation == null) {
                            continuation = extractContinuation(o.obj("continuationItemRenderer"))
                        }
                    }
                }
            }
        }
        return FeedPage(items, continuation)
    }

    fun parseWatchNext(root: JsonObject): WatchNextResult {
        val watchNext = ArrayList<VideoItem>(16)
        var commentsToken: String? = null

        // Watch-next lives in the single-column results rail on mobile.
        val results = root
            .obj("contents")
            ?.obj("singleColumnWatchNextResults")
            ?.obj("results")
            ?.obj("results")
            ?.arr("contents")

        results?.forEach { element ->
            val o = element.asObj() ?: return@forEach

            o.obj("compactVideoRenderer")?.let { renderer ->
                parseCompactVideoRenderer(renderer)?.let(watchNext::add)
            }

            // Sections host the comments entry point with its continuation.
            val section = o.obj("itemSectionRenderer") ?: return@forEach
            if (commentsToken == null) {
                commentsToken = section.arr("contents")
                    ?.firstNotNullOfOrNull { inner ->
                        val i = inner.asObj() ?: return@firstNotNullOfOrNull null
                        extractContinuation(i.obj("continuationItemRenderer"))
                    }
            }
        }
        return WatchNextResult(watchNext, commentsToken)
    }

    fun parseComments(root: JsonObject): CommentsPage {
        val comments = ArrayList<CommentItem>(20)
        var continuation: String? = null

        val actions = root.arr("onResponseReceivedActions") ?: return CommentsPage(emptyList(), null)
        for (action in actions) {
            val actionObj = action.asObj() ?: continue
            val items = actionObj.obj("appendContinuationItemsAction")?.arr("continuationItems")
                ?: actionObj.obj("reloadContinuationItemsCommand")?.arr("continuationItems")
                ?: continue

            for (element in items) {
                val o = element.asObj() ?: continue

                val renderer = o.obj("commentThreadRenderer")
                    ?.obj("comment")
                    ?.obj("commentRenderer")
                    ?: o.obj("commentRenderer")
                if (renderer != null) {
                    parseCommentRenderer(renderer)?.let(comments::add)
                }
                if (continuation == null && o.obj("continuationItemRenderer") != null) {
                    continuation = extractContinuation(o.obj("continuationItemRenderer"))
                }
            }
        }
        return CommentsPage(comments, continuation)
    }

    // ── Renderers → domain ─────────────────────────────────────────────────

    private fun parseVideoRenderer(v: JsonObject): VideoItem? {
        val id = v.str("videoId") ?: return null
        val isLive = hasLiveBadge(v)
        return VideoItem(
            id = id,
            title = v.runsText("title").orEmpty(),
            author = v.obj("ownerText").runsText("runs")
                ?: v.obj("longBylineText").runsText("runs").orEmpty(),
            views = v.obj("viewCountText").str("simpleText")
                ?: v.obj("viewCountText").runsText("runs").orEmpty(),
            published = v.obj("publishedTimeText").str("simpleText").orEmpty(),
            durationText = if (isLive) null else v.obj("lengthText").str("simpleText"),
            thumbnailUrl = v.bestThumbnailUrl(),
            isLive = isLive,
        )
    }

    private fun parseCompactVideoRenderer(v: JsonObject): VideoItem? {
        val id = v.str("videoId") ?: return null
        val isLive = hasLiveBadge(v)
        return VideoItem(
            id = id,
            title = v.runsText("title").orEmpty(),
            author = v.obj("longBylineText").runsText("runs").orEmpty(),
            views = v.obj("viewCountText").str("simpleText")
                ?: v.obj("shortViewCountText").str("simpleText").orEmpty(),
            published = v.obj("publishedTimeText").str("simpleText").orEmpty(),
            durationText = if (isLive) null else v.obj("lengthText").str("simpleText"),
            thumbnailUrl = v.bestThumbnailUrl(),
            isLive = isLive,
        )
    }

    private fun parseCommentRenderer(c: JsonObject): CommentItem? {
        val id = c.str("commentId") ?: return null
        return CommentItem(
            id = id,
            author = c.obj("authorText").str("simpleText")
                ?: c.obj("authorText").runsText("runs").orEmpty(),
            text = c.obj("contentText").runsText("runs").orEmpty(),
            likeCount = c.obj("voteCount").str("simpleText")
                ?: c.obj("voteCount").runsText("runs").orEmpty(),
            published = c.obj("publishedTimeText").str("simpleText").orEmpty(),
        )
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private fun hasLiveBadge(v: JsonObject): Boolean =
        v.arr("badges")?.any { badge ->
            badge.asObj()
                ?.obj("metadataBadgeRenderer")
                ?.str("style")
                ?.contains("LIVE", ignoreCase = true) == true
        } == true

    /**
     * Picks the first video-like renderer inside a content object.
     * Shorts (`reelItemRenderer`) are intentionally skipped: their field
     * shape differs and would produce garbage rows in a video feed.
     */
    private fun JsonObject?.firstKnownVideoRenderer(): JsonObject? {
        if (this == null) return null
        return obj("videoRenderer")
            ?: obj("compactVideoRenderer")
    }

    private fun extractContinuation(continuationItem: JsonObject?): String? =
        continuationItem
            ?.obj("continuationEndpoint")
            ?.obj("continuationCommand")
            ?.str("token")
}
