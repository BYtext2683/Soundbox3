package com.soundbox.player.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.soundbox.player.data.Playlist
import com.soundbox.player.data.Track
import com.soundbox.player.ui.components.ArtworkImage
import com.soundbox.player.ui.components.EmptyState
import com.soundbox.player.ui.components.PlaylistPicker
import com.soundbox.player.ui.components.TrackRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistScreen(app: App) {
    val playlists by app.playlists.playlists.collectAsStateWithLifecycle()
    val playerState by app.player.state.collectAsStateWithLifecycle()

    var openId by remember { mutableStateOf<String?>(null) }
    val open = playlists.firstOrNull { it.id == openId }

    Column(Modifier.fillMaxSize()) {
        if (open == null) {
            TopAppBar(
                title = { Text("歌单") },
                actions = {
                    IconButton(onClick = { openId = app.playlists.create("我的歌单 ${playlists.size + 1}") }) {
                        Icon(Icons.Filled.Add, contentDescription = "新建歌单")
                    }
                },
            )
            if (playlists.isEmpty()) {
                EmptyState("还没有歌单，点右上角 + 新建", Modifier.weight(1f))
            } else {
                LazyColumn(Modifier.weight(1f)) {
                    items(playlists, key = { it.id }) { pl ->
                        PlaylistCard(pl) { openId = pl.id }
                    }
                }
            }
        } else {
            PlaylistDetail(app, open, playerState.currentId) { openId = null }
        }
    }
}

@Composable
private fun PlaylistCard(pl: Playlist, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.LibraryMusic,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = androidx.compose.ui.Modifier.size(40.dp),
        )
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(pl.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${pl.trackIds.size} 首", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaylistDetail(
    app: App,
    playlist: Playlist,
    currentId: String?,
    onBack: () -> Unit,
) {
    val store = app.playlists
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf(playlist.name) }

    val tracks: List<Track> = remember(playlist.trackIds) {
        app.repository.tracksByIds(playlist.trackIds)
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(playlist.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null) } },
            actions = {
                IconButton(onClick = { app.player.playAll(tracks, 0) }) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "播放全部")
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, null) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("按标题排序") },
                            leadingIcon = { Icon(Icons.Filled.SortByAlpha, null) },
                            onClick = {
                                menu = false
                                val sorted = tracks.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
                                store.setOrder(playlist.id, sorted.map { it.id })
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("重命名") },
                            leadingIcon = { Icon(Icons.Filled.Edit, null) },
                            onClick = { menu = false; renaming = true; renameText = playlist.name },
                        )
                        DropdownMenuItem(
                            text = { Text("删除歌单") },
                            leadingIcon = { Icon(Icons.Filled.Delete, null) },
                            onClick = { menu = false; store.delete(playlist.id); onBack() },
                        )
                    }
                }
            },
        )

        if (tracks.isEmpty()) {
            EmptyState("歌单是空的，去歌曲或曲库里「加入歌单」", Modifier.weight(1f))
        } else {
            PlaylistPicker(app.playlists) { onAdd ->
                LazyColumn(Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 8.dp)) {
                    items(tracks, key = { it.id }) { track: Track ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TrackRow(
                                track = track,
                                modifier = Modifier.weight(1f),
                                highlight = track.id == currentId,
                                onPlay = {
                                    val idx = tracks.indexOfFirst { t -> t.id == track.id }
                                    app.player.playAll(tracks, idx.coerceAtLeast(0))
                                },
                                onPlayNext = { app.player.playNext(listOf(track)) },
                                onAddToQueue = { app.player.addToQueue(listOf(track)) },
                                onAddToPlaylist = { onAdd(track) },
                                extraMenu = listOf(
                                    com.soundbox.player.ui.components.MenuItemData("移除出歌单") {
                                        val i = tracks.indexOfFirst { t -> t.id == track.id }
                                        if (i >= 0) store.removeAt(playlist.id, i)
                                    },
                                ),
                            )
                            Column {
                                val i = tracks.indexOfFirst { t -> t.id == track.id }
                                IconButton(
                                    enabled = i > 0,
                                    onClick = { store.move(playlist.id, i, i - 1) },
                                ) { Icon(Icons.Filled.ArrowUpward, null) }
                                IconButton(
                                    enabled = i < tracks.lastIndex,
                                    onClick = { store.move(playlist.id, i, i + 1) },
                                ) { Icon(Icons.Filled.ArrowDownward, null) }
                            }
                        }
                    }
                }
            }
        }
    }

    if (renaming) {
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("重命名歌单") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = { store.rename(playlist.id, renameText.trim()); renaming = false }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("取消") } },
        )
    }
}
