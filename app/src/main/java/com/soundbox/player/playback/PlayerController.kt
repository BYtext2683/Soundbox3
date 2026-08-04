package com.soundbox.player.playback

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.soundbox.player.data.PlayOrder
import com.soundbox.player.data.Prefs
import com.soundbox.player.data.StatsStore
import com.soundbox.player.data.Track
import com.soundbox.player.data.toMediaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PlayerUiState(
    val ready: Boolean = false,
    val currentId: String? = null,
    val title: String = "",
    val artist: String = "",
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val order: PlayOrder = PlayOrder.REPEAT_ALL,
    val queueIds: List<String> = emptyList(),
    val queueIndex: Int = 0,
) {
    val progress: Float
        get() = if (durationMs > 0L) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

/**
 * UI 与后台 MediaSession 之间的桥。把 MediaController 的回调折叠成一个 StateFlow，
 * Compose 侧只需要 collect 一次。
 */
class PlayerController(
    private val context: Context,
    private val prefs: Prefs,
    private val stats: StatsStore,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var controller: MediaController? = null
    private var ticker: Job? = null

    /** 当前正在统计时长的曲目 id，用于切歌时结算上一首。 */
    private var lastId: String? = null
    /** 已播放但未落盘的累计毫秒数，每满 5 秒结算一次。 */
    private var tickAccumMs = 0L

    private val _state = MutableStateFlow(PlayerUiState(order = PlayOrder.of(prefs.playOrderOrdinal)))
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = syncFrom(player)
    }

    fun connect() {
        if (controller != null) return
        val token = SessionToken(context, ComponentName(context, MusicService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            val c = runCatching { future.get() }.getOrNull() ?: return@addListener
            controller = c
            c.addListener(listener)
            applyOrder(PlayOrder.of(prefs.playOrderOrdinal))
            syncFrom(c)
            startTicker()
        }, ContextCompat.getMainExecutor(context))
    }

    fun release() {
        if (lastId != null && tickAccumMs > 0) {
            stats.addDuration(lastId!!, tickAccumMs)
            tickAccumMs = 0
        }
        ticker?.cancel()
        ticker = null
        controller?.removeListener(listener)
        controller?.release()
        controller = null
    }

    // ------------------------------------------------------------ 播放控制

    /** 用一批曲目替换播放队列并从 startIndex 开始播放。 */
    fun playAll(tracks: List<Track>, startIndex: Int = 0) {
        val c = controller ?: return
        if (tracks.isEmpty()) return
        val items: List<MediaItem> = tracks.map { it.toMediaItem() }
        c.setMediaItems(items, startIndex.coerceIn(0, items.lastIndex), 0L)
        c.prepare()
        c.play()
    }

    fun playTrack(track: Track, queue: List<Track>) {
        val index = queue.indexOfFirst { it.id == track.id }
        if (index >= 0) playAll(queue, index) else playAll(listOf(track), 0)
    }

    fun addToQueue(tracks: List<Track>) {
        val c = controller ?: return
        if (tracks.isEmpty()) return
        if (c.mediaItemCount == 0) {
            playAll(tracks, 0)
        } else {
            c.addMediaItems(tracks.map { it.toMediaItem() })
        }
    }

    fun playNext(tracks: List<Track>) {
        val c = controller ?: return
        if (tracks.isEmpty()) return
        if (c.mediaItemCount == 0) {
            playAll(tracks, 0)
        } else {
            c.addMediaItems(c.currentMediaItemIndex + 1, tracks.map { it.toMediaItem() })
        }
    }

    fun togglePlayPause() {
        val c = controller ?: return
        if (c.isPlaying) {
            c.pause()
        } else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            c.play()
        }
    }

    fun next() = controller?.seekToNextMediaItem() ?: Unit

    fun previous() {
        val c = controller ?: return
        // 播放超过 3 秒时，「上一首」先回到本曲开头，符合常见播放器习惯
        if (c.currentPosition > 3_000L) c.seekTo(0L) else c.seekToPreviousMediaItem()
    }

    fun seekTo(ms: Long) {
        controller?.seekTo(ms.coerceAtLeast(0L))
        _state.update { it.copy(positionMs = ms) }
    }

    fun seekToQueueIndex(index: Int) {
        val c = controller ?: return
        if (index in 0 until c.mediaItemCount) {
            c.seekTo(index, 0L)
            c.play()
        }
    }

    fun removeFromQueue(index: Int) {
        val c = controller ?: return
        if (index in 0 until c.mediaItemCount) c.removeMediaItem(index)
    }

    fun clearQueue() {
        controller?.clearMediaItems()
    }

    // ------------------------------------------------------------ 播放顺序

    fun cycleOrder(): PlayOrder {
        val next = _state.value.order.next()
        applyOrder(next)
        return next
    }

    fun applyOrder(order: PlayOrder) {
        prefs.playOrderOrdinal = order.ordinal
        controller?.let {
            it.repeatMode = order.repeatMode
            it.shuffleModeEnabled = order.shuffle
        }
        _state.update { it.copy(order = order) }
    }

    // ------------------------------------------------------------ 内部同步

    private fun syncFrom(player: Player) {
        val meta = player.mediaMetadata
        val queue = buildList {
            for (i in 0 until player.mediaItemCount) {
                add(player.getMediaItemAt(i).mediaId)
            }
        }

        // 切歌时：先结算上一首的收听时长，再为当前曲记录一次播放次数
        val curId = player.currentMediaItem?.mediaId
        if (curId != null && curId != lastId) {
            if (lastId != null && tickAccumMs > 0) {
                stats.addDuration(lastId!!, tickAccumMs)
                tickAccumMs = 0
            }
            stats.recordPlay(curId)
            lastId = curId
        }
        _state.update {
            it.copy(
                ready = true,
                currentId = player.currentMediaItem?.mediaId,
                title = meta.title?.toString().orEmpty(),
                artist = meta.artist?.toString().orEmpty(),
                isPlaying = player.isPlaying,
                positionMs = player.currentPosition.coerceAtLeast(0L),
                durationMs = player.duration.takeIf { d -> d > 0L } ?: 0L,
                order = orderOf(player),
                queueIds = queue,
                queueIndex = player.currentMediaItemIndex.coerceAtLeast(0),
            )
        }
    }

    private fun orderOf(player: Player): PlayOrder = when {
        player.shuffleModeEnabled -> PlayOrder.SHUFFLE
        player.repeatMode == Player.REPEAT_MODE_ONE -> PlayOrder.REPEAT_ONE
        player.repeatMode == Player.REPEAT_MODE_OFF -> PlayOrder.SEQUENTIAL
        else -> PlayOrder.REPEAT_ALL
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                val c = controller
                if (c != null && c.isPlaying) {
                    tickAccumMs += 500
                    if (tickAccumMs >= 5000) {
                        val id = lastId
                        if (id != null) stats.addDuration(id, tickAccumMs)
                        tickAccumMs = 0
                    }
                    _state.update {
                        it.copy(
                            positionMs = c.currentPosition.coerceAtLeast(0L),
                            durationMs = c.duration.takeIf { d -> d > 0L } ?: it.durationMs,
                        )
                    }
                }
                delay(500L)
            }
        }
    }
}
