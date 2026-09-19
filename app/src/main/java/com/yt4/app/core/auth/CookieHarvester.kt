package com.yt4.app.core.auth

/**
 * Parses raw `Cookie:` header strings emitted by [android.webkit.CookieManager]
 * and decides whether a full authenticated YouTube session is present.
 *
 * Pure functions, no allocations beyond the result map — cheap enough to run
 * on every `onPageFinished` during the sign-in WebView flow.
 */
object CookieHarvester {

    /** The six cookies that constitute a usable InnerTube session. */
    val REQUIRED_COOKIES = setOf("SID", "HSID", "SSID", "APISID", "SAPISID", "LOGIN_INFO")

    /**
     * Parses `name=value; name2=value2` into a map.
     * Values may themselves contain `=` (base64), so split on the first `=` only.
     */
    fun parse(cookieHeader: String?): Map<String, String> {
        if (cookieHeader.isNullOrBlank()) return emptyMap()
        val out = HashMap<String, String>(16)
        for (part in cookieHeader.split(';')) {
            val trimmed = part.trim()
            val eq = trimmed.indexOf('=')
            if (eq <= 0) continue
            val name = trimmed.substring(0, eq).trim()
            val value = trimmed.substring(eq + 1).trim()
            if (name.isNotEmpty() && value.isNotEmpty()) out[name] = value
        }
        return out
    }

    /**
     * Returns only the session cookies — and only when ALL of them are
     * present. Partial sets (e.g. pre-login tracking cookies) return null so
     * the WebView keeps running until login truly completes.
     */
    fun extractSession(cookieHeader: String?): Map<String, String>? {
        val all = parse(cookieHeader)
        if (all.keys.containsAll(REQUIRED_COOKIES)) {
            return all.filterKeys { it in REQUIRED_COOKIES }
        }
        return null
    }
}
