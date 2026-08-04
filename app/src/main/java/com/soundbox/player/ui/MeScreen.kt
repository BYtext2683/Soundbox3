package com.soundbox.player.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.soundbox.player.App
import com.soundbox.player.data.formatDuration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeScreen(
    app: App,
    onImport: () -> Unit,
    onSettings: () -> Unit,
    onOpenPlayer: () -> Unit,
) {
    val playerState by app.player.state.collectAsStateWithLifecycle()
    val tracks by app.repository.tracks.collectAsStateWithLifecycle()
    val currentTrack = remember(playerState.currentId, tracks) {
        tracks.firstOrNull { it.id == playerState.currentId }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("我的") },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
        )

        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (playerState.currentId != null) {
                Card(Modifier.fillMaxWidth().clickable(onClick = onOpenPlayer)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (playerState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(
                                "正在播放：${playerState.title.ifBlank { "未知曲目" }}",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            val sub = buildString {
                                append("已播放 ${currentTrack?.playCount ?: 0} 次")
                                val dur = currentTrack?.playDurationMs ?: 0L
                                if (dur > 0) append(" · 累计 ${formatDuration(dur)}")
                                if (!playerState.isPlaying) append("（已暂停）")
                            }
                            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            Card(Modifier.fillMaxWidth().clickable(onClick = onImport)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.FolderOpen, null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.padding(start = 12.dp)) {
                        Text("导入音乐", style = MaterialTheme.typography.bodyLarge)
                        Text("从手机选择文件夹或文件，手动加入曲库", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Card(Modifier.fillMaxWidth().clickable(onClick = onSettings)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Settings, null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.padding(start = 12.dp)) {
                        Text("设置", style = MaterialTheme.typography.bodyLarge)
                        Text("过滤、默认播放顺序与排序", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.MusicNote, null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.padding(start = 12.dp)) {
                        Text("当前播放顺序：${playerState.order.label}", style = MaterialTheme.typography.bodyLarge)
                        Text("在「设置」里可更改默认顺序", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
