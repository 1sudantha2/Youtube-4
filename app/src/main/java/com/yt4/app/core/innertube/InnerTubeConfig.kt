package com.yt4.app.core.innertube

/**
 * InnerTube client identity.
 *
 * We present as the official ANDROID client: the API key below is the public
 * key shipped inside the official Android app, the endpoints accept it
 * without OAuth, and — critically — the ANDROID client is served the
 * `videoId`/stream metadata shapes NewPipeExtractor understands.
 */
object InnerTubeConfig {

    const val BASE_URL = "https://www.youtube.com/youtubei/v1"

    const val API_KEY = "AIzaSyA8eiZmM1FaDVjRy-df2KTyQ_vz_yYM39w"

    const val CLIENT_NAME = "ANDROID"
    const val CLIENT_VERSION = "19.09.37"

    /** Client id used in X-Youtube-Client-Name (3 == Android app). */
    const val CLIENT_ID = "3"

    const val USER_AGENT =
        "com.google.android.youtube/19.09.37 (Linux; U; Android 14; US) gzip"

    // Well-known browseIds for the main feeds.
    const val BROWSE_HOME = "FEwhat_to_watch"
    const val BROWSE_SUBSCRIPTIONS = "FEsubscriptions"
    const val BROWSE_LIBRARY = "FElibrary"

    /** Liked videos playlist. */
    const val PLAYLIST_LIKED = "VLLM"
}
