package com.sudantha2.youtube.player

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.sudantha2.youtube.MainActivity
import com.sudantha2.youtube.core.di.ServiceLocator

/**
 * App-wide [MediaSessionService].
 *
 * One ExoPlayer instance lives here for the entire process lifetime — the
 * Activity binds/unbinds a MediaController around it. Keeping the player in
 * the service means:
 *
 *  • Navigation never touches playback state (no re-prepares, no decoder
 *    churn between screens).
 *  • Screen-off audio continues as a proper foreground service with the
 *    platform media notification, at minimal CPU (no UI thread work).
 *
 * The UI cannot build MergingMediaSources through the MediaController
 * interface (Player only takes MediaItems), so it sends the raw stream URLs
 * via the [PlayerCommands.CMD_PLAY_SOURCES] custom command and this service
 * assembles the source on the real [ExoPlayer].
 */
@OptIn(UnstableApi::class)
class PlayerService : MediaSessionService() {

    private var mediaSession: MediaLibrarySession? = null

    /**
     * Session callback handling the PLAY_SOURCES custom command. Custom
     * commands live on the session callback, not on MediaSessionService.
     */
    private val sessionCallback = object : MediaLibrarySession.Callback {

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            val result = SettableFuture.create<SessionResult>()
            if (customCommand.customAction != PlayerCommands.CMD_PLAY_SOURCES) {
                result.set(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
                return result
            }

            val videoUrl = args.getString(PlayerCommands.ARG_VIDEO_URL)
            val audioUrl = args.getString(PlayerCommands.ARG_AUDIO_URL)
            val startMs = args.getLong(PlayerCommands.ARG_START_POSITION_MS, C.TIME_UNSET)

            val source = buildSource(videoUrl, audioUrl)
            if (source == null) {
                result.set(SessionResult(SessionResult.RESULT_ERROR_BAD_VALUE))
                return result
            }

            val player = session.player as ExoPlayer
            if (startMs == C.TIME_UNSET) {
                player.setMediaSource(source)
            } else {
                player.setMediaSource(source, startMs)
            }
            player.prepare()
            player.playWhenReady = true
            result.set(SessionResult(SessionResult.RESULT_SUCCESS))
            return result
        }
    }

    override fun onCreate() {
        super.onCreate()
        val player = PlayerFactory.create(this, ServiceLocator.httpClient)
        mediaSession = MediaLibrarySession.Builder(this, player, sessionCallback)
            .setSessionActivity(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    /** video+audio merge, single progressive file, or audio-only. */
    private fun buildSource(videoUrl: String?, audioUrl: String?): MediaSource? {
        val factory = PlayerFactory.mediaSourceFactory(ServiceLocator.httpClient)
        return when {
            videoUrl != null && audioUrl != null -> MergingMediaSource(
                factory.createMediaSource(MediaItem.fromUri(videoUrl)),
                factory.createMediaSource(MediaItem.fromUri(audioUrl)),
            )
            videoUrl != null -> factory.createMediaSource(MediaItem.fromUri(videoUrl))
            audioUrl != null -> factory.createMediaSource(MediaItem.fromUri(audioUrl))
            else -> null
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        // App swiped away while not playing → tear down now and release the
        // decoders. Active playback keeps the foreground service alive.
        if (player == null || !player.playWhenReady || player.playbackState == Player.STATE_ENDED) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release() // releases all MediaCodec decoders immediately
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
