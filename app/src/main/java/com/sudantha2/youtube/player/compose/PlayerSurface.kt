package com.sudantha2.youtube.player.compose

import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.session.MediaController

/**
 * Raw [SurfaceView] host for the video renderer.
 *
 * Why not TextureView / the default PlayerView texture mode:
 * TextureView video is uploaded to a SurfaceTexture and re-composited
 * through the app's GL RenderNode every frame — an extra texture copy that
 * costs up to ~40 % more GPU memory and measurable battery on budget GPUs.
 * SurfaceView gets its own hardware-composited layer (HWC): zero copies,
 * direct display path, and it survives Doze better.
 *
 * The surface is attached/detached through [MediaController.setVideoSurfaceHolder]
 * so the single service-hosted player renders wherever the UI currently is.
 */
@Composable
fun PlayerSurface(
    controller: MediaController?,
    modifier: Modifier = Modifier,
) {
    // remember(controller) — re-attach when a fresh controller binds.
    val attachState = remember(controller) { SurfaceAttachState() }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            SurfaceView(context).apply {
                holder.addCallback(attachState.holderCallback)
                attachState.surfaceView = this
                controller?.let { attachState.attach(it) }
            }
        },
        update = { surfaceView ->
            // Runs on recomposition but is allocation-free and O(1):
            // a field store plus a null-checked interface call.
            attachState.surfaceView = surfaceView
            controller?.let(attachState::attach)
        },
    )

    DisposableEffect(controller) {
        onDispose {
            controller?.setVideoSurfaceHolder(null)
        }
    }
}

/** Small holder that never re-allocates callbacks across recompositions. */
internal class SurfaceAttachState {

    var surfaceView: SurfaceView? = null
    private var attachedController: MediaController? = null

    fun attach(controller: MediaController) {
        attachedController = controller
        surfaceView?.holder?.takeIf { it.surface?.isValid == true }?.let {
            controller.setVideoSurfaceHolder(it)
        }
    }

    val holderCallback = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {
            attachedController?.setVideoSurfaceHolder(holder)
        }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            // Aspect is driven by the renderer; nothing to do.
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            attachedController?.setVideoSurfaceHolder(null)
        }
    }
}
