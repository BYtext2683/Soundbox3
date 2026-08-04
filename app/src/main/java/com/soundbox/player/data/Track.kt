package com.soundbox.player.data

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player

const val UNKNOWN_ARTIST = "未知歌手"
const val UNKNOWN_ALBUM = "未知专辑"

enum class TrackSource { DEVICE, IMPORTED }

/** 一首歌曲。id 直接使用 uri 字符串，保证歌单引用长期稳定。 */
data class Track(
    val id: String,
    val uri: Uri,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val format: String,
    val folderName: String,
    val artworkUri: Uri?,
    val addedAt: Long,
    val source: TrackSource,
    /** 累计播放次数（由 StatsStore 合并，默认值 0）。 */
    val playCount: Int = 0,
    /** 累计播放时长（毫秒，由 StatsStore 合并，默认值 0）。 */
    val playDurationMs: Long = 0L,
) {
    val displayArtist: String get() = artist.ifBlank { UNKNOWN_ARTIST }
    val displayAlbum: String get() = album.ifBlank { UNKNOWN_ALBUM }
    val subtitle: String get() = "$displayArtist · $displayAlbum"
}

fun Track.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(id)
    .setUri(uri)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(displayArtist)
            .setAlbumTitle(displayAlbum)
            .setArtworkUri(artworkUri)
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .build()
    )
    .build()

/** 播放顺序：一个按钮循环切换四种模式。 */
enum class PlayOrder(val label: String) {
    SEQUENTIAL("顺序播放"),
    REPEAT_ALL("列表循环"),
    REPEAT_ONE("单曲循环"),
    SHUFFLE("随机播放");

    val repeatMode: Int
        get() = when (this) {
            SEQUENTIAL -> Player.REPEAT_MODE_OFF
            REPEAT_ONE -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_ALL
        }

    val shuffle: Boolean get() = this == SHUFFLE

    fun next(): PlayOrder = entries[(ordinal + 1) % entries.size]

    companion object {
        fun of(ordinal: Int): PlayOrder = entries.getOrElse(ordinal) { REPEAT_ALL }
    }
}

/** 曲库排序方式。 */
enum class SortMode(val label: String) {
    TITLE("按标题"),
    ARTIST("按歌手"),
    ALBUM("按专辑"),
    ADDED_DESC("最近添加"),
    DURATION("按时长"),
    SIZE("按文件大小"),
    PLAY_COUNT("按播放次数"),
    PLAY_TIME("按收听时长");

    companion object {
        fun of(ordinal: Int): SortMode = entries.getOrElse(ordinal) { TITLE }
    }
}

fun List<Track>.sortedBy(mode: SortMode): List<Track> = when (mode) {
    SortMode.TITLE -> sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
    SortMode.ARTIST -> sortedWith(
        compareBy(String.CASE_INSENSITIVE_ORDER, Track::displayArtist)
            .thenBy(String.CASE_INSENSITIVE_ORDER, Track::title)
    )
    SortMode.ALBUM -> sortedWith(
        compareBy(String.CASE_INSENSITIVE_ORDER, Track::displayAlbum)
            .thenBy(String.CASE_INSENSITIVE_ORDER, Track::title)
    )
    SortMode.ADDED_DESC -> sortedByDescending { it.addedAt }
    SortMode.DURATION -> sortedByDescending { it.durationMs }
    SortMode.SIZE -> sortedByDescending { it.sizeBytes }
    SortMode.PLAY_COUNT -> sortedByDescending { it.playCount }
    SortMode.PLAY_TIME -> sortedByDescending { it.playDurationMs }
}

/** 本机原生解码器支持的音频格式。凡是列在这里的才会被扫描进曲库。 */
object AudioFormats {

    val SUPPORTED: Set<String> = setOf(
        // MPEG / AAC 系
        "mp3", "m4a", "m4b", "m4r", "mp4", "aac", "3gp", "3gpp", "3ga",
        // 无损
        "flac", "fla", "wav", "wave", "rf64",
        // Xiph
        "ogg", "oga", "opus",
        // Matroska
        "mka", "webm",
        // 语音
        "amr", "awb",
    )

    fun extensionOf(name: String): String {
        val dot = name.lastIndexOf('.')
        if (dot <= 0 || dot == name.length - 1) return ""
        return name.substring(dot + 1).lowercase()
    }

    fun isSupported(name: String): Boolean = extensionOf(name) in SUPPORTED

    fun label(ext: String): String = if (ext.isBlank()) "其他" else ext.uppercase()
}

fun formatDuration(ms: Long): String {
    if (ms <= 0) return "--:--"
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun formatSize(bytes: Long): String = when {
    bytes <= 0 -> "-"
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.0f KB".format(bytes / 1024.0)
    bytes < 1024L * 1024 * 1024 -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
    else -> "%.2f GB".format(bytes / 1024.0 / 1024.0 / 1024.0)
}
