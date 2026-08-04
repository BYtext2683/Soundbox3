package com.soundbox.player.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.soundbox.player.data.ArtworkLoader
import com.soundbox.player.data.Playlist
import com.soundbox.player.data.PlaylistStore
import com.soundbox.player.data.Track
import com.soundbox.player.data.formatDuration
import com.soundbox.player.playback.PlayerController
import com.soundbox.player.playback.PlayerUiState

/** 带占位图的封面。先查内存缓存，没有再异步解码（专辑图 / 内嵌图）。 */
@Composable
fun ArtworkImage(
    track: Track?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    var bitmap by remember(track?.id) { mutableStateOf<Bitmap?>(ArtworkLoader.peek(track)) }
    val context = LocalContext.current
    LaunchedEffect(track?.id) {
        if (track != null && bitmap == null) {
            bitmap = ArtworkLoader.load(context, track)
        }
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = null,
            modifier = modifier,
            contentScale = contentScale,
        )
    } else {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size((if (modifier == Modifier) 24.dp else 28.dp)),
            )
        }
    }
}

data class MenuItemData(val label: String, val onClick: () -> Unit)

/** 单曲行：封面 + 标题 + 副标题 + 时长 + 更多菜单。 */
@Composable
fun TrackRow(
    track: Track,
    modifier: Modifier = Modifier,
    onPlay: () -> Unit,
    onPlayNext: (() -> Unit)? = null,
    onAddToQueue: (() -> Unit)? = null,
    onAddToPlaylist: (() -> Unit)? = null,
    extraMenu: List<MenuItemData> = emptyList(),
    highlight: Boolean = false,
    playCount: Int = 0,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtworkImage(
            track = track,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp)),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (highlight) MaterialTheme.colorScheme.primary else LocalContentColor.current,
            )
            Text(
                text = track.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = formatDuration(track.durationMs),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.outline,
        )
        if (playCount > 0) {
            Spacer(Modifier.width(2.dp))
            Text(
                text = "▶ $playCount",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        Spacer(Modifier.width(4.dp))
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "更多")
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("播放") },
                    leadingIcon = { Icon(Icons.Filled.PlayArrow, null) },
                    onClick = { menu = false; onPlay() },
                )
                onPlayNext?.let { fn ->
                    DropdownMenuItem(
                        text = { Text("下一首播放") },
                        leadingIcon = { Icon(Icons.Filled.PlayArrow, null) },
                        onClick = { menu = false; fn() },
                    )
                }
                onAddToQueue?.let { fn ->
                    DropdownMenuItem(
                        text = { Text("加入播放队列") },
                        leadingIcon = { Icon(Icons.Filled.MusicNote, null) },
                        onClick = { menu = false; fn() },
                    )
                }
                onAddToPlaylist?.let { fn ->
                    DropdownMenuItem(
                        text = { Text("加入歌单…") },
                        leadingIcon = { Icon(Icons.Filled.LibraryMusic, null) },
                        onClick = { menu = false; fn() },
                    )
                }
                extraMenu.forEach { item ->
                    DropdownMenuItem(
                        text = { Text(item.label) },
                        onClick = { menu = false; item.onClick() },
                    )
                }
            }
        }
    }
}

/** 底部迷你播放条。点击进入完整播放页。 */
@Composable
fun MiniPlayer(
    state: PlayerUiState,
    currentTrack: Track?,
    player: PlayerController,
    onClick: () -> Unit,
) {
    Surface(
        tonalElevation = 3.dp,
        shadowElevation = 4.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ArtworkImage(
                track = currentTrack,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp)),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    state.title.ifBlank { "未播放" },
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    state.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = { player.togglePlayPause() }) {
                Icon(
                    if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (state.isPlaying) "暂停" else "播放",
                )
            }
            IconButton(onClick = { player.next() }) {
                Icon(Icons.Filled.SkipNext, contentDescription = "下一首")
            }
        }
    }
}

/** 空状态占位。 */
@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Filled.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(56.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(text, color = MaterialTheme.colorScheme.outline)
        }
    }
}

/**
 * 包裹需要「加入歌单」能力的界面。内部维护一个待选 Track，
 * 点击后弹出歌单选择 / 新建对话框，逻辑只写一次。
 */
@Composable
fun PlaylistPicker(
    store: PlaylistStore,
    content: @Composable (onAddToPlaylist: (Track) -> Unit) -> Unit,
) {
    var pending by remember { mutableStateOf<Track?>(null) }
    content { t -> pending = t }

    val playlists by store.playlists.collectAsStateWithLifecycle()
    if (pending != null) {
        PlaylistPickerDialog(
            track = pending!!,
            playlists = playlists,
            onDismiss = { pending = null },
            onPick = { id ->
                store.addTracks(id, listOf(pending!!.id))
                pending = null
            },
            onCreate = { name ->
                val id = store.create(name)
                store.addTracks(id, listOf(pending!!.id))
                pending = null
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaylistPickerDialog(
    track: Track,
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
    onCreate: (String) -> Unit,
) {
    var creating by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("加入歌单") },
        text = {
            Column {
                if (creating) {
                    androidx.compose.material3.OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("新歌单名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        TextButton(onClick = { creating = false; name = "" }) { Text("取消") }
                        TextButton(onClick = {
                            if (name.isNotBlank()) onCreate(name.trim())
                        }) { Text("创建并加入") }
                    }
                } else {
                    if (playlists.isEmpty()) {
                        Text("还没有歌单，点下面的「新建歌单」。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        LazyColumn(modifier = Modifier.height(240.dp)) {
                            items(playlists, key = { it.id }) { pl ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { onPick(pl.id) }
                                        .padding(vertical = 12.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.Filled.LibraryMusic, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(12.dp))
                                    Text("${pl.name}  ·  ${pl.trackIds.size} 首")
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { creating = true }) { Text("+ 新建歌单") }
                }
            }
        },
        confirmButton = { },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}
