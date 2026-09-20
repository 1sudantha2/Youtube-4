package com.sudantha2.youtube.core.network

import java.io.IOException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Response
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Bridges OkHttp's async callback into a cancellable coroutine without
 * allocating an intermediate dispatcher hop. Cancelling the coroutine cancels
 * the underlying socket read.
 */
suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    cont.invokeOnCancellation { cancel() }
    enqueue(object : okhttp3.Callback {
        override fun onResponse(call: Call, response: Response) {
            cont.resume(response)
        }

        override fun onFailure(call: Call, e: IOException) {
            // Don't resume if the collector already left.
            if (!cont.isCancelled) cont.resumeWithException(e)
        }
    })
}
