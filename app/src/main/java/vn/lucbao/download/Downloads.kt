package vn.lucbao.download

import android.content.Context
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import vn.lucbao.api.VideoDetails

object Downloads {
    fun enqueue(context: Context, details: VideoDetails, choice: DownloadChoice) {
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(
                workDataOf(
                    DownloadWorker.KEY_URL to details.url,
                    DownloadWorker.KEY_TITLE to (details.title ?: "Video"),
                    DownloadWorker.KEY_THUMB to details.thumbnail,
                    DownloadWorker.KEY_CHOICE to choice.key,
                )
            )
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag(DownloadWorker.TAG)
            .addTag("t:" + (details.title ?: "Video"))
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }
}
