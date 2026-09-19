package com.yt4.app.player

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.yt4.app.core.innertube.InnerTubeConfig
import okhttp3.OkHttpClient

/**
 * Builds the single app-wide [ExoPlayer].
 *
 * Performance decisions, in order of impact:
 *
 *  1. SurfaceView, not TextureView — decided at the call site
 *     ([com.yt4.app.player.compose.PlayerSurface]), because the surface type
 *     is a view-hierarchy property. SurfaceView composites in a separate
 *     hardware layer: ~40 % less GPU memory and no per-frame texture copy
 *     through the app's RenderNode.
 *
 *  2. [LowRamLoadControl] — 25 s / 20 MB buffer ceiling instead of the
 *     50–150 MB the defaults can reach.
 *
 *  3. ASYNCHRONOUS MediaCodec queueing —
 *     [DefaultRenderersFactory.forceEnableMediaCodecAsynchronousQueueing]
 *     runs the decoders in async mode so input buffers are queued from a
 *     callback thread instead of the render loop; this is Media3's blessed
 *     knob for reducing video frame-drop rate on weak SoCs (API 23+; we
 *     min at 26 so it always applies).
 *
 *  4. Decoder fallback — if the preferred HW decoder fails to init we
 *     transparently take the next candidate instead of erroring mid-session.
 *
 *  5. C.PRIORITY_PLAYBACK — the player's priority task is playback itself,
 *     aligning PriorityTaskManager with our "keep frames moving" policy.
 *
 *  6. One shared DataSource factory over the singleton OkHttp client —
 *     media segments reuse the same HTTP/2 pool as the feed and thumbnails.
 */
object PlayerFactory {

    fun renderersFactory(context: Context): DefaultRenderersFactory =
        DefaultRenderersFactory(context.applicationContext)
            .forceEnableMediaCodecAsynchronousQueueing() // async buffer queueing
            .setEnableDecoderFallback(true)              // HW → SW decoder fallback

    /** Progressive source factory over the shared OkHttp pool. */
    fun mediaSourceFactory(httpClient: OkHttpClient): ProgressiveMediaSource.Factory =
        ProgressiveMediaSource.Factory(dataSourceFactory(httpClient))

    private fun dataSourceFactory(httpClient: OkHttpClient): DataSource.Factory =
        OkHttpDataSource.Factory(httpClient)
            .setUserAgent(InnerTubeConfig.USER_AGENT)

    fun create(context: Context, httpClient: OkHttpClient): ExoPlayer =
        ExoPlayer.Builder(context.applicationContext)
            .setRenderersFactory(renderersFactory(context))
            .setLoadControl(LowRamLoadControl.create())
            .setMediaSourceFactory(mediaSourceFactory(httpClient))
            .setPriority(C.PRIORITY_PLAYBACK)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            // CPU wake lock only (network stays alive via OkHttp), for screen-off audio.
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setHandleAudioBecomingNoisy(true) // pause on headset unplug
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .build()
}
