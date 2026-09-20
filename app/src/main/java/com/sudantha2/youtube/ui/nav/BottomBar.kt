package com.sudantha2.youtube.ui.nav

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Subscriptions
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.sudantha2.youtube.core.di.ServiceLocator

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab(Routes.HOME, "Home", Icons.Outlined.Home),
    Tab(Routes.SUBSCRIPTIONS, "Subs", Icons.AutoMirrored.Outlined.Subscriptions),
    Tab(Routes.LIBRARY, "Library", Icons.Outlined.VideoLibrary),
    Tab(Routes.SIGN_IN, "Account", Icons.Outlined.AccountCircle),
)

@Composable
fun BottomBar(navController: NavHostController, currentRoute: String?) {
    val auth by ServiceLocator.authRepository.state.collectAsStateWithLifecycle()

    NavigationBar(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
    ) {
        for (tab in TABS) {
            NavigationBarItem(
                selected = currentRoute == tab.route,
                onClick = {
                    if (currentRoute != tab.route) {
                        navController.navigate(tab.route) {
                            // Pop up to HOME to avoid stacking duplicate
                            // destinations; keeps the back stack O(1) deep.
                            popUpTo(Routes.HOME) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                icon = { Icon(tab.icon, contentDescription = tab.label) },
                label = {
                    val text = if (tab.route == Routes.SIGN_IN && auth is com.sudantha2.youtube.core.auth.AuthState.SignedIn) {
                        "Me"
                    } else {
                        tab.label
                    }
                    Text(text)
                },
            )
        }
    }
}
