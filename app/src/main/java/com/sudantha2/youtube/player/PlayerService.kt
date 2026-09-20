package com.sudantha2.youtube.player

import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
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
 */
@OptIn(UnstableApi::class)
class PlayerService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = PlayerFactory.create(this, ServiceLocator.httpClient)
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(
                android.app.PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, com.sudantha2.youtube.MainActivity::class.java),
                    android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        // App swiped away while not playing → tear down now and release the
        // decoders. Active playback keeps the foreground service alive.
        if (player == null || !player.playWhenReady || player.playbackState == androidx.media3.common.Player.STATE_ENDED) {
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
