package com.yt4.app.player

import android.os.Handler
import android.os.Looper
import androidx.media3.common.Player

/**
 * Stall-driven quality fallback ("auto" mode).
 *
 * With the aggressive [LowRamLoadControl], a degraded network shows up as
 * STATE_BUFFERING while playing. If a stall lasts longer than [STALL_MS]
 * we ask the ViewModel to drop one quality tier — eventually landing on a
 * muxed progressive stream as the last resort. This is how we keep the
 * buffer small *and* the playback continuous.
 *
 * Implemented as a posted runnable rather than a coroutine: zero allocations
 * per state change and it cancels cleanly on RELEASED/READY transitions.
 */
class AdaptiveFallback(
    private val player: Player,
    private val stallMs: Long = DEFAULT_STALL_MS,
    private val onDowngrade: () -> Unit,
) : Player.Listener {

    companion object {
        const val DEFAULT_STALL_MS = 3_000L
    }

    private val handler = Handler(Looper.getMainLooper())

    private val downgradeRunnable = Runnable {
        // Only downgrade if the user still intends to play.
        if (player.playWhenReady) onDowngrade()
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        when (playbackState) {
            Player.STATE_BUFFERING -> {
                handler.removeCallbacks(downgradeRunnable)
                handler.postDelayed(downgradeRunnable, stallMs)
            }
            else -> handler.removeCallbacks(downgradeRunnable)
        }
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (!isPlaying) handler.removeCallbacks(downgradeRunnable)
    }

    /** Detach listener + pending work. Call when leaving the player screen. */
    fun release() {
        handler.removeCallbacks(downgradeRunnable)
        player.removeListener(this)
    }
}
