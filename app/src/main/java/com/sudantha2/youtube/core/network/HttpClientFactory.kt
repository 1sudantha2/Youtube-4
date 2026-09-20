package com.sudantha2.youtube.core.network

import android.content.Context
import java.util.concurrent.TimeUnit
import okhttp3.Cache
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Protocol

/**
 * One OkHttpClient to rule them all.
 *
 * Every byte this app moves — InnerTube RPCs, thumbnails (via Coil), stream
 * manifests and media segments (via Media3's OkHttpDataSource), NewPipe
 * extractions — goes through this single instance so we get:
 *
 *  • HTTP/2 multiplexing: one TCP+TLS connection per host carries all
 *    concurrent requests (InnerTube + segments), cutting TLS handshakes,
 *    radio wake-ups and CPU dramatically.
 *  • A shared connection pool: sockets are recycled across feeds, player
 *    and image loads instead of churned.
 *  • Transparent GZIP: OkHttp's bridge adds `Accept-Encoding: gzip` and
 *    decompresses for us whenever we don't set the header ourselves.
 *  • A bounded disk cache for JSON responses (media bypasses it).
 */
object HttpClientFactory {

    private const val DISK_CACHE_BYTES = 8L * 1024 * 1024 // 8 MB of JSON, nothing more.

    fun create(context: Context): OkHttpClient =
        OkHttpClient.Builder()
            // HTTP/2 first, HTTP/1.1 fallback. OkHttp negotiates via ALPN.
            .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
            // 8 idle conns kept warm for 3 minutes — covers googlevideo.com
            // segment hosts + InnerTube without burning sockets on budget
            // devices.
            .connectionPool(ConnectionPool(8, 3, TimeUnit.MINUTES))
            // TTL-bounded DNS cache in front of the system resolver.
            .dns(CachingDns())
            .cache(Cache(context.cacheDir.resolve("http"), DISK_CACHE_BYTES))
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            // Retry once on a dead pooled socket (cheap), never blindly retry POSTs.
            .retryOnConnectionFailure(true)
            .build()
}
