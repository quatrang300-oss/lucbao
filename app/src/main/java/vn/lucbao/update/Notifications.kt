package vn.lucbao.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.ForegroundInfo
import vn.lucbao.R

object Notifications {
    private const val CHANNEL = "updates"
    private const val ID_UPDATE = 4201
    private const val ID_WORK = 4202

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Cập nhật", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Thông báo khi Lục Bảo có bản mới"
            }
        )
    }

    fun showUpdateReady(context: Context, confirm: Intent) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        val pi = PendingIntent.getActivity(
            context, 0, confirm,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Lục Bảo có bản mới")
            .setContentText("Chạm để cập nhật – chỉ mất vài giây.")
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(ID_UPDATE, n)
    }

    fun clearUpdate(context: Context) {
        NotificationManagerCompat.from(context).cancel(ID_UPDATE)
    }

    fun updateForegroundInfo(context: Context): ForegroundInfo {
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Lục Bảo đang kiểm tra bản cập nhật")
            .setSilent(true)
            .build()
        return ForegroundInfo(ID_WORK, n)
    }
}
