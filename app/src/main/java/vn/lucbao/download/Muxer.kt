package vn.lucbao.download

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.io.FileDescriptor
import java.nio.ByteBuffer

/** Joins the downloaded video and audio files into one MP4/WebM (or remuxes audio alone). */
object Muxer {
    private class Source(val extractor: MediaExtractor, val track: Int, val buffer: ByteBuffer) {
        var done = false
    }

    fun merge(inputs: List<File>, output: FileDescriptor, webm: Boolean) {
        val muxer = MediaMuxer(
            output,
            if (webm) MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM
            else MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
        )
        val sources = ArrayList<Source>()
        try {
            for (file in inputs) {
                val ex = MediaExtractor()
                ex.setDataSource(file.absolutePath)
                val index = (0 until ex.trackCount).firstOrNull { i ->
                    val mime = ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: ""
                    mime.startsWith("video/") || mime.startsWith("audio/")
                } ?: error("No media track in ${file.name}")
                ex.selectTrack(index)
                val format = ex.getTrackFormat(index)
                val isVideo = format.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true
                val declared = if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE))
                    format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) else 0
                val size = maxOf(declared, if (isVideo) 8 shl 20 else 1 shl 20)
                sources += Source(ex, muxer.addTrack(format), ByteBuffer.allocateDirect(size))
            }
            muxer.start()
            val info = MediaCodec.BufferInfo()
            while (true) {
                // Interleave by timestamp.
                val next = sources.filter { !it.done }.minByOrNull { it.extractor.sampleTime } ?: break
                val ex = next.extractor
                next.buffer.clear()
                val n = ex.readSampleData(next.buffer, 0)
                if (n < 0) {
                    next.done = true
                    continue
                }
                val flags = if (ex.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0)
                    MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                info.set(0, n, ex.sampleTime, flags)
                muxer.writeSampleData(next.track, next.buffer, info)
                ex.advance()
            }
            muxer.stop()
        } finally {
            runCatching { muxer.release() }
            sources.forEach { runCatching { it.extractor.release() } }
        }
    }
}
