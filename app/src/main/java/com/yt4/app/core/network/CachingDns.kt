package com.yt4.app.core.network

import java.net.InetAddress
import okhttp3.Dns

/**
 * LRU-ish DNS cache with per-entry TTL.
 *
 * Segment hosts on `*.googlevideo.com` resolve to rotating CDNs; re-resolving
 * every few requests wastes battery and adds latency. We cache for 60 s —
 * long enough to amortize lookups, short enough to follow CDN rotation.
 */
class CachingDns(
    private val upstream: Dns = Dns.SYSTEM,
    private val ttlMs: Long = 60_000L,
) : Dns {

    private data class Entry(val addresses: List<InetAddress>, val expiresAt: Long)

    private val cache = LinkedHashMap<String, Entry>(16, 0.75f, true)

    override fun lookup(hostname: String): List<InetAddress> {
        val now = System.currentTimeMillis()
        synchronized(cache) {
            cache[hostname]?.let { entry ->
                if (entry.expiresAt > now) return entry.addresses
                cache.remove(hostname)
            }
        }
        // Resolve off the lock — never hold it across a syscall.
        val addresses = upstream.lookup(hostname)
        if (addresses.isNotEmpty()) {
            synchronized(cache) {
                // Hard cap: evict the eldest beyond 64 entries.
                if (cache.size >= 64) cache.remove(cache.keys.first())
                cache[hostname] = Entry(addresses, now + ttlMs)
            }
        }
        return addresses
    }
}
