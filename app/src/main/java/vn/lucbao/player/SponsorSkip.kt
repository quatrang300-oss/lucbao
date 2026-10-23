package vn.lucbao.player

import android.util.Log
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import vn.lucbao.data.Prefs
import vn.lucbao.data.Settings
import vn.lucbao.data.videoKey
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest

/** One part of a video marked by the SponsorBlock community. */
data class SkipSegment(val startMs: Long, val endMs: Long, val category: String, val uuid: String)

/** "Đã bỏ qua đoạn tài trợ · Xem lại", shown for a few seconds after a skip. */
data class SkipNotice(val label: String, val segment: SkipSegment, val at: Long = System.currentTimeMillis())

/**
 * Skips sponsor reads, self-promotion, "like & subscribe" reminders… that the video's own
 * creator put inside the video. Uses the free SponsorBlock database (sponsor.ajay.app). Only the
 * first 4 characters of a hash of the video id are sent, so the server does not learn which
 * video is being watched.
 */
object SponsorSkip {
    private const val TAG = "SponsorSkip"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null
    @Volatile private var segments: List<SkipSegment> = emptyList()
    /** Segments the viewer chose to watch ("Xem lại"): not skipped again for this video. */
    private val allowed = HashSet<String>()
    private var lastSkip = 0L

    private val _notice = MutableStateFlow<SkipNotice?>(null)
    val notice: StateFlow<SkipNotice?> = _notice.asStateFlow()

    fun categories(s: Settings): List<String> = buildList {
        if (s.sbSponsor) add("sponsor")
        if (s.sbSelfPromo) add("selfpromo")
        if (s.sbInteraction) add("interaction")
        if (s.sbIntroOutro) { add("intro"); add("outro") }
        if (s.sbMusicOfftopic) add("music_offtopic")
    }

    fun label(category: String): String = when (category) {
        "sponsor" -> "đoạn tài trợ"
        "selfpromo" -> "đoạn tự quảng cáo"
        "interaction" -> "đoạn nhắc like/đăng ký"
        "intro" -> "đoạn mở đầu"
        "outro" -> "đoạn kết thúc"
        "music_offtopic" -> "đoạn không phải nhạc"
        else -> "một đoạn"
    }

    /** A new video: forget the old segments and look up this one's (never for live streams). */
    fun load(url: String, live: Boolean) {
        job?.cancel()
        segments = emptyList()
        allowed.clear()
        _notice.value = null
        val wanted = categories(Prefs.current)
        if (live || wanted.isEmpty()) return
        val id = videoKey(url).takeIf { it.length == 11 } ?: return
        job = scope.launch {
            try {
                segments = withContext(Dispatchers.IO) { fetch(id, wanted) }
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                Log.i(TAG, "No segments: ${t.message}")
            }
        }
    }

    fun clear() {
        job?.cancel()
        segments = emptyList()
        allowed.clear()
        _notice.value = null
    }

    /** Called a few times a second while playing. */
    fun tick(exo: ExoPlayer) {
        val list = segments
        if (list.isEmpty() || !exo.playWhenReady) return
        val now = System.currentTimeMillis()
        if (now - lastSkip < 800) return
        val wanted = categories(Prefs.current)
        val pos = exo.currentPosition
        val seg = list.firstOrNull {
            pos >= it.startMs && pos < it.endMs - 400 && it.uuid !in allowed && it.category in wanted
        } ?: return
        lastSkip = now
        exo.seekTo(seg.endMs)
        _notice.value = SkipNotice(label(seg.category), seg)
    }

    /** "Xem lại": back to the start of the skipped part, and let it play this time. */
    fun rewatch(exo: ExoPlayer) {
        val n = _notice.value ?: return
        allowed.add(n.segment.uuid)
        exo.seekTo(n.segment.startMs)
        _notice.value = null
    }

    fun dismissNotice() {
        _notice.value = null
    }

    private fun fetch(id: String, categories: List<String>): List<SkipSegment> {
        val hash = MessageDigest.getInstance("SHA-256").digest(id.toByteArray())
            .joinToString("") { "%02x".format(it) }.take(4)
        val cats = URLEncoder.encode(JSONArray(categories).toString(), "UTF-8")
        val url = URL("https://sponsor.ajay.app/api/skipSegments/$hash?categories=$cats&actionType=skip")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
            setRequestProperty("User-Agent", "LucBao")
        }
        try {
            if (conn.responseCode == 404) return emptyList() // no video with this hash prefix
            if (conn.responseCode != 200) return emptyList()
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val arr = JSONArray(body)
            for (i in 0 until arr.length()) {
                val v = arr.optJSONObject(i) ?: continue
                if (v.optString("videoID") != id) continue
                val segs = v.optJSONArray("segments") ?: return emptyList()
                val out = ArrayList<SkipSegment>()
                for (j in 0 until segs.length()) {
                    val s = segs.optJSONObject(j) ?: continue
                    if (s.optString("actionType", "skip") != "skip") continue
                    val range = s.optJSONArray("segment") ?: continue
                    val start = (range.optDouble(0) * 1000).toLong()
                    val end = (range.optDouble(1) * 1000).toLong()
                    if (end - start < 1_000) continue
                    out.add(SkipSegment(start, end, s.optString("category"), s.optString("UUID")))
                }
                return out.sortedBy { it.startMs }
            }
            return emptyList()
        } finally {
            conn.disconnect()
        }
    }
}
