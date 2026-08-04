package com.soundbox.player.data

import android.content.Context
import androidx.core.content.edit
import org.json.JSONObject

class Prefs(context: Context) {

    private val sp = context.getSharedPreferences("soundbox_prefs", Context.MODE_PRIVATE)

    var playOrderOrdinal: Int
        get() = sp.getInt(KEY_ORDER, PlayOrder.REPEAT_ALL.ordinal)
        set(value) = sp.edit { putInt(KEY_ORDER, value) }

    var sortModeOrdinal: Int
        get() = sp.getInt(KEY_SORT, SortMode.TITLE.ordinal)
        set(value) = sp.edit { putInt(KEY_SORT, value) }

    /** 过滤掉时长过短的片段（秒），0 表示不过滤 */
    var minDurationSec: Int
        get() = sp.getInt(KEY_MIN_DURATION, 0)
        set(value) = sp.edit { putInt(KEY_MIN_DURATION, value) }

    /** 用户通过「导入文件夹」添加的 SAF 目录树 */
    var importedTrees: Set<String>
        get() = sp.getStringSet(KEY_TREES, emptySet()).orEmpty()
        set(value) = sp.edit { putStringSet(KEY_TREES, value) }

    /** 用户通过「导入文件」逐个添加的音频 */
    var importedFiles: Set<String>
        get() = sp.getStringSet(KEY_FILES, emptySet()).orEmpty()
        set(value) = sp.edit { putStringSet(KEY_FILES, value) }

    // ----- 曲库整理：隐藏与重命名 -----

    /** 被用户从曲库隐藏（移除）的曲目 id（URI 字符串）。不删除原始文件。 */
    var hiddenTrackIds: Set<String>
        get() = sp.getStringSet(KEY_HIDDEN, emptySet()).orEmpty()
        set(value) = sp.edit { putStringSet(KEY_HIDDEN, value) }

    /** 用户手动设置的曲目标题覆盖，JSON 对象：id -> 标题。 */
    var titleOverridesJson: String
        get() = sp.getString(KEY_TITLE_OVERRIDES, "") ?: ""
        set(value) = sp.edit { putString(KEY_TITLE_OVERRIDES, value) }

    fun titleOverrides(): Map<String, String> {
        val raw = titleOverridesJson
        if (raw.isBlank()) return emptyMap()
        return runCatching {
            val o = JSONObject(raw)
            buildMap { o.keys().asSequence().forEach { put(it, o.optString(it)) } }
        }.getOrDefault(emptyMap())
    }

    fun setTitleOverride(id: String, title: String) {
        val map = titleOverrides().toMutableMap()
        if (title.isBlank()) map.remove(id) else map[id] = title
        val o = JSONObject()
        map.forEach { (k, v) -> o.put(k, v) }
        titleOverridesJson = o.toString()
    }

    // ----- 背景壁纸 -----

    var wallpaperUri: String
        get() = sp.getString(KEY_WALLPAPER_URI, "") ?: ""
        set(value) = sp.edit { putString(KEY_WALLPAPER_URI, value) }

    var wallpaperScale: Float
        get() = sp.getFloat(KEY_WALLPAPER_SCALE, 1f)
        set(value) = sp.edit { putFloat(KEY_WALLPAPER_SCALE, value) }

    var wallpaperOffsetX: Float
        get() = sp.getFloat(KEY_WALLPAPER_OFFSET_X, 0f)
        set(value) = sp.edit { putFloat(KEY_WALLPAPER_OFFSET_X, value) }

    var wallpaperOffsetY: Float
        get() = sp.getFloat(KEY_WALLPAPER_OFFSET_Y, 0f)
        set(value) = sp.edit { putFloat(KEY_WALLPAPER_OFFSET_Y, value) }

    /** 背景不透明度（0 = 完全被蒙版遮住，1 = 壁纸完全清晰）。 */
    var wallpaperOpacity: Float
        get() = sp.getFloat(KEY_WALLPAPER_OPACITY, DEFAULT_WALLPAPER_OPACITY)
        set(value) = sp.edit { putFloat(KEY_WALLPAPER_OPACITY, value) }

    private companion object {
        const val KEY_ORDER = "play_order"
        const val KEY_SORT = "sort_mode"
        const val KEY_MIN_DURATION = "min_duration_sec"
        const val KEY_TREES = "imported_trees"
        const val KEY_FILES = "imported_files"
        const val KEY_HIDDEN = "hidden_track_ids"
        const val KEY_TITLE_OVERRIDES = "title_overrides"
        const val KEY_WALLPAPER_URI = "wallpaper_uri"
        const val KEY_WALLPAPER_SCALE = "wallpaper_scale"
        const val KEY_WALLPAPER_OFFSET_X = "wallpaper_offset_x"
        const val KEY_WALLPAPER_OFFSET_Y = "wallpaper_offset_y"
        const val KEY_WALLPAPER_OPACITY = "wallpaper_opacity"
        const val DEFAULT_WALLPAPER_OPACITY = 0.28f
    }
}
