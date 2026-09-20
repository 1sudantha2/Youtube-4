package com.sudantha2.youtube.core.innertube

import com.sudantha2.youtube.core.auth.AuthRepository
import com.sudantha2.youtube.core.network.await
import com.sudantha2.youtube.data.model.CommentsPage
import com.sudantha2.youtube.data.model.FeedPage
import com.sudantha2.youtube.data.model.WatchNextResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Thin, suspend transport over the InnerTube RPC surface.
 *
 * Design notes:
 *  • Responses are decoded once into a JsonObject tree via kotlinx's
 *    streaming parser (no reflection, single pass); mapping to domain types
 *    happens in [InnerTubeParser] using indexed key lookups only.
 *  • Parsing is moved to [Dispatchers.Default] so OkHttp's callback thread
 *    is released immediately after the read.
 */
class InnerTubeApi(
    private val http: OkHttpClient,
    private val auth: AuthRepository,
) {

    /** Lenient + tolerant: InnerTube drifts field shapes constantly. */
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private companion object {
        val JSON_MEDIA = "application/json".toMediaType()
    }

    // ── Feeds ──────────────────────────────────────────────────────────────

    suspend fun browse(
        browseId: String,
        continuation: String? = null,
        params: String? = null,
    ): FeedPage = post("browse") {
        if (continuation != null) {
            put("continuation", continuation)
        } else {
            put("browseId", browseId)
            params?.let { put("params", it) }
        }
    }.let { InnerTubeParser.parseBrowseFeed(it) }

    /** Watch-next rail + comments entry token for a video. */
    suspend fun watchNext(videoId: String, continuation: String? = null): WatchNextResult =
        post("next") {
            if (continuation != null) {
                put("continuation", continuation)
            } else {
                put("videoId", videoId)
            }
        }.let { InnerTubeParser.parseWatchNext(it) }

    /** Comment pages arrive as continuations of `next`. */
    suspend fun comments(continuation: String): CommentsPage = post("next") {
        put("continuation", continuation)
    }.let { InnerTubeParser.parseComments(it) }

    // ── Engagement actions ─────────────────────────────────────────────────

    suspend fun like(videoId: String): Boolean =
        simpleAction("like/like") { put("target", target(videoId)) }

    suspend fun dislike(videoId: String): Boolean =
        simpleAction("like/dislike") { put("target", target(videoId)) }

    suspend fun removeLike(videoId: String): Boolean =
        simpleAction("like/removelike") { put("target", target(videoId)) }

    suspend fun subscribe(channelIds: List<String>): Boolean =
        simpleAction("subscription/subscribe") {
            putJsonArray("channelIds") { channelIds.forEach { add(it) } }
        }

    suspend fun unsubscribe(channelIds: List<String>): Boolean =
        simpleAction("subscription/unsubscribe") {
            putJsonArray("channelIds") { channelIds.forEach { add(it) } }
        }

    /**
     * Post a top-level comment on a video. `params` is the opaque token from
     * the comment-entry-point renderer when one is present; null works for
     * the standard video comment box on the ANDROID client.
     */
    suspend fun createComment(videoId: String, text: String, params: String? = null): Boolean =
        simpleAction("comment/create_comment") {
            put("commentText", text)
            putJsonObject("createCommentParams") {
                put("videoId", videoId)
                params?.let { put("params", it) }
            }
        }

    // ── Internals ──────────────────────────────────────────────────────────

    private fun target(videoId: String) = buildJsonObject { put("videoId", videoId) }

    private suspend fun simpleAction(
        path: String,
        extras: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit,
    ): Boolean = runCatching {
        post(path, extras)
        true
    }.getOrDefault(false)

    /** POST an InnerTube RPC and return the decoded root object. */
    private suspend fun post(
        path: String,
        extras: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit = {},
    ): JsonObject = withContext(Dispatchers.Default) {
        val body = buildJsonObject {
            putJsonObject("context") { putClientContext() }
            extras()
        }
        val request = Request.Builder()
            .url("${InnerTubeConfig.BASE_URL}/$path?key=${InnerTubeConfig.API_KEY}")
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .applyAuth()
            .build()

        http.newCall(request).await().use { response ->
            check(response.isSuccessful) { "InnerTube $path → HTTP ${response.code}" }
            json.parseToJsonElement(response.body?.string().orEmpty()) as JsonObject
        }
    }

    private fun Request.Builder.applyAuth(): Request.Builder = apply {
        header("Content-Type", "application/json")
        header("User-Agent", InnerTubeConfig.USER_AGENT)
        header("X-Goog-Api-Key", InnerTubeConfig.API_KEY)
        header("X-Youtube-Client-Name", InnerTubeConfig.CLIENT_ID)
        header("X-Youtube-Client-Version", InnerTubeConfig.CLIENT_VERSION)
        header("Accept-Encoding", "gzip") // explicit: keep GZIP on even when bodies change
        // SAPISIDHASH is timestamped per-request; Cookie replays the session.
        auth.authorizationHeader()?.let { header("Authorization", it) }
        auth.cookieHeader()?.let { header("Cookie", it) }
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.putClientContext() {
        putJsonObject("client") {
            put("clientName", InnerTubeConfig.CLIENT_NAME)
            put("clientVersion", InnerTubeConfig.CLIENT_VERSION)
            put("androidSdkVersion", android.os.Build.VERSION.SDK_INT)
            put("hl", "en")
            put("gl", "US")
            put("userAgent", InnerTubeConfig.USER_AGENT)
        }
    }
}
