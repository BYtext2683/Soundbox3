package com.soundbox.player.data

import android.content.Context
import androidx.core.content.edit

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

    private companion object {
        const val KEY_ORDER = "play_order"
        const val KEY_SORT = "sort_mode"
        const val KEY_MIN_DURATION = "min_duration_sec"
        const val KEY_TREES = "imported_trees"
        const val KEY_FILES = "imported_files"
    }
}
