package com.sudantha2.youtube.player

import androidx.media3.exoplayer.DefaultLoadControl

/**
 * Aggressively capped LoadControl.
 *
 * ExoPlayer's defaults target *smoothness above all*: up to ~50 s of buffer,
 * which on a 1080p stream is 50–150 MB of compressed samples sitting in
 * Java/native heap. For a "< 120 MB active playback" budget that alone
 * would blow the envelope, so we clamp the buffer hard:
 *
 *  ┌──────────────────────────────────┬────────────┐
 *  │ minBufferMs                      │ 10 000 ms  │
 *  │ maxBufferMs                      │ 25 000 ms  │
 *  │ bufferForPlaybackMs              │  1 500 ms  │
 *  │ bufferForPlaybackAfterRebufferMs │  3 000 ms  │
 *  │ targetBufferBytes                │ 20 MB cap  │
 *  └──────────────────────────────────┴────────────┘
 *
 * Additionally:
 *  • prioritizeTimeOverSizeThresholds = true — implements C.PRIORITY_PLAYBACK:
 *    when the byte budget is tight, ExoPlayer prefers keeping playback
 *    moving over hoarding bytes.
 *  • 3 s back-buffer, not retained from keyframes — seeking backwards never
 *    re-allocates, old samples are dropped immediately.
 *
 * Cost: slightly more rebuffering on very bad networks; the adaptive
 * fallback (see AdaptiveQualityController) covers that by dropping
 * resolution instead of stalling.
 */
object LowRamLoadControl {

    private const val MIN_BUFFER_MS = 10_000
    private const val MAX_BUFFER_MS = 25_000
    private const val BUFFER_FOR_PLAYBACK_MS = 1_500
    private const val BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 3_000

    /** Hard 20 MB ceiling on buffered sample bytes. */
    const val TARGET_BUFFER_BYTES = 20 * 1024 * 1024

    /** Only 3 s of rewind history kept in RAM. */
    private const val BACK_BUFFER_MS = 3_000

    fun create(): DefaultLoadControl =
        DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                MIN_BUFFER_MS,
                MAX_BUFFER_MS,
                BUFFER_FOR_PLAYBACK_MS,
                BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
            )
            .setTargetBufferBytes(TARGET_BUFFER_BYTES)
            .setPrioritizeTimeOverSizeThresholds(true) // C.PRIORITY_PLAYBACK policy
            .setBackBuffer(BACK_BUFFER_MS, /* retainBackBufferFromKeyframe = */ false)
            .build()
}
