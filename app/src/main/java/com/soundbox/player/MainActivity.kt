package com.soundbox.player

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.soundbox.player.ui.HomeScreen
import com.soundbox.player.ui.ImportScreen
import com.soundbox.player.ui.LibraryScreen
import com.soundbox.player.ui.MeScreen
import com.soundbox.player.ui.PlayerScreen
import com.soundbox.player.ui.PlaylistScreen
import com.soundbox.player.ui.SettingsScreen
import com.soundbox.player.ui.WallpaperBackground
import com.soundbox.player.ui.WallpaperScreen
import com.soundbox.player.ui.theme.SoundBoxTheme

class MainActivity : androidx.activity.ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = applicationContext as App
        setContent {
            SoundBoxTheme {
                AppRoot(app)
            }
        }
    }
}

@Composable
fun AppRoot(app: App) {
    val nav = rememberNavController()
    val currentRoute = nav.currentBackStackEntryAsState().value?.destination?.route
    val showBar = currentRoute != "player"
    val wp by app.wallpaperConfig.collectAsStateWithLifecycle()

    // 通知栏播放控制需要 POST_NOTIFICATIONS 权限（Android 13+）。非阻塞请求。
    val postNotifLauncher = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { }

    LaunchedEffect(Unit) {
        app.player.connect()
        app.repository.refresh()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    app, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                postNotifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        WallpaperBackground(app)
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background.copy(
                alpha = (1f - wp.opacity).coerceIn(0f, 1f),
            ),
            bottomBar = { if (showBar) AppBottomBar(nav) },
        ) { inner ->
            NavHost(
                nav,
                startDestination = "home",
                modifier = Modifier.fillMaxSize().padding(inner),
            ) {
                composable("home") { HomeScreen(app) }
                composable("library") { LibraryScreen(app) }
                composable("playlists") { PlaylistScreen(app) }
                composable("me") {
                    MeScreen(
                        app,
                        onImport = { nav.navigate("import") },
                        onSettings = { nav.navigate("settings") },
                        onOpenPlayer = { nav.navigate("player") },
                    )
                }
                composable("import") { ImportScreen(app, onBack = { nav.popBackStack() }) }
                composable("settings") {
                    SettingsScreen(
                        app,
                        onBack = { nav.popBackStack() },
                        onOpenWallpaper = { nav.navigate("wallpaper") },
                    )
                }
                composable("wallpaper") { WallpaperScreen(app, onBack = { nav.popBackStack() }) }
                composable("player") { PlayerScreen(app, onBack = { nav.popBackStack() }) }
            }
        }
    }
}

private data class TabItem(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    TabItem("home", "歌曲", Icons.Filled.MusicNote),
    TabItem("library", "曲库", Icons.Filled.LibraryMusic),
    TabItem("playlists", "歌单", Icons.Filled.QueueMusic),
    TabItem("me", "我的", Icons.Filled.Person),
)

@Composable
private fun AppBottomBar(nav: androidx.navigation.NavHostController) {
    val navBackStackEntry by nav.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    NavigationBar {
        TABS.forEach { tab ->
            val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
            NavigationBarItem(
                selected = selected,
                onClick = {
                    nav.navigate(tab.route) {
                        popUpTo(nav.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(tab.icon, contentDescription = tab.label) },
                label = { Text(tab.label) },
            )
        }
    }
}
