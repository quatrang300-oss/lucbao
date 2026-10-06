package vn.lucbao.shorts

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
import vn.lucbao.data.Library
import vn.lucbao.data.Video
import vn.lucbao.data.videoKey
import vn.lucbao.engine.EngineManager
import vn.lucbao.ui.components.errorText
import vn.lucbao.update.Updater

/** A topic chip at the top of the Shorts tab. */
data class ShortsTopic(val id: String, val label: String, val queries: List<String>)

val SHORTS_TOPICS = listOf(
    ShortsTopic("all", "Dành cho bạn", listOf("#shorts việt nam", "#shorts", "#shorts hài hước", "#shorts xu hướng")),
    ShortsTopic("funny", "Hài hước", listOf("#shorts hài", "#shorts funny", "#shorts hài hước việt nam")),
    ShortsTopic("music", "Âm nhạc", listOf("#shorts nhạc", "#shorts cover", "#shorts music")),
    ShortsTopic("food", "Ẩm thực", listOf("#shorts ẩm thực", "#shorts nấu ăn", "#shorts food")),
    ShortsTopic("sport", "Thể thao", listOf("#shorts bóng đá", "#shorts thể thao", "#shorts football")),
    ShortsTopic("pets", "Thú cưng", listOf("#shorts chó mèo", "#shorts thú cưng", "#shorts cute animals")),
    ShortsTopic("following", "Kênh theo dõi", emptyList()),
)

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

    /** Remembered position (index in the feed) per topic is not needed: the feed is per topic. */
    private val tokens = HashMap<String, String?>()
    private val exhausted = HashSet<String>()
    private var round = 0
    private var emptyStreak = 0
    private var job: Job? = null
    private val seen = HashSet<String>()

    init {
        loadMore()
    }

    fun selectTopic(t: ShortsTopic) {
        if (t.id == _topic.value.id) return
        _topic.value = t
        reset()
        loadMore()
    }

    fun refresh() {
        reset()
        loadMore()
    }

    private fun reset() {
        job?.cancel()
        tokens.clear()
        exhausted.clear()
        seen.clear()
        round = 0
        emptyStreak = 0
        _feed.value = ShortsFeed()
    }

    fun loadMore() {
        val f = _feed.value
        if (f.loading || f.end) return
        val t = _topic.value
        _feed.update { it.copy(loading = true, error = null) }
        job = viewModelScope.launch {
            try {
                val fresh = if (t.id == "following") loadFollowing() else loadQueries(t)
                val unique = fresh.filter { seen.add(videoKey(it.url)) }
                val noMore = t.id == "following" || t.queries.all { it in exhausted }
                emptyStreak = if (unique.isEmpty()) emptyStreak + 1 else 0
                _feed.update {
                    it.copy(items = it.items + unique, loading = false, end = noMore || emptyStreak >= 3)
                }
                // Nothing new this round (all duplicates): try the next round.
                if (unique.isEmpty() && !noMore && emptyStreak < 3) loadMore()
            } catch (c: CancellationException) {
                throw c
            } catch (t2: Throwable) {
                _feed.update { it.copy(loading = false, error = describe(t2)) }
            }
        }
    }

    /** One page from two of the topic's searches at a time, mixed together. */
    private suspend fun loadQueries(t: ShortsTopic): List<Video> {
        val live = t.queries.filterNot { it in exhausted }
        if (live.isEmpty()) return emptyList()
        val pick = listOf(live[round % live.size], live[(round + 1) % live.size]).distinct()
        round++
        val pages = withContext(Dispatchers.IO) {
            pick.map { q ->
                async {
                    if (tokens.containsKey(q) && tokens[q] == null) {
                        return@async Triple<String, ShortsPage?, Throwable?>(q, ShortsPage(emptyList(), null), null)
                    }
                    try {
                        Triple<String, ShortsPage?, Throwable?>(q, ShortsRepo.search(q, tokens[q]), null)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: ShortsUnsupportedException) {
                        throw e
                    } catch (e: Throwable) {
                        Triple<String, ShortsPage?, Throwable?>(q, null, e)
                    }
                }
            }.awaitAll()
        }
        // Every search failed (e.g. no network): report it instead of "nothing here".
        if (pages.all { it.second == null }) throw pages.first().third ?: IllegalStateException()
        val lists = pages.mapNotNull { (q, page, _) ->
            if (page == null) return@mapNotNull null // failed: keep its place, try again later
            tokens[q] = page.next
            if (page.next == null) exhausted.add(q)
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

    private fun describe(t: Throwable): String = when {
        t is ShortsUnsupportedException -> "Bộ phát YouTube đang tự cập nhật để có mục Shorts. Thử lại sau ít phút nhé."
        t.message == "NO_CHANNELS" -> "Bạn chưa theo dõi kênh nào. Bấm Theo dõi ở một video để xem Shorts của kênh đó tại đây."
        else -> errorText(EngineManager.classify(t))
    }
}
