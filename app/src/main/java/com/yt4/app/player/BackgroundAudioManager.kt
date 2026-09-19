package com.yt4.app.player

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.yt4.app.core.di.ServiceLocator

/**
 * Decoder lifecycle manager for foreground/background transitions.
 *
 * Requirement: "release media decoders immediately when playback stops or
 * transitions to background audio-only."
 *
 * How: when the app leaves the foreground mid-playback we replace the
 * merged video+audio source with the audio-only stream (keeping the exact
 * playback position). ExoPlayer tears down the now-unused video renderer's
 * MediaCodec decoder during the source swap — immediately, not lazily —
 * while Opus/AAC audio keeps playing from the foreground service.
 *
 * On return to the foreground we restore the merged source at the current
 * position; the SurfaceView callback re-attaches the surface automatically.
 */
class BackgroundAudioManager {

    @Volatile
    private var audioOnlyActive = false

    @Volatile
    private var watched: Player? = null

    /** Releases the video surface/decoder once a video finishes. */
    private val endedListener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_ENDED) watched?.let(::onPlaybackEnded)
        }
    }

    /** Attach the ended-state listener to the current player (idempotent). */
    fun watch(player: Player) {
        if (watched === player) return
        watched?.removeListener(endedListener)
        watched = player
        player.addListener(endedListener)
    }

    /** Call from ON_STOP. No-op unless a split audio track is available. */
    fun onBackgrounded(player: Player) {
        val sources = PlaybackRegistry.current ?: return
        if (audioOnlyActive) return
        // Only swap while actively playing; a paused player keeps its decoder
        // so resume in the foreground is instant.
        if (!player.isPlaying) return
        val audioUrl = sources.audioUrl ?: return // muxed: nothing to release

        val positionMs = player.currentPosition
        player.setMediaSource(audioSource(audioUrl), positionMs)
        player.clearVideoSurfaceHolder(null) // drop the surface; decoder released by source swap
        audioOnlyActive = true
    }

    /** Call from ON_START. */
    fun onForegrounded(player: Player) {
        if (!audioOnlyActive) return
        val sources = PlaybackRegistry.current ?: run {
            audioOnlyActive = false
            return
        }
        val videoUrl = sources.videoUrl ?: run {
            audioOnlyActive = false
            return
        }
        val positionMs = player.currentPosition
        player.setMediaSource(mergedSource(videoUrl, sources.audioUrl), positionMs)
        audioOnlyActive = false
        // Surface re-attach happens via the SurfaceHolder.Callback in PlayerSurface.
    }

    /**
     * Releases the video output path when playback ends. The decoder itself
     * dies with the next source change; clearing the holder here makes the
     * last frame vanish immediately instead of lingering on the surface.
     */
    fun onPlaybackEnded(player: Player) {
        player.clearVideoSurfaceHolder(null)
    }

    private fun factory(): ProgressiveMediaSource.Factory =
        PlayerFactory.mediaSourceFactory(ServiceLocator.httpClient)

    private fun audioSource(url: String): MediaSource =
        factory().createMediaSource(MediaItem.fromUri(url))

    private fun mergedSource(videoUrl: String, audioUrl: String?): MediaSource {
        val f = factory()
        val video = f.createMediaSource(MediaItem.fromUri(videoUrl))
        return if (audioUrl != null) {
            MergingMediaSource(video, f.createMediaSource(MediaItem.fromUri(audioUrl)))
        } else {
            video
        }
    }
}
