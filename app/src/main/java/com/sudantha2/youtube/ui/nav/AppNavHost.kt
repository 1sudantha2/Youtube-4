package com.sudantha2.youtube.ui.nav

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.sudantha2.youtube.core.di.ServiceLocator
import com.sudantha2.youtube.player.BackgroundAudioManager
import com.sudantha2.youtube.player.PlayerConnection
import com.sudantha2.youtube.ui.auth.SignInScreen
import com.sudantha2.youtube.ui.feed.FeedScreen
import com.sudantha2.youtube.ui.feed.FeedKind
import com.sudantha2.youtube.ui.home.HomeScreen
import com.sudantha2.youtube.ui.player.PlayerScreen

object Routes {
    const val HOME = "home"
    const val SUBSCRIPTIONS = "subscriptions"
    const val LIBRARY = "library"
    const val SIGN_IN = "signin"
    const val PLAYER = "player/{videoId}"

    fun player(videoId: String) = "player/$videoId"
}

@Composable
fun AppNavHost(
    navController: NavHostController,
    connection: PlayerConnection,
    backgroundAudio: BackgroundAudioManager,
    modifier: Modifier = Modifier,
) {
    val controller by connection.controller.collectAsStateWithLifecycle()

    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        modifier = modifier.fillMaxSize(),
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                repository = ServiceLocator.feedRepository,
                onVideoClick = { videoId -> navController.navigate(Routes.player(videoId)) },
                onRefreshFeeds = { /* reserved: swipe-refresh triggers repo reload */ },
            )
        }

        composable(Routes.SUBSCRIPTIONS) {
            FeedScreen(
                kind = FeedKind.SUBSCRIPTIONS,
                repository = ServiceLocator.feedRepository,
                onVideoClick = { videoId -> navController.navigate(Routes.player(videoId)) },
            )
        }

        composable(Routes.LIBRARY) {
            FeedScreen(
                kind = FeedKind.LIBRARY,
                repository = ServiceLocator.feedRepository,
                onVideoClick = { videoId -> navController.navigate(Routes.player(videoId)) },
            )
        }

        composable(Routes.SIGN_IN) {
            SignInScreen(
                authRepository = ServiceLocator.authRepository,
                onDone = { navController.popBackStack() },
            )
        }

        composable(
            route = Routes.PLAYER,
            arguments = listOf(navArgument("videoId") { type = NavType.StringType }),
        ) { entry ->
            val videoId = entry.arguments?.getString("videoId").orEmpty()
            PlayerScreen(
                videoId = videoId,
                controller = controller,
                backgroundAudio = backgroundAudio,
                onBack = { navController.popBackStack() },
                onVideoClick = { next -> navController.navigate(Routes.player(next)) },
            )
        }
    }
}
