package com.soundbox.player.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.soundbox.player.data.SortMode
import com.soundbox.player.data.Track
import com.soundbox.player.data.search
import com.soundbox.player.data.sortedBy
import com.soundbox.player.ui.components.EmptyState
import com.soundbox.player.ui.components.MenuItemData
import com.soundbox.player.ui.components.PlaylistPicker
import com.soundbox.player.ui.components.TrackManageDialogs
import com.soundbox.player.ui.components.TrackRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(app: App) {
    val tracks by app.repository.tracks.collectAsStateWithLifecycle()
    val scanning by app.repository.scanning.collectAsStateWithLifecycle()
    val status by app.repository.status.collectAsStateWithLifecycle()
    val playerState by app.player.state.collectAsStateWithLifecycle()

    var query by remember { mutableStateOf("") }
    var sortMenu by remember { mutableStateOf(false) }
    var sortMode by remember { mutableStateOf(SortMode.of(app.prefs.sortModeOrdinal)) }

    var renameTarget by remember { mutableStateOf<Track?>(null) }
    var deleteTarget by remember { mutableStateOf<Track?>(null) }

    val base = if (query.isBlank()) tracks else tracks.search(query)
    val shown = remember(base, sortMode) { base.sortedBy(sortMode) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("歌曲") },
            actions = {
                IconButton(onClick = { app.repository.refresh() }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "刷新曲库")
                }
                IconButton(onClick = { sortMenu = true }) {
                    Icon(Icons.Filled.Sort, contentDescription = "排序")
                }
                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                    SortMode.entries.forEach { m ->
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (m == sortMode) Icon(Icons.Filled.ArrowUpward, null, tint = MaterialTheme.colorScheme.primary)
                                    Text("  " + m.label)
                                }
                            },
                            onClick = {
                                sortMode = m
                                app.prefs.sortModeOrdinal = m.ordinal
                                sortMenu = false
                            },
                        )
                    }
                }
            },
        )

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("搜索歌曲 / 歌手 / 专辑") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )

        if (scanning && status != null) {
            Text(status ?: "", Modifier.padding(16.dp, 4.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (shown.isEmpty()) {
            EmptyState(if (tracks.isEmpty()) "曲库为空，去「我的」导入音乐" else "没有匹配的歌曲", Modifier.weight(1f))
        } else {
            PlaylistPicker(app.playlists) { onAdd ->
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 8.dp),
                ) {
                    items(shown, key = { it.id }) { track: Track ->
                        TrackRow(
                            track = track,
                            highlight = track.id == playerState.currentId,
                            playCount = track.playCount,
                            onPlay = { app.player.playTrack(track, shown) },
                            onPlayNext = { app.player.playNext(listOf(track)) },
                            onAddToQueue = { app.player.addToQueue(listOf(track)) },
                            onAddToPlaylist = { onAdd(track) },
                            extraMenu = listOf(
                                MenuItemData("重命名") { renameTarget = track },
                                MenuItemData("删除") { deleteTarget = track },
                            ),
                        )
                    }
                }
            }
        }

        TrackManageDialogs(
            renameTarget = renameTarget,
            deleteTarget = deleteTarget,
            onRename = { t, newName ->
                app.repository.renameTrack(t.id, newName)
                renameTarget = null
            },
            onDelete = { t ->
                if (t.id == playerState.currentId) app.player.next()
                app.repository.hideTrack(t.id)
                deleteTarget = null
            },
            onDismissRename = { renameTarget = null },
            onDismissDelete = { deleteTarget = null },
        )
    }
}
