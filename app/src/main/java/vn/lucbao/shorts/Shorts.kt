package vn.lucbao.shorts

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import vn.lucbao.player.PlayerController
import kotlin.random.Random
import vn.lucbao.data.Library
import vn.lucbao.data.Video
import vn.lucbao.data.videoKey
import vn.lucbao.engine.EngineManager
import vn.lucbao.ui.components.errorText
import vn.lucbao.update.Updater

/** A topic chip at the top of the Shorts tab. */
data class ShortsTopic(val id: String, val label: String, val queries: List<String>)

val SHORTS_TOPICS = listOf(
    ShortsTopic(
        "all", "Dành cho bạn", listOf(
            "#shorts việt nam", "#shorts", "#shorts hài hước", "#shorts xu hướng", "#shorts viral",
            "#shorts mới nhất", "#shorts hôm nay", "#shorts trending việt nam", "#shorts thú vị", "#shorts đời sống",
        )
    ),
    ShortsTopic(
        "funny", "Hài hước", listOf(
            "#shorts hài", "#shorts funny", "#shorts hài hước việt nam", "#shorts troll",
            "#shorts hài mới nhất", "#shorts comedy", "#shorts hài hôm nay",
        )
    ),
    ShortsTopic(
        "music", "Âm nhạc", listOf(
            "#shorts nhạc", "#shorts cover", "#shorts music", "#shorts nhạc trẻ",
            "#shorts nhạc hot", "#shorts hát live", "#shorts nhạc mới",
        )
    ),
    ShortsTopic(
        "food", "Ẩm thực", listOf(
            "#shorts ẩm thực", "#shorts nấu ăn", "#shorts food", "#shorts món ngon",
            "#shorts street food việt nam", "#shorts mukbang", "#shorts công thức nấu ăn",
        )
    ),
    ShortsTopic(
        "sport", "Thể thao", listOf(
            "#shorts bóng đá", "#shorts thể thao", "#shorts football", "#shorts bóng đá việt nam",
            "#shorts football skills", "#shorts highlights", "#shorts gym",
        )
    ),
    ShortsTopic(
        "pets", "Thú cưng", listOf(
            "#shorts chó mèo", "#shorts thú cưng", "#shorts cute animals", "#shorts mèo",
            "#shorts chó", "#shorts funny animals", "#shorts pets",
        )
    ),
    ShortsTopic("following", "Kênh theo dõi", emptyList()),
)

/**
 * Shorts the user has already swiped past (newest last), kept across app restarts so the feed
 * doesn't show the same ones again every time.
 */
object ShortsHistory {
    private const val MAX = 1000
    private val keys = LinkedHashSet<String>()
    private var loaded = false

    private fun prefs() = PlayerController.appContext.getSharedPreferences("shorts", Context.MODE_PRIVATE)

    @Synchronized
    private fun load() {
        if (loaded) return
        loaded = true
        runCatching {
            prefs().getString("seen", null)?.split('\n')?.filter { it.isNotBlank() }?.let(keys::addAll)
        }
    }

    @Synchronized
    fun contains(key: String): Boolean {
        load()
        return key in keys
    }

    @Synchronized
    fun add(key: String) {
        load()
        if (!keys.add(key)) return
        while (keys.size > MAX) keys.remove(keys.first())
        runCatching { prefs().edit().putString("seen", keys.joinToString("\n")).apply() }
    }
}

class ShortsPage(val items: List<Video>, val next: String?)

class ShortsUnsupportedException : Exception("Engine has no shorts support yet")

/** Shorts through the engine (methods found by name, like the Music tab). */
object ShortsRepo {
    suspend fun search(query: String, token: String?): ShortsPage = parse(call("shortsJson", query, token))

    suspend fun channel(url: String): ShortsPage = parse(call("channelShortsJson", url))

    private suspend fun call(name: String, vararg args: String?): String = withContext(Dispatchers.IO) {
        val engine = EngineManager.get()
        val types = Array(args.size) { String::class.java }
        val method = runCatching { engine.javaClass.getMethod(name, *types) }.getOrNull()
        if (method == null) {
            Updater.requestEngineCheck(vn.lucbao.player.PlayerController.appContext)
            throw ShortsUnsupportedException()
        }
        try {
            method.invoke(engine, *args) as String? ?: "{}"
        } catch (e: java.lang.reflect.InvocationTargetException) {
            throw e.targetException ?: e
        }
    }

    private fun parse(json: String): ShortsPage {
        val o = JSONObject(json)
        val arr = o.optJSONArray("items")
        val out = ArrayList<Video>()
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val it = arr.optJSONObject(i) ?: continue
                val url = it.optString("url")
                if (url.isBlank()) continue
                out.add(
                    Video(
                        url = url,
                        title = it.optString("title"),
                        channel = it.optString("channel"),
                        thumbnail = it.optString("thumb").ifBlank { null },
                        duration = it.optLong("duration", -1),
                        views = it.optLong("views", -1),
                        channelUrl = it.optString("channelUrl").ifBlank { null },
                    )
                )
            }
        }
        val next = if (o.isNull("next")) null else o.optString("next").ifBlank { null }
        return ShortsPage(out, next)
    }
}

data class ShortsFeed(
    val items: List<Video> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    /** nothing more to load for this topic */
    val end: Boolean = false,
)

class ShortsViewModel : ViewModel() {
    private val _topic = MutableStateFlow(SHORTS_TOPICS.first())
    val topic: StateFlow<ShortsTopic> = _topic.asStateFlow()

    private val _feed = MutableStateFlow(ShortsFeed())
    val feed: StateFlow<ShortsFeed> = _feed.asStateFlow()

    /** Bumped on every new feed, so the screen can jump back to the first short. */
    private val _generation = MutableStateFlow(0)
    val generation: StateFlow<Int> = _generation.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    /** A pull-down refresh is in progress (shows the spinner at the top). */
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val tokens = HashMap<String, String?>()
    private val exhausted = HashSet<String>()
    private var order: List<String> = emptyList()
    private var round = 0
    private var emptyStreak = 0
    private var job: Job? = null
    private val seen = HashSet<String>()
    /** Already watched (or skipped-page) shorts, shown only if nothing new is left. */
    private val reserve = ArrayList<Video>()
    private var lastVisit = -1
    private var stoppedAt = 0L

    init {
        reset()
        loadMore()
    }

    fun selectTopic(t: ShortsTopic) {
        if (t.id == _topic.value.id) {
            refresh() // tapping the current topic again: new shorts
            return
        }
        _topic.value = t
        reset()
        loadMore()
    }

    fun refresh() {
        reset()
        loadMore()
    }

    /** Pull down on the first short. */
    fun pullRefresh() {
        if (_refreshing.value) return
        _refreshing.value = true
        refresh()
    }

    /** The Shorts tab was opened ([visit] counts visits): a new visit starts with a new feed. */
    fun enterTab(visit: Int) {
        if (lastVisit == -1) {
            lastVisit = visit // the feed loaded in init is already fresh
            return
        }
        if (visit != lastVisit) {
            lastVisit = visit
            refresh()
        }
    }

    fun onAppStopped() {
        stoppedAt = System.currentTimeMillis()
    }

    /** True (and a new feed is loading) when the app comes back after a long break. */
    fun refreshIfStale(): Boolean {
        val away = stoppedAt > 0 && System.currentTimeMillis() - stoppedAt > STALE_MS
        stoppedAt = 0
        if (away) refresh()
        return away
    }

    /** The user actually watched this short. */
    fun markWatched(v: Video) {
        ShortsHistory.add(videoKey(v.url))
    }

    private fun reset() {
        job?.cancel()
        job = null
        tokens.clear()
        exhausted.clear()
        seen.clear()
        reserve.clear()
        order = _topic.value.queries.shuffled()
        round = 0
        emptyStreak = 0
        _feed.value = ShortsFeed()
        _generation.value++
    }

    fun loadMore() {
        val f = _feed.value
        if (f.loading || f.end) return
        val t = _topic.value
        _feed.update { it.copy(loading = true, error = null) }
        job = viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { ShortsHistory.contains("") } // read it from disk off the main thread
                val fresh = if (t.id == "following") loadFollowing() else loadQueries(t)
                var unique = ArrayList<Video>()
                for (v in fresh.shuffled()) {
                    val k = videoKey(v.url)
                    if (k in seen) continue
                    seen.add(k)
                    if (ShortsHistory.contains(k)) reserve.add(v) else unique.add(v)
                }
                val noMore = t.id == "following" || t.queries.all { it in exhausted }
                emptyStreak = if (unique.isEmpty()) emptyStreak + 1 else 0
                var end = noMore || emptyStreak >= MAX_EMPTY_ROUNDS
                if (end && reserve.isNotEmpty()) {
                    // Nothing new left: show the already watched ones rather than an empty feed.
                    unique = ArrayList(unique + reserve.shuffled())
                    reserve.clear()
                    emptyStreak = 0
                    end = noMore
                }
                _feed.update { it.copy(items = it.items + unique, loading = false, end = end) }
                _refreshing.value = false
                // Nothing new this round (all duplicates or watched): try the next round.
                if (unique.isEmpty() && !end) loadMore()
            } catch (c: CancellationException) {
                throw c
            } catch (t2: Throwable) {
                _refreshing.value = false
                _feed.update { it.copy(loading = false, error = describe(t2)) }
            }
        }
    }

    private class QueryResult(val query: String, val page: ShortsPage?, val skipped: List<Video>, val error: Throwable?)

    /** One page from two of the topic's searches at a time, mixed together. */
    private suspend fun loadQueries(t: ShortsTopic): List<Video> {
        val live = order.filterNot { it in exhausted }
        if (live.isEmpty()) return emptyList()
        val pick = listOf(live[round % live.size], live[(round + 1) % live.size]).distinct()
        round++
        // Page tokens are read here on the main thread (a refresh may clear them meanwhile).
        val tok = pick.associateWith { q -> if (tokens.containsKey(q)) tokens[q] else UNSET }
        val results = withContext(Dispatchers.IO) {
            pick.map { q ->
                async {
                    val token = tok[q]
                    if (token == null) {
                        return@async QueryResult(q, ShortsPage(emptyList(), null), emptyList(), null)
                    }
                    try {
                        val first = token == UNSET
                        var page = ShortsRepo.search(q, if (first) null else token)
                        var skipped: List<Video> = emptyList()
                        // Now and then start one page deeper, so the feed doesn't always open
                        // with the same top results. The skipped page is kept as a reserve.
                        if (first && page.next != null && Random.nextInt(3) == 0) {
                            val deeper = runCatching { ShortsRepo.search(q, page.next) }.getOrNull()
                            if (deeper != null && deeper.items.isNotEmpty()) {
                                skipped = page.items
                                page = deeper
                            }
                        }
                        QueryResult(q, page, skipped, null)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: ShortsUnsupportedException) {
                        throw e
                    } catch (e: Throwable) {
                        QueryResult(q, null, emptyList(), e)
                    }
                }
            }.awaitAll()
        }
        // Every search failed (e.g. no network): report it instead of "nothing here".
        if (results.all { it.page == null }) throw results.first().error ?: IllegalStateException()
        val lists = results.mapNotNull { r ->
            val page = r.page ?: return@mapNotNull null // failed: keep its place, try again later
            tokens[r.query] = page.next
            if (page.next == null) exhausted.add(r.query)
            r.skipped.forEach { v -> if (seen.add(videoKey(v.url))) reserve.add(v) }
            page.items
        }
        return interleave(lists).let { if (t.id == "all") mixFollowing(it) else it }
    }

    /** "Dành cho bạn" sprinkles in shorts from followed channels the first time. */
    private suspend fun mixFollowing(list: List<Video>): List<Video> {
        if (round > 1) return list
        val extra = runCatching { loadFollowing(limitChannels = 3) }.getOrDefault(emptyList()).shuffled().take(8)
        return interleave(listOf(list, extra))
    }

    private suspend fun loadFollowing(limitChannels: Int = 8): List<Video> {
        val channels = Library.channels.value.shuffled().take(limitChannels)
        if (channels.isEmpty()) {
            if (_topic.value.id == "following") throw IllegalStateException("NO_CHANNELS")
            return emptyList()
        }
        val lists = withContext(Dispatchers.IO) {
            channels.map { ch ->
                async { runCatching { ShortsRepo.channel(ch.url).items.take(10) }.getOrDefault(emptyList()) }
            }.awaitAll()
        }
        return interleave(lists)
    }

    private fun interleave(lists: List<List<Video>>): List<Video> {
        val out = ArrayList<Video>()
        val max = lists.maxOfOrNull { it.size } ?: 0
        for (i in 0 until max) lists.forEach { l -> l.getOrNull(i)?.let(out::add) }
        return out
    }

    private companion object {
        /** Rounds in a row with nothing new before the feed is considered finished. */
        const val MAX_EMPTY_ROUNDS = 5
        /** Coming back to the app after this long shows a new feed. */
        const val STALE_MS = 15 * 60_000L
        /** "never loaded" marker for a query's page token (null means no more pages) */
        const val UNSET = "\u0000"
    }

    private fun describe(t: Throwable): String = when {
        t is ShortsUnsupportedException -> "Bộ phát YouTube đang tự cập nhật để có mục Shorts. Thử lại sau ít phút nhé."
        t.message == "NO_CHANNELS" -> "Bạn chưa theo dõi kênh nào. Bấm Theo dõi ở một video để xem Shorts của kênh đó tại đây."
        else -> errorText(EngineManager.classify(t))
    }
}
