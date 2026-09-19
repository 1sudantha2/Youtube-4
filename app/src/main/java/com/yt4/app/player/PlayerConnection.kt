package com.yt4.app.player

import android.content.ComponentName
import androidx.annotation.OptIn
import androidx.compose.runtime.Stable
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.yt4.app.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Owns the Activity ↔ PlayerService [MediaController] binding.
 *
 * Follows the Media3 reference pattern: connect in onStart, release in
 * onStop (API 24+), so the controller exists exactly while the UI is
 * visible and zero IPC objects linger in the background.
 */
@Stable
@OptIn(UnstableApi::class)
class PlayerConnection(private val activity: MainActivity) {

    private val _controller = MutableStateFlow<MediaController?>(null)

    /** null while disconnected — the UI must handle this state. */
    val controller: StateFlow<MediaController?> = _controller.asStateFlow()

    private var controllerFuture: ListenableFuture<MediaController>? = null

    fun connect() {
        if (controllerFuture != null) return
        val sessionToken = SessionToken(activity, ComponentName(activity, PlayerService::class.java))
        val future = MediaController.Builder(activity, sessionToken).buildAsync()
        controllerFuture = future
        future.addListener({
            _controller.value = try {
                future.get()
            } catch (_: Exception) {
                null // service unavailable; UI stays in its disconnected state
            }
        }, MoreExecutors.directExecutor())
    }

    fun disconnect() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        _controller.value = null
    }
}
