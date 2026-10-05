package vn.lucbao.download

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import vn.lucbao.R
import vn.lucbao.data.DownloadEntry
import vn.lucbao.data.Library
import vn.lucbao.engine.EngineManager
import java.io.File

/** Downloads one video (or its audio) in the background, with a progress notification. */
class DownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private val nm = context.getSystemService(NotificationManager::class.java)
    private val notifId = id.hashCode()
    private val title = inputData.getString(KEY_TITLE) ?: "Video"
    private var lastUpdate = 0L

    override suspend fun getForegroundInfo(): ForegroundInfo = foreground(0f, "Đang chuẩn bị…")

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val url = inputData.getString(KEY_URL) ?: return@withContext Result.failure()
        val key = inputData.getString(KEY_CHOICE) ?: return@withContext Result.failure()
        val thumb = inputData.getString(KEY_THUMB)
        runCatching { setForeground(foreground(0f, "Đang chuẩn bị…")) }

        val dir = File(applicationContext.cacheDir, "downloads/$id").apply { mkdirs() }
        try {
            var refreshes = 0
            var picked: DownloadChoice? = null
            val files = ArrayList<File>()
            while (true) {
                val engine = EngineManager.get()
                val details = engine.details(url)
                val choice = DownloadChoices.of(details).firstOrNull { it.key == key }
                    ?: return@withContext fail("Video không còn định dạng này.")
                picked = choice
                val tracks = engine.resolve(details.url, choice.video?.id, choice.audio.id).tracks
                files.clear()
                val totals = tracks.map { Fetcher.contentLength(it.uri) }
                val grand = totals.filter { it > 0 }.sum().coerceAtLeast(1)
                try {
                    var before = 0L
                    tracks.forEachIndexed { i, t ->
                        val f = File(dir, "part$i")
                        files += f
                        Fetcher.download(t.uri, f, { isStopped }) { done, _ ->
                            report(((before + done).toFloat() / grand).coerceIn(0f, 0.97f), null)
                        }
                        before += f.length()
                    }
                    break
                } catch (e: ExpiredLinkException) {
                    refreshes++
                    if (refreshes > 3) throw e
                }
            }

            report(0.98f, "Đang ghép video và âm thanh…")
            val choice = picked ?: return@withContext fail("Không tải được.")
            val audioOnly = choice.video == null
            val ext = when {
                audioOnly && choice.webm -> "webm"
                audioOnly -> "m4a"
                choice.webm -> "webm"
                else -> "mp4"
            }
            val name = safeName(title) + if (audioOnly) "" else " (${choice.label})"
            val out = MediaSaver.create(applicationContext, name, ext, audioOnly)
            val uri = try {
                Muxer.merge(files, out.pfd.fileDescriptor, choice.webm)
                MediaSaver.finish(applicationContext, out)
            } catch (t: Throwable) {
                MediaSaver.abort(applicationContext, out)
                throw t
            }
            Library.addDownload(
                DownloadEntry(
                    title = title,
                    thumbnail = thumb,
                    uri = uri.toString(),
                    path = out.file?.absolutePath,
                    mime = out.mime,
                    label = if (audioOnly) "Âm thanh · ${choice.format}" else "${choice.label} · ${choice.format}",
                    bytes = files.sumOf { it.length() },
                    sourceUrl = url,
                    at = System.currentTimeMillis(),
                )
            )
            runCatching { done(uri.toString(), out.mime) }
            Result.success()
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            android.util.Log.e("DownloadWorker", "Download failed", t)
            fail("Tải không thành công. Thử lại sau nhé.")
        } finally {
            dir.deleteRecursively()
            nm.cancel(notifId)
        }
    }

    private fun report(fraction: Float, stage: String?) {
        if (isStopped) return
        val now = System.currentTimeMillis()
        if (stage == null && now - lastUpdate < 800) return
        lastUpdate = now
        setProgressAsync(workDataOf(KEY_PROGRESS to fraction, KEY_TITLE to title))
        nm.notify(notifId, notification(fraction, stage ?: "${(fraction * 100).toInt()}%"))
    }

    private fun notification(fraction: Float, text: String) =
        NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(100, (fraction * 100).toInt(), fraction <= 0f)
            .addAction(
                0, "Huỷ",
                WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)
            )
            .build()

    private fun foreground(fraction: Float, text: String): ForegroundInfo =
        if (Build.VERSION.SDK_INT >= 29) {
            ForegroundInfo(notifId, notification(fraction, text), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notifId, notification(fraction, text))
        }

    private fun done(uri: String, mime: String) {
        val open = Intent(Intent.ACTION_VIEW)
            .setDataAndType(android.net.Uri.parse(uri), mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        val pi = android.app.PendingIntent.getActivity(
            applicationContext, notifId, open,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )
        nm.notify(
            notifId + 1,
            NotificationCompat.Builder(applicationContext, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Đã tải xong")
                .setContentText(title)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build()
        )
    }

    private fun fail(message: String): Result {
        nm.notify(
            notifId + 1,
            NotificationCompat.Builder(applicationContext, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Không tải được: $title")
                .setContentText(message)
                .setAutoCancel(true)
                .build()
        )
        return Result.failure(workDataOf(KEY_TITLE to title))
    }

    companion object {
        const val CHANNEL = "downloads"
        const val TAG = "lucbao-download"
        const val KEY_URL = "url"
        const val KEY_TITLE = "title"
        const val KEY_THUMB = "thumb"
        const val KEY_CHOICE = "choice"
        const val KEY_PROGRESS = "p"

        fun safeName(s: String): String =
            s.replace(Regex("[\\\\/:*?\"<>|\\n\\r\\t]"), " ").replace(Regex("\\s+"), " ").trim()
                .take(120).ifEmpty { "Video" }
    }
}
