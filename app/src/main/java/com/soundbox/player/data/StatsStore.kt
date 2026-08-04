package com.soundbox.player.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File

/** 单曲播放统计：累计播放次数与累计播放时长。 */
data class TrackStat(val count: Int = 0, val durationMs: Long = 0L)

/**
 * 播放统计持久层。以曲目 id（uri 字符串）为键，落盘到 stats.json。
 * 通过 StateFlow 对外暴露，曲库列表可据此实时合并「播放次数 / 累计时长」。
 */
class StatsStore(context: Context) {

    private val file = File(context.filesDir, "stats.json")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()

    private val _state = MutableStateFlow(load())
    val state: StateFlow<Map<String, TrackStat>> = _state.asStateFlow()

    private var dirty = false

    fun count(id: String): Int = _state.value[id]?.count ?: 0
    fun duration(id: String): Long = _state.value[id]?.durationMs ?: 0L

    /** 一首歌开始播放时调用，播放次数 +1。 */
    fun recordPlay(id: String) {
        update(id) { it.copy(count = it.count + 1) }
    }

    /** 播放过程中累计正在收听的时长（毫秒）。 */
    fun addDuration(id: String, ms: Long) {
        if (ms <= 0) return
        update(id) { it.copy(durationMs = it.durationMs + ms) }
    }

    private fun update(id: String, transform: (TrackStat) -> TrackStat) {
        synchronized(lock) {
            val cur = _state.value
            val next = cur[id] ?: TrackStat()
            _state.value = cur + (id to transform(next))
        }
        scheduleSave()
    }

    private fun scheduleSave() {
        dirty = true
        scope.launch {
            delay(1500L)
            synchronized(lock) {
                if (dirty) {
                    save()
                    dirty = false
                }
            }
        }
    }

    @Synchronized
    private fun save() {
        runCatching {
            val o = JSONObject()
            _state.value.forEach { (id, s) ->
                o.put(id, JSONObject().apply {
                    put("c", s.count)
                    put("d", s.durationMs)
                })
            }
            file.writeText(o.toString())
        }
    }

    private fun load(): Map<String, TrackStat> = runCatching {
        if (!file.exists()) return@runCatching emptyMap()
        val o = JSONObject(file.readText())
        val map = mutableMapOf<String, TrackStat>()
        o.keys().forEach { id ->
            val j = o.getJSONObject(id)
            map[id] = TrackStat(j.optInt("c", 0), j.optLong("d", 0L))
        }
        map
    }.getOrDefault(emptyMap())
}
