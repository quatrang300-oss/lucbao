@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package vn.lucbao.player

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import vn.lucbao.R
import vn.lucbao.ui.MainActivity

/** Keeps playback alive in the background with lock-screen / notification controls. */
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        PlayerController.init(this)
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java)
                .setAction(MainActivity.ACTION_OPEN_PLAYER)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        session = MediaSession.Builder(this, PlayerController.sessionPlayer)
            .setSessionActivity(open)
            .build()
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this).build().apply {
                setSmallIcon(R.drawable.ic_notification)
            }
        )
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        session?.release()
        session = null
        super.onDestroy()
    }
}
