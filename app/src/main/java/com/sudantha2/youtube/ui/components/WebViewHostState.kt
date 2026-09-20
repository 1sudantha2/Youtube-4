package com.sudantha2.youtube.ui.components

import android.view.ViewGroup
import android.webkit.WebView

/**
 * Owns the full destruction protocol for a sign-in [WebView].
 *
 * WebView instances keep native renderers, the cookie-observer thread and a
 * Chromium task queue alive until [WebView.destroy] — forgetting any step
 * leaks several MB per sign-in. This class makes the teardown sequence
 * impossible to get wrong:
 *
 *   stopLoading → detach from parent → clear history → destroy
 */
class WebViewHostState {

    private var webView: WebView? = null
    private var destroyed = false

    fun attach(view: WebView) {
        if (destroyed) {
            // The composable raced with teardown; never reuse.
            view.destroy()
            return
        }
        webView = view
    }

    /** Idempotent. Safe to call from callbacks and DisposableEffect. */
    fun destroy() {
        if (destroyed) return
        destroyed = true
        val view = webView ?: return
        webView = null
        view.stopLoading()
        view.webViewClient = android.webkit.WebViewClient() // drop our callbacks
        (view.parent as? ViewGroup)?.removeView(view)
        view.clearHistory()
        view.destroy()
    }
}
