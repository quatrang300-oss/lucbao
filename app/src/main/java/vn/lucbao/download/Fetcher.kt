package vn.lucbao.download

import vn.lucbao.api.Track
import vn.lucbao.engine.EngineManager
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CancellationException

/** The stream link has expired: fetch fresh links and continue. */
class ExpiredLinkException : IOException("Link expired")

/**
 * Downloads a YouTube stream in 8 MB pieces, shaping each request through the engine
 * exactly like the player does (YouTube throttles or refuses one big request).
 * Resumes from what is already in [target].
 */
object Fetcher {
    private const val CHUNK = 8L * 1024 * 1024

    fun contentLength(url: String): Long =
        Regex("[?&]clen=(\\d+)").find(url)?.groupValues?.get(1)?.toLongOrNull() ?: -1

    fun download(
        url: String,
        target: File,
        isStopped: () -> Boolean,
        onProgress: (done: Long, total: Long) -> Unit,
    ) {
        val total = contentLength(url)
        var pos = if (target.exists()) target.length() else 0L
        FileOutputStream(target, true).use { out ->
            while (total < 0 || pos < total) {
                if (isStopped()) throw CancellationException("stopped")
                val len = if (total > 0) minOf(CHUNK, total - pos) else CHUNK
                var attempt = 0
                var n: Long
                while (true) {
                    try {
                        n = fetchRange(url, pos, len, out)
                        break
                    } catch (e: ExpiredLinkException) {
                        throw e
                    } catch (e: IOException) {
                        attempt++
                        if (attempt >= 4) throw e
                        // Drop the half-written piece before asking for it again.
                        out.flush()
                        out.channel.truncate(pos)
                        Thread.sleep(1500L * attempt)
                    }
                }
                pos += n
                onProgress(pos, total)
                if (n <= 0 || (total < 0 && n < len)) break
            }
            if (total > 0 && pos < total) throw IOException("Incomplete: $pos/$total")
        }
    }

    private fun fetchRange(url: String, pos: Long, len: Long, out: OutputStream): Long {
        val plan = EngineManager.get().shapeRequest(url, pos, len, Track.KIND_DASH)
        val c = URL(plan.url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 20_000
            c.readTimeout = 30_000
            c.instanceFollowRedirects = true
            c.requestMethod = plan.method
            plan.headers?.forEach { (k, v) -> c.setRequestProperty(k, v) }
            c.setRequestProperty("Accept-Encoding", "identity")
            if (!plan.rangeInUrl) c.setRequestProperty("Range", "bytes=$pos-${pos + len - 1}")
            val body = plan.body
            if (body != null) {
                c.doOutput = true
                c.setFixedLengthStreamingMode(body.size)
                c.outputStream.use { it.write(body) }
            }
            val code = c.responseCode
            if (code == 416) return 0
            if (code == 403 || code == 410) throw ExpiredLinkException()
            if (code !in 200..299) throw IOException("HTTP $code")
            if (code == 200 && pos > 0 && !plan.rangeInUrl) throw IOException("Range ignored")
            var written = 0L
            val buf = ByteArray(64 * 1024)
            c.inputStream.use { input ->
                while (written < len) {
                    val want = minOf(buf.size.toLong(), len - written).toInt()
                    val r = input.read(buf, 0, want)
                    if (r < 0) break
                    out.write(buf, 0, r)
                    written += r
                }
            }
            return written
        } finally {
            c.disconnect()
        }
    }
}
