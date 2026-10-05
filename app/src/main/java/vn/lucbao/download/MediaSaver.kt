package vn.lucbao.download

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Saves downloads where the Gallery / Music apps can see them: Movies/LucBao and Music/LucBao. */
object MediaSaver {
    class Output(val uri: Uri, val pfd: ParcelFileDescriptor, val file: File?, val mime: String)

    fun mimeFor(ext: String, audio: Boolean): String = when (ext) {
        "mp4" -> "video/mp4"
        "m4a" -> "audio/mp4"
        "webm" -> if (audio) "audio/webm" else "video/webm"
        else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
    }

    fun create(context: Context, displayName: String, ext: String, audio: Boolean): Output {
        val mime = mimeFor(ext, audio)
        val name = "$displayName.$ext"
        if (Build.VERSION.SDK_INT >= 29) {
            val collection = if (audio) MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            else MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, if (audio) "Music/LucBao" else "Movies/LucBao")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(collection, values) ?: error("Không tạo được tệp")
            val pfd = resolver.openFileDescriptor(uri, "rw") ?: error("Không mở được tệp")
            return Output(uri, pfd, null, mime)
        }
        @Suppress("DEPRECATION")
        val dir = File(
            Environment.getExternalStoragePublicDirectory(
                if (audio) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES
            ), "LucBao"
        ).apply { mkdirs() }
        var file = File(dir, name)
        var i = 1
        while (file.exists()) file = File(dir, "$displayName ($i).$ext").also { i++ }
        val pfd = ParcelFileDescriptor.open(
            file,
            ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_CREATE or
                ParcelFileDescriptor.MODE_TRUNCATE
        )
        return Output(Uri.fromFile(file), pfd, file, mime)
    }

    /** Closes the file and makes it visible; returns a content:// Uri to open it with. */
    fun finish(context: Context, out: Output): Uri {
        out.pfd.close()
        if (Build.VERSION.SDK_INT >= 29) {
            context.contentResolver.update(
                out.uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null
            )
            return out.uri
        }
        val file = out.file ?: return out.uri
        val latch = CountDownLatch(1)
        var scanned: Uri? = null
        MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf(out.mime)) { _, uri ->
            scanned = uri
            latch.countDown()
        }
        latch.await(10, TimeUnit.SECONDS)
        return scanned ?: out.uri
    }

    fun abort(context: Context, out: Output) {
        runCatching { out.pfd.close() }
        if (Build.VERSION.SDK_INT >= 29) runCatching { context.contentResolver.delete(out.uri, null, null) }
        else out.file?.delete()
    }

    fun delete(context: Context, uri: String, path: String?) {
        runCatching { context.contentResolver.delete(Uri.parse(uri), null, null) }
        if (path != null) runCatching { File(path).delete() }
    }
}
