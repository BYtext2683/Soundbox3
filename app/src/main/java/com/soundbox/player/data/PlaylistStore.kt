package com.soundbox.player.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class Playlist(
    val id: String,
    val name: String,
    val trackIds: List<String>,
    val createdAt: Long,
)

/**
 * 歌单持久化。用 JSON 文件存在 app 私有目录，
 * 不引入数据库依赖，云端编译零风险。
 */
class PlaylistStore(context: Context) {

    private val file = File(context.filesDir, "playlists.json")

    private val _playlists = MutableStateFlow(load())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    fun create(name: String): String {
        val id = UUID.randomUUID().toString()
        update { it + Playlist(id, name.trim().ifBlank { "新建歌单" }, emptyList(), System.currentTimeMillis()) }
        return id
    }

    fun rename(id: String, name: String) = update { list ->
        list.map { if (it.id == id) it.copy(name = name.trim().ifBlank { it.name }) else it }
    }

    fun delete(id: String) = update { list -> list.filterNot { it.id == id } }

    fun addTracks(id: String, trackIds: List<String>) = update { list ->
        list.map { pl ->
            if (pl.id != id) pl
            else pl.copy(trackIds = pl.trackIds + trackIds.filterNot { it in pl.trackIds })
        }
    }

    fun removeAt(id: String, index: Int) = update { list ->
        list.map { pl ->
            if (pl.id != id || index !in pl.trackIds.indices) pl
            else pl.copy(trackIds = pl.trackIds.toMutableList().also { it.removeAt(index) })
        }
    }

    /** 手动调整歌单内曲目顺序 */
    fun move(id: String, from: Int, to: Int) = update { list ->
        list.map { pl ->
            if (pl.id != id) return@map pl
            val items = pl.trackIds.toMutableList()
            if (from !in items.indices || to !in items.indices) return@map pl
            items.add(to, items.removeAt(from))
            pl.copy(trackIds = items)
        }
    }

    fun setOrder(id: String, trackIds: List<String>) = update { list ->
        list.map { if (it.id == id) it.copy(trackIds = trackIds) else it }
    }

    fun byId(id: String?): Playlist? = _playlists.value.firstOrNull { it.id == id }

    private fun update(transform: (List<Playlist>) -> List<Playlist>) {
        val next = transform(_playlists.value)
        _playlists.value = next
        save(next)
    }

    private fun load(): List<Playlist> = runCatching {
        if (!file.exists()) return@runCatching emptyList()
        val root = JSONArray(file.readText())
        buildList {
            for (i in 0 until root.length()) {
                val o = root.getJSONObject(i)
                val idsArray = o.optJSONArray("tracks") ?: JSONArray()
                val ids = buildList { for (j in 0 until idsArray.length()) add(idsArray.getString(j)) }
                add(
                    Playlist(
                        id = o.optString("id", UUID.randomUUID().toString()),
                        name = o.optString("name", "歌单"),
                        trackIds = ids,
                        createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                    )
                )
            }
        }
    }.getOrDefault(emptyList())

    private fun save(list: List<Playlist>) {
        runCatching {
            val root = JSONArray()
            list.forEach { pl ->
                root.put(
                    JSONObject().apply {
                        put("id", pl.id)
                        put("name", pl.name)
                        put("createdAt", pl.createdAt)
                        put("tracks", JSONArray().also { arr -> pl.trackIds.forEach(arr::put) })
                    }
                )
            }
            file.writeText(root.toString())
        }
    }
}
