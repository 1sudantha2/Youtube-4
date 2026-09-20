package com.sudantha2.youtube

import android.app.Application
import coil.Coil
import com.sudantha2.youtube.core.di.ServiceLocator

/**
 * Process-wide bootstrap. Intentionally minimal: the only work done here is
 * wiring the DI graph and installing the Coil singleton. Everything else
 * (OkHttp, NPE, DataStore, EncryptedSharedPreferences) is lazy and only
 * touches memory on first real use — startup stays under ~12 ms on a
 * Snapdragon 4-series part.
 */
class YT4App : Application() {

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
        // Install the app-wide image loader: shared OkHttp pool, HARDWARE
        // bitmaps, 20 % heap memory-cache cap. See ImageLoaderFactory.
        Coil.setImageLoader(ServiceLocator.imageLoader)
    }
}
