package com.yt4.app.core.network

import android.content.Context
import android.graphics.Bitmap
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import okhttp3.OkHttpClient

/**
 * Coil configured for minimum heap pressure:
 *
 *  • Reuses the app-wide OkHttp client → same HTTP/2 pool, no second pool.
 *  • Memory cache capped at 20 % of available heap (hard requirement).
 *  • Disk cache capped at 64 MB so cold starts don't refetch thumbnails.
 *  • Default Bitmap.Config = HARDWARE: pixels live in GPU memory and never
 *    count against the Dalvik/Java heap → no GC pauses while scrolling.
 *    Individual request sites additionally pass `precision = Precision.EXACT`
 *    so the decode dimensions match the target view exactly (see VideoCard).
 *  • Crossfade off: a crossfade needs an extra bitmap pass; we'd rather have
 *    the 16 MB of heap back.
 */
object ImageLoaderFactory {

    private const val DISK_CACHE_BYTES = 64L * 1024 * 1024

    fun create(context: Context, httpClient: OkHttpClient): ImageLoader =
        ImageLoader.Builder(context)
            .okHttpClient(httpClient)
            .memoryCache {
                MemoryCache.Builder(context)
                    .maxSizePercent(0.20) // strict 20 % heap cap
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("coil"))
                    .maxSizeBytes(DISK_CACHE_BYTES)
                    .build()
            }
            .bitmapConfig(Bitmap.Config.HARDWARE) // GPU-resident bitmaps
            .allowHardware(true)
            .crossfade(false)
            .respectCacheHeaders(false) // googlevideo thumbnails send no useful CC headers
            .build()
}
