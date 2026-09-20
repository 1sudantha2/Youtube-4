package com.sudantha2.youtube

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.sudantha2.youtube.player.BackgroundAudioManager
import com.sudantha2.youtube.player.PlayerConnection
import com.sudantha2.youtube.ui.nav.AppNavHost
import com.sudantha2.youtube.ui.nav.BottomBar
import com.sudantha2.youtube.ui.theme.YT4Theme
import kotlinx.coroutines.launch

/**
 * Single-Activity shell.
 *
 * Responsibilities:
 *  • bind/release the [PlayerConnection] around the visible lifetime,
 *  • drive [BackgroundAudioManager] transitions (decoder release on
 *    background, audio-only continuation),
 *  • request the media notification permission once on API 33+.
 */
class MainActivity : ComponentActivity() {

    private lateinit var connection: PlayerConnection
    private val backgroundAudio = BackgroundAudioManager()

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* result ignored */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        connection = PlayerConnection(this)

        // When a controller becomes available, re-arm the ended-state
        // listener and undo any audio-only swap once we're visible again.
        lifecycleScope.launch {
            connection.controller.collect { controller ->
                if (controller != null) {
                    backgroundAudio.watch(controller)
                    backgroundAudio.onForegrounded(controller)
                }
            }
        }

        setContent {
            YT4Theme {
                RequestNotificationPermissionOnce()

                val navController = rememberNavController()
                val backStack by navController.currentBackStackEntryAsState()
                val currentRoute = backStack?.destination?.route

                // No bottom bar on immersive destinations.
                val showBottomBar = remember(currentRoute) {
                    currentRoute != null &&
                        !currentRoute.startsWith("player/") &&
                        currentRoute != "signin"
                }

                Scaffold(
                    bottomBar = {
                        if (showBottomBar) BottomBar(navController = navController, currentRoute = currentRoute)
                    },
                ) { padding ->
                    AppNavHost(
                        navController = navController,
                        connection = connection,
                        backgroundAudio = backgroundAudio,
                        modifier = Modifier.padding(padding),
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        connection.connect()
    }

    override fun onStop() {
        // Swap to audio-only BEFORE releasing the controller — the swap
        // itself releases the video decoder inside the service.
        connection.controller.value?.let(backgroundAudio::onBackgrounded)
        connection.disconnect()
        super.onStop()
    }

    @androidx.compose.runtime.Composable
    private fun RequestNotificationPermissionOnce() {
        LaunchedEffect(Unit) {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
