package com.sudantha2.youtube.core.auth

import java.security.MessageDigest

/**
 * Computes YouTube's `SAPISIDHASH` authorization header.
 *
 * The header is:
 *
 * ```
 * Authorization: SAPISIDHASH <unix_ts>_<SHA1("<unix_ts> <SAPISID> https://www.youtube.com")>
 * ```
 *
 * The digest is recomputed per request with a fresh timestamp — a captured
 * header expires, but the SAPISID cookie is long-lived, which is exactly why
 * we store the cookie (encrypted) and derive headers on demand.
 */
object SapisidHash {

    private const val ORIGIN = "https://www.youtube.com"
    private const val PREFIX = "SAPISIDHASH"

    /** Hex-encoded SHA-1 of `<timestamp> <sapisid> <origin>`. */
    fun computeHash(sapisid: String, unixTimestampSec: Long = System.currentTimeMillis() / 1000L): String {
        val payload = "$unixTimestampSec $sapisid $ORIGIN"
        val digest = MessageDigest.getInstance("SHA-1").digest(payload.toByteArray(Charsets.UTF_8))
        return buildString(digest.size * 2) {
            for (b in digest) {
                append(HEX_CHARS[(b.toInt() shr 4) and 0x0F])
                append(HEX_CHARS[b.toInt() and 0x0F])
            }
        }
    }

    /** Full header value, e.g. `SAPISIDHASH 1711036800_a1b2c3…`. */
    fun buildHeader(sapisid: String): String {
        val ts = System.currentTimeMillis() / 1000L
        return "$PREFIX ${ts}_${computeHash(sapisid, ts)}"
    }

    private val HEX_CHARS = "0123456789abcdef".toCharArray()
}
