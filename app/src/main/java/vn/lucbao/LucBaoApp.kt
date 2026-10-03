package vn.lucbao

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.crossfade
import vn.lucbao.data.Library
import vn.lucbao.data.Prefs
import vn.lucbao.engine.EngineManager
import vn.lucbao.player.PlayerController
import vn.lucbao.update.Notifications
import vn.lucbao.update.Updater

class LucBaoApp : Application(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        Library.init(this)
        EngineManager.init(this)
        PlayerController.init(this)
        Notifications.createChannels(this)
        Updater.init(this)
        Updater.schedule(this)
        // Quick check at every launch (rate limited inside).
        Updater.requestEngineCheck(this, minIntervalMs = 60 * 60_000L)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context).crossfade(true).build()
}
