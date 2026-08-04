package com.soundbox.player.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.soundbox.player.App
import com.soundbox.player.data.PlayOrder
import com.soundbox.player.data.SortMode

/** 与 tools/gen_keystore.py 生成的固定签名一致，用于让用户核对 APK 来源。 */
const val SIGNING_FINGERPRINT =
    "A4:1C:23:1B:CD:FD:DB:BC:02:B4:5C:1E:EF:B1:75:10:A3:AF:96:14:95:AA:B8:71:DC:EB:12:3C:EC:C7:44:17"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(app: App, onBack: () -> Unit) {
    val playerState by app.player.state.collectAsStateWithLifecycle()
    var minDur by remember { mutableStateOf(app.prefs.minDurationSec) }
    var order by remember { mutableStateOf(playerState.order) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("设置") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null) } },
        )

        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // 最短时长过滤
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("过滤过短的片段", style = MaterialTheme.typography.bodyLarge)
                    Text("导入的音频低于该时长不进入曲库（秒）。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0, 10, 30, 60).forEach { sec ->
                            val selected = minDur == sec
                            OutlinedButton(
                                onClick = {
                                    minDur = sec
                                    app.prefs.minDurationSec = sec
                                    app.repository.refresh()
                                },
                                modifier = Modifier.padding(0.dp),
                            ) {
                                Text(if (sec == 0) "不过滤" else "${sec}s")
                            }
                        }
                    }
                }
            }

            // 默认播放顺序
            Card(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("默认播放顺序", style = MaterialTheme.typography.bodyLarge)
                        Text(order.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = {
                        order = app.player.cycleOrder()
                    }) {
                        Icon(orderIcon(order), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                    }
                }
            }

            // 默认排序
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("歌曲列表默认排序", style = MaterialTheme.typography.bodyLarge)
                    val sort = SortMode.of(app.prefs.sortModeOrdinal)
                    Text(sort.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SortMode.entries.forEach { m ->
                            OutlinedButton(onClick = { app.prefs.sortModeOrdinal = m.ordinal }) {
                                Text(m.label)
                            }
                        }
                    }
                }
            }

            // 关于
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Info, null, tint = MaterialTheme.colorScheme.primary)
                        Text("  关于 SoundBox", style = MaterialTheme.typography.bodyLarge)
                    }
                    Text(
                        "版本 1.0.0  ·  安卓 8.0+  ·  原生解码器",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Text(
                        "APK 签名指纹 (SHA-256)：\n$SIGNING_FINGERPRINT",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Text(
                        "统一签名保证每次云端编译出的安装包都能直接覆盖升级，无需卸载旧版。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

private fun orderIcon(order: PlayOrder) = when (order) {
    PlayOrder.SHUFFLE -> Icons.Filled.Shuffle
    PlayOrder.REPEAT_ONE -> Icons.Filled.RepeatOne
    else -> Icons.Filled.Repeat
}
