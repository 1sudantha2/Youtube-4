package com.yt4.app.ui.auth

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yt4.app.core.auth.AuthRepository
import com.yt4.app.core.auth.AuthState
import com.yt4.app.core.auth.CookieHarvester
import com.yt4.app.ui.components.WebViewHostState

/**
 * In-app Google sign-in.
 *
 * Flow:
 *  1. A bare [WebView] loads the official YouTube sign-in entry point.
 *  2. After every page load we read the cookie jar via [CookieManager] and
 *     check for the full six-cookie session set
 *     ([CookieHarvester.REQUIRED_COOKIES]).
 *  3. The moment the set is complete the cookies are persisted (encrypted)
 *     and the WebView is torn down: detached from its parent, loading
 *     stopped, [WebView.destroy] called — nothing survives in memory.
 *
 * The WebView exists ONLY for the duration of sign-in. No cookie is ever
 * used to drive WebView requests afterwards; InnerTube calls replay the
 * cookies through OkHttp with a fresh SAPISIDHASH per request.
 */
@SuppressLint("SetJavaScriptEnabled") // Google's login flow requires JS.
@Composable
fun SignInScreen(
    authRepository: AuthRepository,
    onDone: () -> Unit,
) {
    val auth by authRepository.state.collectAsStateWithLifecycle()
    val hostState = remember { WebViewHostState() }

    // Guaranteed teardown even if the user leaves mid-login.
    DisposableEffect(Unit) {
        onDispose { hostState.destroy() }
    }

    Column(Modifier.fillMaxSize()) {
        Text(
            text = "Sign in",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(16.dp),
        )
        Text(
            text = "Cookies stay on this device, encrypted. No data leaves the app.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        if (auth is AuthState.SignedIn) {
            SignedInPanel(authRepository, onDone)
        } else {
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                factory = { context ->
                    WebView(context).apply {
                        configureForSignIn()
                        hostState.attach(this)
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView, url: String) {
                                val jar = CookieManager.getInstance()
                                    .getCookie("https://www.youtube.com")
                                CookieHarvester.extractSession(jar)?.let { cookies ->
                                    // Capture, persist, then burn the WebView.
                                    authRepository.saveSessionCookies(cookies)
                                    hostState.destroy()
                                }
                            }
                        }
                        loadUrl(SIGN_IN_URL)
                    }
                },
            )
        }
    }
}

@Composable
private fun SignedInPanel(authRepository: AuthRepository, onDone: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        Text(
            text = "You're signed in. Home, subscriptions, likes and comments now use your account.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(vertical = 12.dp),
        )
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
            Text("Continue")
        }
        Button(
            onClick = { authRepository.signOut() },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Sign out")
        }
    }
}

private const val SIGN_IN_URL =
    "https://accounts.google.com/ServiceLogin?service=youtube&continue=https%3A%2F%2Fwww.youtube.com"

private fun WebView.configureForSignIn() {
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    // Phone UA: the mobile login flow is far lighter than desktop's.
    settings.userAgentString =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36"

    CookieManager.getInstance().apply {
        setAcceptCookie(true)
        setAcceptThirdPartyCookies(this@configureForSignIn, true)
        removeAllCookies(null) // fresh session every sign-in attempt
    }
}
