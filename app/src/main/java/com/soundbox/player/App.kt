package com.soundbox.player

import android.app.Application
import com.soundbox.player.data.MusicRepository
import com.soundbox.player.data.PlaylistStore
import com.soundbox.player.data.Prefs
import com.soundbox.player.data.StatsStore
import com.soundbox.player.playback.PlayerController
import kotlinx.coroutines.flow.MutableStateFlow

/** 背景壁纸配置。uri 为空字符串表示未设置壁纸。 */
data class WallpaperConfig(
    val uri: String = "",
    val scale: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val opacity: Float = Prefs.DEFAULT_WALLPAPER_OPACITY,
)

/** 手写的极简依赖容器，不引入 DI 框架，减少云端编译的不确定性。 */
class App : Application() {

    lateinit var prefs: Prefs
        private set
    lateinit var repository: MusicRepository
        private set
    lateinit var playlists: PlaylistStore
        private set
    lateinit var player: PlayerController
        private set

    /** 当前背景壁纸配置（uri/缩放/偏移），变更时驱动壁纸重绘。 */
    lateinit var wallpaperConfig: MutableStateFlow<WallpaperConfig>
        private set

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        val stats = StatsStore(this)
        repository = MusicRepository(this, prefs, stats)
        playlists = PlaylistStore(this)
        player = PlayerController(this, prefs, stats)
        wallpaperConfig = MutableStateFlow(
            WallpaperConfig(
                uri = prefs.wallpaperUri,
                scale = prefs.wallpaperScale,
                offsetX = prefs.wallpaperOffsetX,
                offsetY = prefs.wallpaperOffsetY,
                opacity = prefs.wallpaperOpacity,
            )
        )
    }

    fun applyWallpaper(
        uri: String,
        scale: Float,
        offsetX: Float,
        offsetY: Float,
        opacity: Float = Prefs.DEFAULT_WALLPAPER_OPACITY,
    ) {
        prefs.wallpaperUri = uri
        prefs.wallpaperScale = scale
        prefs.wallpaperOffsetX = offsetX
        prefs.wallpaperOffsetY = offsetY
        prefs.wallpaperOpacity = opacity
        wallpaperConfig.value = WallpaperConfig(uri, scale, offsetX, offsetY, opacity)
    }

    /** 仅更新背景不透明度，实时生效并持久化。 */
    fun setWallpaperOpacity(opacity: Float) {
        val c = wallpaperConfig.value
        prefs.wallpaperOpacity = opacity
        wallpaperConfig.value = c.copy(opacity = opacity)
    }

    fun resetWallpaper() {
        prefs.wallpaperUri = ""
        prefs.wallpaperOpacity = Prefs.DEFAULT_WALLPAPER_OPACITY
        wallpaperConfig.value = WallpaperConfig()
    }
}
