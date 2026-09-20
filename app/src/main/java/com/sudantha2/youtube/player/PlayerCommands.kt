package com.sudantha2.youtube.player

import android.os.Bundle
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand

/**
 * Custom session commands bridging the UI process to the service-hosted
 * ExoPlayer.
 *
 * Why: [androidx.media3.common.Player] only accepts MediaItems; building a
 * [androidx.media3.exoplayer.source.MergingMediaSource] (video-only DASH +
 * Opus/AAC) requires the real ExoPlayer API, which lives in the service.
 * The UI therefore sends URLs over a SessionCommand and the service builds
 * the merged source itself (see PlayerService.onCustomCommand).
 */
object PlayerCommands {

    const val CMD_PLAY_SOURCES = "com.sudantha2.youtube.PLAY_SOURCES"
    const val ARG_VIDEO_URL = "video_url"
    const val ARG_AUDIO_URL = "audio_url"
    const val ARG_START_POSITION_MS = "start_position_ms"

    /**
     * Loads (video+audio) / (video) / (audio) into the service player.
     * [startPositionMs] = androidx.media3.common.C.TIME_UNSET for default.
     */
    fun playSources(
        controller: MediaController,
        videoUrl: String?,
        audioUrl: String?,
        startPositionMs: Long,
    ) {
        val args = Bundle().apply {
            videoUrl?.let { putString(ARG_VIDEO_URL, it) }
            audioUrl?.let { putString(ARG_AUDIO_URL, it) }
            putLong(ARG_START_POSITION_MS, startPositionMs)
        }
        controller.sendCustomCommand(SessionCommand(CMD_PLAY_SOURCES, Bundle.EMPTY), args)
    }
}
