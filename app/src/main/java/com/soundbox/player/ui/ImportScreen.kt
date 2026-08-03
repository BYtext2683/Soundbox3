package com.soundbox.player.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.soundbox.player.App
import com.soundbox.player.data.AudioFormats

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(app: App, onBack: () -> Unit) {
    val scanning by app.repository.scanning.collectAsStateWithLifecycle()

    var reloadKey by remember { mutableStateOf(0) }
    val trees = remember(reloadKey) { app.prefs.importedTrees.toList() }
    val files = remember(reloadKey) { app.prefs.importedFiles.toList() }

    val treeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            app.repository.addTree(it)
            app.repository.refresh()
            reloadKey++
        }
    }
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentMultiple()) { result ->
        val uris = result ?: emptyList()
        if (uris.isNotEmpty()) {
            app.repository.addFiles(uris)
            app.repository.refresh()
            reloadKey++
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("导入音乐") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null) } },
        )

        LazyColumn(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp)) {
            item {
                Text(
                    "把手机里的音乐加进 SoundBox。两种方式都会被长期记住（App 获得文件访问授权），重装前无需重新导入。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }

            item {
                OutlinedButton(
                    onClick = { treeLauncher.launch(null) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.FolderOpen, null)
                    Text("  导入整个文件夹", modifier = Modifier.padding(start = 8.dp))
                }
            }
            item {
                OutlinedButton(
                    onClick = { fileLauncher.launch(arrayOf("audio/*")) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) {
                    Icon(Icons.Filled.InsertDriveFile, null)
                    Text("  选择单个 / 多个文件", modifier = Modifier.padding(start = 8.dp))
                }
            }

            item { SectionTitle("已导入的文件夹 (${trees.size})") }
            items(trees) { raw ->
                val label = runCatching {
                    Uri.parse(raw).let { it.lastPathSegment?.substringAfterLast('/')?.substringAfter(':') ?: raw }
                }.getOrDefault(raw)
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.FolderOpen, null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        label,
                        Modifier.weight(1f).padding(start = 12.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    IconButton(onClick = { app.repository.removeTree(raw); reloadKey++ }) {
                        Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            item { SectionTitle("已导入的文件 (${files.size})") }
            items(files) { raw ->
                val label = raw.substringAfterLast('/').substringAfterLast(':')
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.InsertDriveFile, null, tint = MaterialTheme.colorScheme.outline)
                    Text(
                        label,
                        Modifier.weight(1f).padding(start = 12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            item {
                if (files.isNotEmpty() || trees.isNotEmpty()) {
                    OutlinedButton(
                        onClick = { app.repository.clearImports(); reloadKey++ },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    ) {
                        Icon(Icons.Filled.Delete, null)
                        Text("  清除全部导入", modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }

            item {
                Text(
                    "支持的格式：${AudioFormats.SUPPORTED.joinToString(", ").uppercase()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )
}
