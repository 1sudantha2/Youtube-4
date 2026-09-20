package com.sudantha2.youtube.core.persistence

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * AES-256-GCM-encrypted SharedPreferences for session cookies.
 *
 * Keys: AES256_SIV · Values: AES256_GCM, backed by an Android Keystore
 * resident master key that never leaves secure hardware. The file is also
 * excluded from cloud backup / device transfer (see data_extraction_rules).
 */
class SecureStore(context: Context) {

    private companion object {
        const val FILE_NAME = "youtube_auth"
        const val KEY_COOKIES = "session_cookies"
    }

    private val json = Json { ignoreUnknownKeys = true }

    private val mapSerializer = MapSerializer(String.serializer(), String.serializer())

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun saveSessionCookies(cookies: Map<String, String>) {
        prefs.edit().putString(KEY_COOKIES, json.encodeToString(mapSerializer, cookies)).apply()
    }

    fun loadSessionCookies(): Map<String, String>? {
        val raw = prefs.getString(KEY_COOKIES, null) ?: return null
        return runCatching { json.decodeFromString(mapSerializer, raw) }.getOrNull()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}
