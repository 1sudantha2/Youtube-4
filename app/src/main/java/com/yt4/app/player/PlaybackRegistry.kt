package com.yt4.app.player

import androidx.compose.runtime.Immutable

/**
 * Process-wide record of what is currently loaded into the player.
 *
 * The background-audio manager needs this to swap a video+audio merge for
 * an audio-only source when the app leaves the foreground — releasing the
 * video MediaCodec decoder while music/podcast playback continues.
 */
object PlaybackRegistry {

    @Immutable
    data class Sources(
        val videoId: String,
        /** null when the active format already carries no dedicated video track. */
        val videoUrl: String?,
        val videoHeight: Int,
        /** null for muxed progressive streams (cannot be split). */
        val audioUrl: String?,
    )

    @Volatile
    var current: Sources? = null

    fun record(videoId: String, videoUrl: String?, videoHeight: Int, audioUrl: String?) {
        current = Sources(videoId, videoUrl, videoHeight, audioUrl)
    }

    fun clearIf(videoId: String) {
        if (current?.videoId == videoId) current = null
    }
}
