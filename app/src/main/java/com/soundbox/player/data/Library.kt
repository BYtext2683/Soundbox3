package com.soundbox.player.data

/** 曲库的自动分类维度。 */
enum class CategoryType(val route: String, val label: String) {
    ARTIST("artist", "歌手"),
    ALBUM("album", "专辑"),
    FOLDER("folder", "文件夹"),
    FORMAT("format", "格式");

    companion object {
        fun of(route: String?): CategoryType =
            entries.firstOrNull { it.route == route } ?: ARTIST
    }
}

data class CategoryItem(
    val key: String,
    val title: String,
    val subtitle: String,
    val count: Int,
    val cover: Track?,
)

private fun Track.keyFor(type: CategoryType): String = when (type) {
    CategoryType.ARTIST -> displayArtist
    CategoryType.ALBUM -> displayAlbum
    CategoryType.FOLDER -> folderName
    CategoryType.FORMAT -> format
}

fun List<Track>.groupInto(type: CategoryType): List<CategoryItem> {
    val grouped = groupBy { it.keyFor(type) }
    val items = grouped.map { (key, tracks) ->
        CategoryItem(
            key = key,
            title = key,
            subtitle = when (type) {
                CategoryType.ALBUM -> "${tracks.first().displayArtist} · ${tracks.size} 首"
                CategoryType.FORMAT -> "${tracks.size} 首 · ${formatSize(tracks.sumOf { it.sizeBytes })}"
                else -> "${tracks.size} 首"
            },
            count = tracks.size,
            cover = tracks.firstOrNull { it.artworkUri != null } ?: tracks.firstOrNull(),
        )
    }
    return if (type == CategoryType.FORMAT) {
        items.sortedByDescending { it.count }
    } else {
        items.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
    }
}

fun List<Track>.inCategory(type: CategoryType, key: String): List<Track> =
    filter { it.keyFor(type) == key }

fun List<Track>.search(query: String): List<Track> {
    val q = query.trim()
    if (q.isEmpty()) return this
    return filter {
        it.title.contains(q, true) ||
            it.artist.contains(q, true) ||
            it.album.contains(q, true) ||
            it.folderName.contains(q, true)
    }
}
