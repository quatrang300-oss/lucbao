package vn.lucbao.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner

/** Receives the installer's answer; asks the user only when Android insists. */
class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm: Intent = (if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                }) ?: return
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                val foreground = ProcessLifecycleOwner.get().lifecycle.currentState
                    .isAtLeast(Lifecycle.State.STARTED)
                if (foreground) {
                    runCatching { context.startActivity(confirm) }
                        .onFailure { Notifications.showUpdateReady(context, confirm) }
                } else {
                    Notifications.showUpdateReady(context, confirm)
                }
            }
            PackageInstaller.STATUS_SUCCESS -> Notifications.clearUpdate(context)
            else -> Log.w(
                "InstallReceiver",
                "Install status $status: ${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)}"
            )
        }
    }
}
