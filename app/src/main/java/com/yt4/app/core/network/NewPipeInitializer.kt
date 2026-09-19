package com.yt4.app.core.network

import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.localization.Localization

/**
 * One-time NewPipeExtractor bootstrap, deliberately deferred until first
 * playback so Rhino/jsoup classes never load during app start.
 */
object NewPipeInitializer {

    @Volatile
    private var initialized = false

    private val lock = Any()

    fun ensureInitialized(httpClient: OkHttpClient) {
        if (initialized) return
        synchronized(lock) {
            if (initialized) return
            NewPipe.init(SharedOkHttpDownloader(httpClient), Localization("en", "US"))
            initialized = true
        }
    }

    /**
     * Routes all NPE traffic through the app-wide OkHttp client instead of
     * letting it build its own (NPE's default would spin up a second
     * connection pool — unacceptable).
     */
    private class SharedOkHttpDownloader(private val client: OkHttpClient) : Downloader() {

        override fun execute(request: Request): Response {
            // NPE uses method-style accessors: url(), httpMethod(), headers().
            val body = request.dataToSend()?.toRequestBody()
            val builder = okhttp3.Request.Builder()
                .url(request.url())
                .method(request.httpMethod(), body)

            request.headers().forEach { (name, values) ->
                values.forEach { value -> builder.addHeader(name, value) }
            }

            client.newCall(builder.build()).execute().use { response ->
                return Response(
                    response.code,
                    response.message,
                    response.headers.toMultimap(),
                    response.body?.string().orEmpty(),
                    // latestUrl lets NPE detect redirects; OkHttp follows them
                    // transparently, so the request URL is accurate enough here.
                    response.request.url.toString(),
                )
            }
        }

        private fun ByteArray.toRequestBody(): okhttp3.RequestBody =
            okhttp3.RequestBody.create(null, this)
    }
}
