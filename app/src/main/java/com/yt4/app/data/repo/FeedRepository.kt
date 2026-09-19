package com.yt4.app.data.repo

import com.yt4.app.core.di.ServiceLocator
import com.yt4.app.core.innertube.InnerTubeApi
import com.yt4.app.core.innertube.InnerTubeConfig
import com.yt4.app.data.model.CommentsPage
import com.yt4.app.data.model.FeedPage
import com.yt4.app.data.model.WatchNextResult

/**
 * Feed use-cases over InnerTube. Thin on purpose — caching/pagination policy
 * belongs to ViewModels so each screen keeps control of its own lifecycle.
 */
class FeedRepository(private val api: InnerTubeApi = ServiceLocator.innerTubeApi) {

    suspend fun home(): FeedPage = api.browse(InnerTubeConfig.BROWSE_HOME)

    suspend fun homeMore(continuation: String): FeedPage =
        api.browse(InnerTubeConfig.BROWSE_HOME, continuation)

    suspend fun subscriptions(): FeedPage = api.browse(InnerTubeConfig.BROWSE_SUBSCRIPTIONS)

    suspend fun library(): FeedPage = api.browse(InnerTubeConfig.BROWSE_LIBRARY)

    suspend fun likedVideos(): FeedPage = api.browse(InnerTubeConfig.PLAYLIST_LIKED)

    suspend fun watchNext(videoId: String): WatchNextResult = api.watchNext(videoId)

    suspend fun comments(continuation: String): CommentsPage = api.comments(continuation)
}
