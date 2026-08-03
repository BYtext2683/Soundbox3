package com.soundbox.player

import android.app.Application
import com.soundbox.player.data.MusicRepository
import com.soundbox.player.data.PlaylistStore
import com.soundbox.player.data.Prefs
import com.soundbox.player.playback.PlayerController

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

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        repository = MusicRepository(this, prefs)
        playlists = PlaylistStore(this)
        player = PlayerController(this, prefs)
    }
}
