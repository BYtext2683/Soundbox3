package com.soundbox.player

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.soundbox.player.ui.components.MiniPlayer
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

private fun storagePermission(): String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

private fun hasStorage(perms: Map<String, Boolean>): Boolean =
    perms[storagePermission()] == true

private fun buildPermissionRequests(): Array<String> = buildList {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.READ_MEDIA_AUDIO)
        add(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        add(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
}.toTypedArray()

@Composable
fun AppRoot(app: App) {
    val nav = rememberNavController()
    val playerState by app.player.state.collectAsStateWithLifecycle()
    val currentTrack = app.repository.trackById(playerState.currentId)

    val currentRoute = nav.currentBackStackEntryAsState().value?.destination?.route
    val showBar = currentRoute != "player"

    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(app, storagePermission()) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val permLauncher = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions(),
    ) { res ->
        if (hasStorage(res)) {
            granted = true
            app.repository.refresh()
        }
    }

    LaunchedEffect(Unit) {
        app.player.connect()
        if (granted) app.repository.refresh()
    }

    if (!granted) {
        PermissionScreen(onRequest = { permLauncher.launch(buildPermissionRequests()) })
        return
    }

    Scaffold(
        bottomBar = {
            if (showBar) {
                Column {
                    if (playerState.currentId != null) {
                        MiniPlayer(
                            state = playerState,
                            currentTrack = currentTrack,
                            player = app.player,
                            onClick = { nav.navigate("player") },
                        )
                    }
                    AppBottomBar(nav)
                }
            }
        },
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
                )
            }
            composable("import") { ImportScreen(app, onBack = { nav.popBackStack() }) }
            composable("settings") { SettingsScreen(app, onBack = { nav.popBackStack() }) }
            composable("player") { PlayerScreen(app, onBack = { nav.popBackStack() }) }
        }
    }
}

private data class TabItem(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

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

@Composable
private fun PermissionScreen(onRequest: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.MusicNote, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text("SoundBox 需要读取音频的权限", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            "用于扫描本机音乐与导入的文件夹。我们不会上传任何文件，所有播放都在本地完成。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRequest, modifier = Modifier.fillMaxWidth(0.7f)) {
            Text("授予权限")
        }
    }
}
