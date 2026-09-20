package com.sudantha2.youtube.core.di

import android.content.Context
import com.sudantha2.youtube.core.auth.AuthRepository
import com.sudantha2.youtube.core.innertube.InnerTubeApi
import com.sudantha2.youtube.core.network.HttpClientFactory
import com.sudantha2.youtube.core.network.ImageLoaderFactory
import com.sudantha2.youtube.core.network.NewPipeInitializer
import com.sudantha2.youtube.core.persistence.SecureStore
import com.sudantha2.youtube.core.persistence.SettingsStore
import com.sudantha2.youtube.data.repo.FeedRepository
import com.sudantha2.youtube.data.repo.PlayerRepository
import coil.ImageLoader
import okhttp3.OkHttpClient

/**
 * Hand-rolled service locator.
 *
 * Why not Hilt/Dagger: code-generated DI wiring costs ~1–2 MB of dex and a
 * measurable amount of startup time. For an app with ~10 singletons the
 * trade-off is never worth it. Every entry is a thread-safe `by lazy`.
 */
object ServiceLocator {

    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /** Single HTTP/2 client for data, images and media segments. */
    val httpClient: OkHttpClient by lazy { HttpClientFactory.create(appContext) }

    /** Encrypted-at-rest store for session cookies. */
    val secureStore: SecureStore by lazy { SecureStore(appContext) }

    /** Cheap, non-encrypted preferences (quality default, theme, …). */
    val settingsStore: SettingsStore by lazy { SettingsStore(appContext) }

    /** Cookie-backed session state + SAPISIDHASH generation. */
    val authRepository: AuthRepository by lazy { AuthRepository(secureStore) }

    /** Raw InnerTube transport. */
    val innerTubeApi: InnerTubeApi by lazy { InnerTubeApi(httpClient, authRepository) }

    val feedRepository: FeedRepository by lazy { FeedRepository(innerTubeApi) }

    val playerRepository: PlayerRepository by lazy {
        // NPE init is deferred to first playback — it loads Rhino/jsoup classes.
        NewPipeInitializer.ensureInitialized(httpClient)
        PlayerRepository()
    }

    /** App-wide Coil loader: HARDWARE bitmaps + 20 % heap cache cap. */
    val imageLoader: ImageLoader by lazy { ImageLoaderFactory.create(appContext, httpClient) }

    /** Call before any NewPipeExtractor use outside [playerRepository]. */
    fun ensureNewPipe() {
        NewPipeInitializer.ensureInitialized(httpClient)
    }
}
