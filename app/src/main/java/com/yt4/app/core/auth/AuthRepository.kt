package com.yt4.app.core.auth

import android.webkit.CookieManager
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.yt4.app.core.persistence.SecureStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Signed-in state exposed to the UI. */
@Immutable
sealed interface AuthState {

    @Immutable
    data object SignedOut : AuthState

    @Immutable
    data class SignedIn(val accountHint: String?) : AuthState
}

/**
 * Owns the YouTube session: cookie storage, header derivation, sign-out.
 *
 * Threading note: cookie maps are immutable snapshots swapped atomically via
 * the StateFlow — readers never see a torn session.
 */
@Stable
class AuthRepository(private val secureStore: SecureStore) {

    @Volatile
    private var cookies: Map<String, String>? = null

    private val _state = MutableStateFlow<AuthState>(AuthState.SignedOut)
    val state: StateFlow<AuthState> = _state.asStateFlow()

    init {
        // Restore a previous session (cheap: single encrypted-prefs read).
        secureStore.loadSessionCookies()?.takeIf { it.isNotEmpty() }?.let {
            cookies = it
            _state.value = AuthState.SignedIn(accountHintFrom(it))
        }
    }

    val isSignedIn: Boolean
        get() = cookies != null

    /** Called by the WebView capture flow once all six cookies are present. */
    fun saveSessionCookies(captured: Map<String, String>) {
        cookies = captured
        secureStore.saveSessionCookies(captured)
        _state.value = AuthState.SignedIn(accountHintFrom(captured))
    }

    fun signOut() {
        cookies = null
        secureStore.clear()
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        _state.value = AuthState.SignedOut
    }

    /** `Authorization: SAPISIDHASH …` for InnerTube calls, or null if signed out. */
    fun authorizationHeader(): String? =
        cookies?.get("SAPISID")?.let(SapisidHash::buildHeader)

    /** `Cookie:` header value replaying the session cookies, or null. */
    fun cookieHeader(): String? =
        cookies?.entries?.joinToString(separator = "; ") { (k, v) -> "$k=$v" }

    private fun accountHintFrom(map: Map<String, String>): String? =
        // LOGIN_INFO often embeds the account email in its payload; keep it
        // opaque — we only show "signed in" in the UI.
        if (map.containsKey("LOGIN_INFO")) "account" else null
}
