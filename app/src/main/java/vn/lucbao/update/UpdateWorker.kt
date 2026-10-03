package vn.lucbao.update

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Updater.runChecks(applicationContext)
        Result.success()
    }

    /** Needed for expedited work on Android 11 and older. */
    override suspend fun getForegroundInfo(): ForegroundInfo =
        Notifications.updateForegroundInfo(applicationContext)
}
