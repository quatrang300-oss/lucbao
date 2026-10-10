package vn.lucbao.player

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build
import vn.lucbao.api.AudioOption
import vn.lucbao.api.VideoOption

/** One entry of the quality menu. [video] null means "audio only". */
data class QualityChoice(
    val label: String,
    /** e.g. 1080 */
    val p: Int,
    val fps: Int,
    val video: VideoOption?,
    val badge: String?,
) {
    val audioOnly get() = video == null
}

object Quality {
    const val AUDIO_ONLY = -1

    private val decoders: List<MediaCodecInfo> by lazy {
        runCatching { MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { !it.isEncoder } }
            .getOrDefault(emptyList())
    }

    private fun isHardware(info: MediaCodecInfo): Boolean =
        if (Build.VERSION.SDK_INT >= 29) info.isHardwareAccelerated
        else !info.name.startsWith("OMX.google.") && !info.name.startsWith("c2.android.")

    /** Whether a decoder (hardware only when [hardwareOnly]) can play this size/rate. */
    private fun canDecode(mime: String, w: Int, h: Int, fps: Int, hardwareOnly: Boolean): Boolean =
        decoders.any { info ->
            if (hardwareOnly && !isHardware(info)) return@any false
            if (info.supportedTypes.none { it.equals(mime, ignoreCase = true) }) return@any false
            if (w <= 0 || h <= 0) return@any true
            val caps = runCatching { info.getCapabilitiesForType(mime).videoCapabilities }.getOrNull()
                ?: return@any true
            val rate = if (fps > 0) fps.toDouble() else 30.0
            caps.areSizeAndRateSupported(w, h, rate) || caps.areSizeAndRateSupported(h, w, rate)
        }

    /** Higher is better; negative means this phone cannot play it smoothly. */
    private fun rank(o: VideoOption): Int {
        val codec = o.codec?.lowercase() ?: ""
        val w = o.width
        val h = o.height
        return when {
            codec.startsWith("av01") ->
                if (canDecode("video/av01", w, h, o.fps, hardwareOnly = true)) 30 else -1
            codec.startsWith("vp9") || codec.startsWith("vp09") -> when {
                canDecode("video/x-vnd.on2.vp9", w, h, o.fps, hardwareOnly = true) -> 20
                minOf(w, h) <= 720 && canDecode("video/x-vnd.on2.vp9", w, h, o.fps, false) -> 5
                else -> -1
            }
            codec.startsWith("avc") ->
                if (canDecode("video/avc", w, h, o.fps, hardwareOnly = false)) 10 else -1
            else -> 0
        } + (if (o.videoOnly) 1 else 0)
    }

    private fun shortSide(o: VideoOption): Int =
        if (o.width > 0 && o.height > 0) minOf(o.width, o.height) else o.height

    /** Builds the de-duplicated menu, best first, followed by "audio only". */
    fun choices(options: List<VideoOption>, hasAudio: Boolean): List<QualityChoice> {
        val byLabel = LinkedHashMap<String, Pair<VideoOption, Int>>()
        for (o in options) {
            val r = rank(o)
            if (r < 0) continue
            val p = shortSide(o)
            if (p <= 0) continue
            val fps = if (o.fps > 30) o.fps else 0
            val label = "${p}p" + (if (fps > 0) "$fps" else "")
            val prev = byLabel[label]
            if (prev == null || r > prev.second || (r == prev.second && o.bitrate > prev.first.bitrate)) {
                byLabel[label] = o to r
            }
        }
        val list = byLabel.map { (label, pair) ->
            val o = pair.first
            val p = shortSide(o)
            QualityChoice(label, p, if (o.fps > 30) o.fps else 30, o, badge(p))
        }.sortedWith(compareByDescending<QualityChoice> { it.p }.thenByDescending { it.fps })
        return if (hasAudio) list + QualityChoice("Chỉ âm thanh", AUDIO_ONLY, 0, null, null) else list
    }

    private fun badge(p: Int): String? = when {
        p >= 4320 -> "8K"
        p >= 2160 -> "4K"
        p >= 1440 -> "2K"
        p >= 1080 -> "HD"
        else -> null
    }

    /** Highest choice not above [maxP] (preferring smooth 60fps), else the lowest one. */
    fun pick(choices: List<QualityChoice>, maxP: Int): QualityChoice? {
        if (maxP == AUDIO_ONLY) return choices.firstOrNull { it.audioOnly } ?: choices.firstOrNull()
        val videos = choices.filter { !it.audioOnly }
        return videos.firstOrNull { it.p <= maxP } ?: videos.lastOrNull() ?: choices.firstOrNull()
    }

    fun bestAudio(audios: List<AudioOption>): AudioOption? =
        audios.maxWithOrNull(compareBy<AudioOption> { if (it.original) 1 else 0 }.thenBy { it.bitrate })
}
