package com.soundbox.player.data

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * 曲库仓库。仅收录用户手动导入的内容（不再自动扫描整台手机）：
 *  1. 用户导入的文件夹（SAF 目录树，递归扫描）
 *  2. 用户导入的单个文件
 *
 * 导入来源的元数据会缓存到 JSON，避免每次启动都重新解析标签。
 * 播放次数 / 累计时长由 StatsStore 维护，并通过 tracks 流合并到每首曲目上。
 */
class MusicRepository(
    private val context: Context,
    private val prefs: Prefs,
    private val statsStore: StatsStore,
) {

    /** 未附加统计的基础曲目列表（统计由 tracks 流合并后对外）。 */
    private val _base = MutableStateFlow<List<Track>>(emptyList())

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 对外曲库：基础列表 + 每首的播放次数 / 累计时长。 */
    val tracks: StateFlow<List<Track>> = combine(_base, statsStore.state) { base, stats ->
        base.map { t ->
            val s = stats[t.id]
            if (s != null) t.copy(playCount = s.count, playDurationMs = s.durationMs) else t
        }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status.asStateFlow()

    private val cacheFile = File(context.filesDir, "imported_cache.json")
    private val artDir = File(context.cacheDir, "artwork")

    @Volatile
    private var index: Map<String, Track> = emptyMap()

    fun trackById(id: String?): Track? = id?.let { index[it] }

    fun tracksByIds(ids: List<String>): List<Track> = ids.mapNotNull { index[it] }

    // ----------------------------------------------------- 隐藏与重命名（即时生效）

    /** 从曲库隐藏一首歌（不删除手机里的原始文件）。 */
    fun hideTrack(id: String) {
        prefs.hiddenTrackIds = prefs.hiddenTrackIds + id
        val cur = _base.value
        _base.value = cur.filter { it.id != id }
        index = index - id
    }

    /** 给歌曲改一个仅供显示的标题（不修改原文件）。 */
    fun renameTrack(id: String, newTitle: String) {
        val title = newTitle.trim()
        prefs.setTitleOverride(id, title)
        val cur = _base.value
        _base.value = cur.map { t -> if (t.id == id) t.copy(title = title.ifBlank { t.title }) else t }
    }

    // ------------------------------------------------------------------ 扫描

    fun refresh() {
        scope.launch { refreshInternal() }
    }

    private suspend fun refreshInternal() = withContext(Dispatchers.IO) {
        if (_scanning.value) return@withContext
        _scanning.value = true
        try {
            artDir.mkdirs()
            val cache = loadCache().toMutableMap()
            val merged = LinkedHashMap<String, Track>()

            val trees = prefs.importedTrees.toList()
            trees.forEachIndexed { i, tree ->
                _status.value = "正在扫描导入目录 ${i + 1}/${trees.size}…"
                runCatching { scanTree(Uri.parse(tree), cache, merged) }
            }

            val files = prefs.importedFiles.toList()
            if (files.isNotEmpty()) {
                _status.value = "正在读取导入文件…"
                files.forEach { raw ->
                    val cached = cache[raw]
                    val track = cached ?: runCatching { readSingleFile(Uri.parse(raw)) }.getOrNull()
                    if (track != null) {
                        cache[track.id] = track
                        merged[track.id] = track
                    }
                }
            }

            val minMs = prefs.minDurationSec * 1000L
            val hidden = prefs.hiddenTrackIds
            val overrides = prefs.titleOverrides()
            val list = merged.values.mapNotNull { t ->
                if (t.id in hidden) null
                else overrides[t.id]?.let { t.copy(title = it) } ?: t
            }.filter {
                minMs <= 0L || it.durationMs <= 0L || it.durationMs >= minMs
            }

            index = list.associateBy { it.id }
            _base.value = list.toList()
            saveCache(list.filter { it.source == TrackSource.IMPORTED })
            _status.value = null
        } finally {
            _scanning.value = false
        }
    }

    // ------------------------------------------------ 来源 2：SAF 目录树递归扫描

    private fun scanTree(
        treeUri: Uri,
        cache: MutableMap<String, Track>,
        out: MutableMap<String, Track>,
    ) {
        val cr = context.contentResolver
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )

        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        val stack = ArrayDeque<Pair<String, String>>()
        stack.addLast(rootId to treeDisplayName(treeUri, rootId))

        var visited = 0
        while (stack.isNotEmpty() && visited < MAX_DIRS) {
            visited++
            val (parentId, parentName) = stack.removeLast()
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)

            runCatching { cr.query(childrenUri, projection, null, null, null) }
                .getOrNull()?.use { c ->
                    while (c.moveToNext()) {
                        val docId = c.stringOr(0) ?: continue
                        val name = c.stringOr(1) ?: continue
                        val mime = c.stringOr(2).orEmpty()

                        if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                            stack.addLast(docId to name)
                            continue
                        }
                        if (!AudioFormats.isSupported(name)) continue

                        val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                        val key = docUri.toString()
                        val size = c.longOr(3, 0L)
                        val modified = c.longOr(4, 0L)

                        val cached = cache[key]
                        val track = if (cached != null && cached.sizeBytes == size) {
                            cached.copy(folderName = parentName.ifBlank { cached.folderName })
                        } else {
                            readMetadata(docUri, name, size, modified, parentName)
                        }
                        cache[key] = track
                        out[track.id] = track
                    }
                }
        }
    }

    private fun treeDisplayName(treeUri: Uri, rootId: String): String {
        val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootId)
        val name = runCatching {
            context.contentResolver.query(
                docUri,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null, null, null
            )?.use { if (it.moveToFirst()) it.stringOr(0) else null }
        }.getOrNull()
        return name?.takeIf { it.isNotBlank() }
            ?: rootId.substringAfterLast(':').substringAfterLast('/').ifBlank { "导入目录" }
    }

    // ------------------------------------------------------- 来源 3：单个文件

    private fun readSingleFile(uri: Uri): Track? {
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
        var name: String? = null
        var size = 0L
        var modified = 0L
        runCatching { context.contentResolver.query(uri, projection, null, null, null) }
            .getOrNull()?.use {
                if (it.moveToFirst()) {
                    name = it.stringOr(0)
                    size = it.longOr(1, 0L)
                    modified = it.longOr(2, 0L)
                }
            }
        val fileName = name ?: uri.lastPathSegment?.substringAfterLast('/') ?: return null
        if (!AudioFormats.isSupported(fileName)) return null
        return readMetadata(uri, fileName, size, modified, "单独导入")
    }

    // ------------------------------------------------------------ 标签解析

    private fun readMetadata(
        uri: Uri,
        displayName: String,
        size: Long,
        modified: Long,
        folder: String,
    ): Track {
        var title = displayName.substringBeforeLast('.').ifBlank { displayName }
        var artist = ""
        var album = ""
        var duration = 0L
        var artwork: Uri? = null

        val mmr = MediaMetadataRetriever()
        try {
            mmr.setDataSource(context, uri)
            mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?.trim()?.takeIf { it.isNotEmpty() }?.let { title = it }
            artist = (mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?: mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST))
                .orEmpty().trim()
            album = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM).orEmpty().trim()
            duration = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            artwork = cacheEmbeddedArtwork(mmr, artist, album, folder)
        } catch (_: Throwable) {
            // 解析失败就退回文件名，不影响播放
        } finally {
            runCatching { mmr.release() }
        }

        return Track(
            id = uri.toString(),
            uri = uri,
            title = title,
            artist = artist,
            album = album,
            durationMs = duration,
            sizeBytes = size,
            format = AudioFormats.label(AudioFormats.extensionOf(displayName)),
            folderName = folder.ifBlank { "导入" },
            artworkUri = artwork,
            addedAt = if (modified > 0) modified else System.currentTimeMillis(),
            source = TrackSource.IMPORTED,
        )
    }

    /** 把内嵌封面按「歌手+专辑」去重后落盘，供列表与通知栏复用。 */
    private fun cacheEmbeddedArtwork(
        mmr: MediaMetadataRetriever,
        artist: String,
        album: String,
        folder: String,
    ): Uri? {
        val file = File(artDir, sha1("$artist|$album|$folder") + ".img")
        if (file.exists() && file.length() > 0L) return Uri.fromFile(file)
        val bytes = runCatching { mmr.embeddedPicture }.getOrNull() ?: return null
        if (bytes.isEmpty() || bytes.size > MAX_ART_BYTES) return null
        return runCatching {
            artDir.mkdirs()
            file.writeBytes(bytes)
            Uri.fromFile(file)
        }.getOrNull()
    }

    // ------------------------------------------------------------ 导入管理

    fun addTree(uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        prefs.importedTrees = prefs.importedTrees + uri.toString()
    }

    fun removeTree(uriString: String) {
        prefs.importedTrees = prefs.importedTrees - uriString
        runCatching {
            context.contentResolver.releasePersistableUriPermission(
                Uri.parse(uriString), Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
    }

    fun addFiles(uris: List<Uri>) {
        uris.forEach {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    it, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
        }
        prefs.importedFiles = prefs.importedFiles + uris.map(Uri::toString)
    }

    fun clearImports() {
        prefs.importedTrees.forEach { removeTree(it) }
        prefs.importedFiles = emptySet()
        runCatching { cacheFile.delete() }
    }

    fun importedTreeNames(): List<Pair<String, String>> = prefs.importedTrees.map { raw ->
        val uri = Uri.parse(raw)
        val label = runCatching {
            DocumentsContract.getTreeDocumentId(uri).substringAfter(':').ifBlank { raw }
        }.getOrDefault(raw)
        raw to label
    }

    // ------------------------------------------------------------ 元数据缓存

    private fun loadCache(): Map<String, Track> = runCatching {
        if (!cacheFile.exists()) return@runCatching emptyMap<String, Track>()
        val arr = JSONArray(cacheFile.readText())
        buildMap {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val raw = o.optString("uri")
                if (raw.isBlank()) continue
                put(
                    raw,
                    Track(
                        id = raw,
                        uri = Uri.parse(raw),
                        title = o.optString("title"),
                        artist = o.optString("artist"),
                        album = o.optString("album"),
                        durationMs = o.optLong("duration"),
                        sizeBytes = o.optLong("size"),
                        format = o.optString("format"),
                        folderName = o.optString("folder"),
                        artworkUri = o.optString("art").takeIf { it.isNotBlank() }?.let(Uri::parse),
                        addedAt = o.optLong("added"),
                        source = TrackSource.IMPORTED,
                    )
                )
            }
        }
    }.getOrDefault(emptyMap())

    private fun saveCache(list: List<Track>) {
        runCatching {
            val arr = JSONArray()
            list.forEach { t ->
                arr.put(
                    JSONObject().apply {
                        put("uri", t.id)
                        put("title", t.title)
                        put("artist", t.artist)
                        put("album", t.album)
                        put("duration", t.durationMs)
                        put("size", t.sizeBytes)
                        put("format", t.format)
                        put("folder", t.folderName)
                        put("art", t.artworkUri?.toString().orEmpty())
                        put("added", t.addedAt)
                    }
                )
            }
            cacheFile.writeText(arr.toString())
        }
    }

    private companion object {
        const val MAX_DIRS = 20_000
        const val MAX_ART_BYTES = 4 * 1024 * 1024
    }
}

private fun Cursor.stringOr(index: Int): String? =
    if (index < 0 || isNull(index)) null else runCatching { getString(index) }.getOrNull()

private fun Cursor.longOr(index: Int, fallback: Long): Long =
    if (index < 0 || isNull(index)) fallback else runCatching { getLong(index) }.getOrDefault(fallback)

private fun sha1(input: String): String = runCatching {
    MessageDigest.getInstance("SHA-1").digest(input.toByteArray())
        .joinToString("") { "%02x".format(it) }
}.getOrDefault(input.hashCode().toString())
