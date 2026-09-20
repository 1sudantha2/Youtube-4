package com.sudantha2.youtube.data.repo

import com.sudantha2.youtube.core.di.ServiceLocator
import com.sudantha2.youtube.core.innertube.InnerTubeApi
import com.sudantha2.youtube.core.innertube.InnerTubeConfig
import com.sudantha2.youtube.data.model.CommentsPage
import com.sudantha2.youtube.data.model.FeedPage
import com.sudantha2.youtube.data.model.WatchNextResult

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
