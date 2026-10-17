package vn.lucbao.kids

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import vn.lucbao.data.Video
import vn.lucbao.data.toVideo
import vn.lucbao.data.videoKey
import vn.lucbao.engine.EngineManager
import vn.lucbao.player.PlayerController
import java.security.MessageDigest

/** A channel the parents allowed, with the topic they put it under. */
data class KidsChannel(val url: String, val name: String, val avatar: String?, val topic: String)

/** Topic chips on the kids screen (id, label). */
val KIDS_TOPICS = listOf(
    "cartoon" to "Hoạt hình",
    "music" to "Nhạc thiếu nhi",
    "learn" to "Học tập",
    "other" to "Khác",
)

data class KidsFeed(
    val items: List<Video> = emptyList(),
    val loading: Boolean = false,
    /** every channel failed (e.g. no network) */
    val failed: Boolean = false,
)

/**
 * Chế độ Trẻ em: only videos from channels the parents picked, a PIN to leave, and an optional
 * time limit. Kept on the phone (SharedPreferences "kids"); survives restarts, so a child can't
 * leave it by closing the app.
 */
object KidsStore {
    private const val SALT = "lucbao-kids:"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var prefs: SharedPreferences? = null

    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active.asStateFlow()

    private val _channels = MutableStateFlow<List<KidsChannel>>(emptyList())
    val channels: StateFlow<List<KidsChannel>> = _channels.asStateFlow()

    /** minutes per session, 0 = no limit */
    private val _timer = MutableStateFlow(30)
    val timer: StateFlow<Int> = _timer.asStateFlow()

    /** when the current session ends (ms), 0 = no limit */
    private val _sessionEnd = MutableStateFlow(0L)
    val sessionEnd: StateFlow<Long> = _sessionEnd.asStateFlow()

    private val _feed = MutableStateFlow(KidsFeed())
    val feed: StateFlow<KidsFeed> = _feed.asStateFlow()
    private var feedJob: Job? = null
    private var feedLoadedAt = 0L
    private var feedFor: Set<String> = emptySet()

    /** Safe from any thread (read by the player every 150 ms). */
    val isActive: Boolean get() = _active.value

    /** Call once at app start. */
    fun init(context: Context) {
        if (prefs != null) return
        val sp = context.applicationContext.getSharedPreferences("kids", Context.MODE_PRIVATE)
        prefs = sp
        _channels.value = parseChannels(sp.getString("channels", null))
        _timer.value = sp.getInt("timer", 30)
        _sessionEnd.value = sp.getLong("sessionEnd", 0L)
        _active.value = sp.getBoolean("active", false)
        failCount = sp.getInt("fails", 0)
        lockedUntil = sp.getLong("lockedUntil", 0L)
        if (_active.value) hookPlayer(true)
    }

    private fun sp(): SharedPreferences? {
        if (prefs == null) runCatching { init(PlayerController.appContext) }
        return prefs
    }

    // ------------------------------------------------------------------ mode

    /** The KID button on Trang chủ. Whatever was playing stops (it may not be for children). */
    fun enter() {
        if (_active.value) return
        runCatching { PlayerController.stop() }
        _active.value = true
        sp()?.edit()?.putBoolean("active", true)?.apply()
        hookPlayer(true)
        if (hasPin()) startSession()
        refresh(force = false)
    }

    /** Leaves kids mode (after the PIN, or before a PIN was ever set). */
    fun exit() {
        runCatching { PlayerController.stop() }
        _active.value = false
        sp()?.edit()?.putBoolean("active", false)?.apply()
        hookPlayer(false)
    }

    private fun hookPlayer(on: Boolean) {
        val override: (Video) -> List<Video> = { cur -> upNextFor(cur) }
        PlayerController.relatedOverride = if (on) override else null
    }

    // ------------------------------------------------------------------ PIN

    private var failCount = 0
    /** Too many wrong PINs: no attempts accepted before this time (ms). */
    var lockedUntil = 0L
        private set

    /** A wrong PIN. From the 5th one on, waits grow: 30 s, 1 min, 2 min… (kept across restarts). */
    fun recordFail() {
        failCount++
        if (failCount >= 5) lockedUntil = System.currentTimeMillis() + (30_000L shl (failCount - 5).coerceAtMost(5))
        sp()?.edit()?.putInt("fails", failCount)?.putLong("lockedUntil", lockedUntil)?.apply()
    }

    fun recordOk() {
        failCount = 0
        lockedUntil = 0
        sp()?.edit()?.putInt("fails", 0)?.putLong("lockedUntil", 0)?.apply()
    }

    fun hasPin(): Boolean = !sp()?.getString("pin", null).isNullOrEmpty()

    fun checkPin(pin: String): Boolean = sp()?.getString("pin", null) == hash(pin)

    fun setPin(pin: String) {
        sp()?.edit()?.putString("pin", hash(pin))?.apply()
    }

    private fun hash(pin: String): String =
        MessageDigest.getInstance("SHA-256").digest((SALT + pin).toByteArray())
            .joinToString("") { "%02x".format(it) }

    // ------------------------------------------------------------------ time limit

    fun setTimer(minutes: Int) {
        _timer.value = minutes
        sp()?.edit()?.putInt("timer", minutes)?.apply()
    }

    /** A new viewing period from now (entering, or a parent giving more time). */
    fun startSession() {
        val m = _timer.value
        val end = if (m > 0) System.currentTimeMillis() + m * 60_000L else 0L
        _sessionEnd.value = end
        sp()?.edit()?.putLong("sessionEnd", end)?.apply()
    }

    /** True when kids mode is on and its time is used up. */
    fun isTimeUp(now: Long = System.currentTimeMillis()): Boolean {
        val end = _sessionEnd.value
        return _active.value && end > 0 && now >= end
    }

    // ------------------------------------------------------------------ channels

    fun addChannel(ch: KidsChannel) {
        val list = _channels.value.filterNot { it.url == ch.url } + ch
        saveChannels(list)
        refresh(force = true)
    }

    fun removeChannel(url: String) {
        saveChannels(_channels.value.filterNot { it.url == url })
        _feed.value = _feed.value.copy(items = _feed.value.items.filterNot { it.channelUrl == url })
    }

    fun isAllowed(url: String?): Boolean = url != null && _channels.value.any { it.url == url }

    private fun saveChannels(list: List<KidsChannel>) {
        _channels.value = list
        val arr = JSONArray()
        list.forEach { c ->
            arr.put(JSONObject().put("url", c.url).put("name", c.name).put("avatar", c.avatar ?: "").put("topic", c.topic))
        }
        sp()?.edit()?.putString("channels", arr.toString())?.apply()
    }

    private fun parseChannels(json: String?): List<KidsChannel> = runCatching {
        val arr = JSONArray(json ?: return emptyList())
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val url = o.optString("url")
            if (url.isBlank()) null
            else KidsChannel(url, o.optString("name"), o.optString("avatar").ifBlank { null }, o.optString("topic", "other"))
        }
    }.getOrDefault(emptyList())

    // ------------------------------------------------------------------ videos

    /** Newest videos of every allowed channel, mixed so no channel takes over the screen. */
    fun refresh(force: Boolean) {
        val channels = _channels.value
        val urls = channels.map { it.url }.toSet()
        val fresh = System.currentTimeMillis() - feedLoadedAt < 30 * 60_000L
        if (!force && fresh && urls == feedFor && _feed.value.items.isNotEmpty()) return
        feedJob?.cancel()
        if (channels.isEmpty()) {
            _feed.value = KidsFeed()
            return
        }
        _feed.value = _feed.value.copy(loading = true, failed = false)
        feedJob = scope.launch {
            try {
                val engine = withContext(Dispatchers.IO) { EngineManager.get() }
                val lists = withContext(Dispatchers.IO) {
                    channels.map { ch ->
                        async {
                            runCatching {
                                engine.kiosk("feed:" + ch.url, null).items
                                    .filter { !it.live }
                                    .map { item -> item.toVideo().let { v -> v.copy(channelUrl = ch.url, channel = v.channel.ifBlank { ch.name }) } }
                            }.getOrNull()
                        }
                    }.awaitAll()
                }
                val ok = lists.filterNotNull()
                val mixed = ArrayList<Video>()
                val max = ok.maxOfOrNull { it.size } ?: 0
                for (i in 0 until max) ok.forEach { l -> l.getOrNull(i)?.let(mixed::add) }
                // Feeds return nothing (rather than an error) when offline: empty means it failed.
                _feed.value = KidsFeed(items = mixed.distinctBy { videoKey(it.url) }, failed = mixed.isEmpty())
                feedLoadedAt = System.currentTimeMillis()
                feedFor = urls
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                _feed.value = _feed.value.copy(loading = false, failed = true)
            }
        }
    }

    /** "Tiếp theo" and autoplay in kids mode: the kids' own list, starting after [cur]. */
    private fun upNextFor(cur: Video): List<Video> {
        val items = _feed.value.items
        if (items.isEmpty()) return emptyList()
        val key = videoKey(cur.url)
        val i = items.indexOfFirst { videoKey(it.url) == key }
        val ordered = if (i < 0) items else items.drop(i + 1) + items.take(i)
        return ordered.filter { videoKey(it.url) != key }.take(30)
    }
}
