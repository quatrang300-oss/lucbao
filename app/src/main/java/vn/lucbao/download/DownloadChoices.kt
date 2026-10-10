package vn.lucbao.download

import vn.lucbao.api.AudioOption
import vn.lucbao.api.VideoDetails
import vn.lucbao.api.VideoOption

/** One entry of the download menu. [video] null means "audio only". */
data class DownloadChoice(
    /** stable key, e.g. "v:1080p60:mp4" or "a:m4a" */
    val key: String,
    val label: String,
    val p: Int,
    val fps: Int,
    val video: VideoOption?,
    val audio: AudioOption,
    /** true → WebM container (VP9 + Opus), false → MP4/M4A */
    val webm: Boolean,
    /** rough size estimate, -1 when unknown */
    val bytes: Long,
) {
    val format: String
        get() = when {
            video == null && webm -> "WebM"
            video == null -> "M4A"
            webm -> "WebM"
            else -> "MP4"
        }
    val badge: String?
        get() = when {
            p >= 4320 -> "8K"
            p >= 2160 -> "4K"
            p >= 1440 -> "2K"
            p >= 1080 -> "HD"
            else -> null
        }
}

object DownloadChoices {
    /** Only plain files can be downloaded (segmented "OTF" streams cannot). */
    private fun progressive(id: String?) = id?.endsWith("PROGRESSIVE_HTTP") == true

    private fun isAvc(o: VideoOption) = o.codec?.lowercase()?.startsWith("avc") == true
    private fun isVp9(o: VideoOption) = o.codec?.lowercase()?.let { it.startsWith("vp9") || it.startsWith("vp09") } == true
    private fun isAac(a: AudioOption) =
        a.codec?.lowercase()?.startsWith("mp4a") == true || a.mime?.contains("mp4") == true
    private fun isOpus(a: AudioOption) =
        a.codec?.lowercase()?.contains("opus") == true || a.mime?.contains("webm") == true

    private val audioOrder = compareBy<AudioOption>({ if (it.original) 1 else 0 }, { it.bitrate })

    private fun shortSide(o: VideoOption) =
        if (o.width > 0 && o.height > 0) minOf(o.width, o.height) else o.height

    private fun size(bitsPerSecond: Long, seconds: Long): Long =
        if (bitsPerSecond <= 0 || seconds <= 0) -1 else bitsPerSecond / 8 * seconds

    fun of(d: VideoDetails): List<DownloadChoice> {
        if (d.live) return emptyList()
        val seconds = d.duration
        val audios = d.audioOptions.filter { progressive(it.id) }
        val aac = audios.filter(::isAac).maxWithOrNull(audioOrder)
        val opus = audios.filter(::isOpus).maxWithOrNull(audioOrder)

        val out = ArrayList<DownloadChoice>()
        d.videoOptions
            .filter { it.videoOnly && progressive(it.id) && shortSide(it) > 0 }
            .groupBy { o -> "${shortSide(o)}p" + (if (o.fps > 30) "${o.fps}" else "") }
            .forEach { (label, opts) ->
                val avc = opts.filter(::isAvc).maxByOrNull { it.bitrate }
                val vp9 = opts.filter(::isVp9).maxByOrNull { it.bitrate }
                val (video, audio, webm) = when {
                    avc != null && aac != null -> Triple(avc, aac, false)
                    vp9 != null && opus != null -> Triple(vp9, opus, true)
                    else -> return@forEach
                }
                out += DownloadChoice(
                    key = "v:$label:" + (if (webm) "webm" else "mp4"),
                    label = label,
                    p = shortSide(video),
                    fps = if (video.fps > 30) video.fps else 30,
                    video = video,
                    audio = audio,
                    webm = webm,
                    bytes = size(video.bitrate.toLong() + audio.bitrate, seconds),
                )
            }
        out.sortWith(compareByDescending<DownloadChoice> { it.p }.thenByDescending { it.fps })

        val music = aac ?: opus
        if (music != null) {
            val webm = music !== aac
            out += DownloadChoice(
                key = "a:" + (if (webm) "webm" else "m4a"),
                label = "Chỉ âm thanh",
                p = -1, fps = 0, video = null, audio = music, webm = webm,
                bytes = size(music.bitrate.toLong(), seconds),
            )
        }
        return out
    }
}
