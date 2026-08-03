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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.soundbox.player.App
import com.soundbox.player.data.CategoryItem
import com.soundbox.player.data.CategoryType
import com.soundbox.player.data.Track
import com.soundbox.player.data.groupInto
import com.soundbox.player.data.inCategory
import com.soundbox.player.ui.components.ArtworkImage
import com.soundbox.player.ui.components.EmptyState
import com.soundbox.player.ui.components.PlaylistPicker
import com.soundbox.player.ui.components.TrackRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(app: App) {
    val tracks by app.repository.tracks.collectAsStateWithLifecycle()
    val playerState by app.player.state.collectAsStateWithLifecycle()

    var tabIndex by remember { mutableStateOf(0) }
    var drillKey by remember { mutableStateOf<String?>(null) }
    val currentType = CategoryType.entries[tabIndex]

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(if (drillKey == null) "曲库" else "返回曲库") },
            navigationIcon = if (drillKey != null) {
                { IconButton(onClick = { drillKey = null }) { Icon(Icons.Filled.ArrowBack, null) } }
            } else {
                {}
            },
        )

        if (drillKey == null) {
            ScrollableTabRow(
                selectedTabIndex = tabIndex,
                edgePadding = 16.dp,
            ) {
                CategoryType.entries.forEachIndexed { i, type ->
                    Tab(
                        selected = i == tabIndex,
                        onClick = { tabIndex = i },
                        text = { Text(type.label) },
                    )
                }
            }
        }

        if (drillKey == null) {
            val groups = remember(tracks, currentType) { tracks.groupInto(currentType) }
            if (groups.isEmpty()) {
                EmptyState("还没有可分类的歌曲", Modifier.weight(1f))
            } else {
                LazyColumn(Modifier.weight(1f)) {
                    items(groups, key = { it.key }) { item ->
                        CategoryCard(item) { drillKey = item.key }
                    }
                }
            }
        } else {
            val list = remember(tracks, currentType, drillKey) {
                tracks.inCategory(currentType, drillKey!!)
            }
            if (list.isEmpty()) {
                EmptyState("这个分类下没有歌曲", Modifier.weight(1f))
            } else {
                PlaylistPicker(app.playlists) { onAdd ->
                    LazyColumn(Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 8.dp)) {
                        items(list, key = { it.id }) { track: Track ->
                            TrackRow(
                                track = track,
                                highlight = track.id == playerState.currentId,
                                onPlay = { app.player.playTrack(track, list) },
                                onPlayNext = { app.player.playNext(listOf(track)) },
                                onAddToQueue = { app.player.addToQueue(listOf(track)) },
                                onAddToPlaylist = { onAdd(track) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryCard(item: CategoryItem, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtworkImage(
            track = item.cover,
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp)),
        )
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        ) {
            Text(
                item.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                item.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
