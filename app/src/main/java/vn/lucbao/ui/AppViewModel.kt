package vn.lucbao.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vn.lucbao.api.ErrorKind
import vn.lucbao.api.Kiosk
import vn.lucbao.data.Library
import vn.lucbao.data.Video
import vn.lucbao.data.toVideo
import vn.lucbao.data.videoKey
import vn.lucbao.engine.EngineManager
import vn.lucbao.update.Updater

enum class Tab { HOME, SEARCH, LIBRARY, SETTINGS }

data class NavState(
    val tab: Tab = Tab.HOME,
    val playerExpanded: Boolean = false,
    val fullscreen: Boolean = false,
    val pip: Boolean = false,
    /** bumped to ask the search field for focus */
    val focusSearch: Int = 0,
)

data class FeedState(
    val items: List<Video> = emptyList(),
    val next: String? = null,
    val loading: Boolean = false,
    val error: Int? = null,
    val loaded: Boolean = false,
    val suggestion: String? = null,
)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        const val FOR_YOU = "for_you"
    }

    private val app: Context get() = getApplication()

    val nav = MutableStateFlow(NavState())

    private val _kiosks = MutableStateFlow<List<Kiosk>>(emptyList())
    val kiosks: StateFlow<List<Kiosk>> = _kiosks.asStateFlow()

    private val _selected = MutableStateFlow(FOR_YOU)
    val selected: StateFlow<String> = _selected.asStateFlow()

    private val _feeds = MutableStateFlow<Map<String, FeedState>>(emptyMap())
    val feeds: StateFlow<Map<String, FeedState>> = _feeds.asStateFlow()

    /** kiosk used for "Dành cho bạn" while there is no watch history yet */
    private var forYouFallback: String? = null
    private val feedJobs = HashMap<String, Job>()

    // search
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()
    private val _suggestions = MutableStateFlow<List<String>>(emptyList())
    val suggestions: StateFlow<List<String>> = _suggestions.asStateFlow()
    private val _results = MutableStateFlow(FeedState())
    val results: StateFlow<FeedState> = _results.asStateFlow()
    private val _submitted = MutableStateFlow<String?>(null)
    val submitted: StateFlow<String?> = _submitted.asStateFlow()
    private val _recent = MutableStateFlow(loadRecent())
    val recent: StateFlow<List<String>> = _recent.asStateFlow()
    private var suggestJob: Job? = null
    private var searchJob: Job? = null

    init {
        loadKiosks()
        load(FOR_YOU)
    }

    fun go(tab: Tab) = nav.update { it.copy(tab = tab, playerExpanded = false) }

    fun openSearch() = nav.update {
        it.copy(tab = Tab.SEARCH, playerExpanded = false, focusSearch = it.focusSearch + 1)
    }

    // ------------------------------------------------------------------ home

    private fun loadKiosks() {
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) {
                runCatching { EngineManager.get().kiosks() }.getOrDefault(emptyList())
            }
            _kiosks.value = list
            if (list.isEmpty()) {
                delay(15_000)
                loadKiosks()
            }
        }
    }

    fun select(id: String) {
        _selected.value = id
        val f = _feeds.value[id]
        if (f == null || (!f.loaded && !f.loading)) load(id)
    }

    fun refresh() {
        val id = _selected.value
        feedJobs[id]?.cancel()
        _feeds.update { it - id }
        if (_kiosks.value.isEmpty()) loadKiosks()
        load(id)
    }

    fun loadMore() {
        val id = _selected.value
        val f = _feeds.value[id] ?: return
        if (f.loading || f.next == null) return
        load(id, more = true)
    }

    private fun setFeed(id: String, block: (FeedState) -> FeedState) =
        _feeds.update { it + (id to block(it[id] ?: FeedState())) }

    private fun load(id: String, more: Boolean = false) {
        if (feedJobs[id]?.isActive == true) return
        setFeed(id) { it.copy(loading = true, error = null) }
        feedJobs[id] = viewModelScope.launch {
            try {
                val token = if (more) _feeds.value[id]?.next else null
                val (items, next) = withContext(Dispatchers.IO) {
                    if (id == FOR_YOU) forYou(token) else {
                        val feed = EngineManager.get().kiosk(id, token)
                        feed.items.map { it.toVideo() } to feed.nextPageToken
                    }
                }
                setFeed(id) {
                    val merged = (if (more) it.items + items else items).distinctBy { v -> videoKey(v.url) }
                    it.copy(items = merged, next = next, loading = false, loaded = true)
                }
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                val kind = EngineManager.classify(t)
                setFeed(id) { it.copy(loading = false, error = kind, loaded = it.items.isNotEmpty()) }
                if (kind == ErrorKind.BROKEN || kind == ErrorKind.UNKNOWN) Updater.requestEngineCheck(app)
            }
        }
    }

    /** "Dành cho bạn": videos related to what was watched recently, else a popular category. */
    private suspend fun forYou(token: String?): Pair<List<Video>, String?> {
        val engine = EngineManager.get()
        if (token != null) {
            val k = forYouFallback ?: return emptyList<Video>() to null
            val feed = engine.kiosk(k, token)
            return feed.items.map { it.toVideo() } to feed.nextPageToken
        }
        val seeds = Library.history.value.take(4).map { it.video.url }
        if (seeds.isNotEmpty()) {
            val watched = Library.history.value.take(150).map { videoKey(it.video.url) }.toSet()
            val lists = coroutineScope {
                seeds.map { url ->
                    async(Dispatchers.IO) {
                        runCatching { engine.details(url).related.map { it.toVideo() } }
                            .getOrDefault(emptyList())
                    }
                }.awaitAll()
            }
            val mixed = ArrayList<Video>()
            val max = lists.maxOfOrNull { it.size } ?: 0
            for (i in 0 until max) for (l in lists) if (i < l.size) mixed.add(l[i])
            val result = mixed.filter { videoKey(it.url) !in watched && !it.live }
                .distinctBy { videoKey(it.url) }
            if (result.size >= 6) return result to null
        }
        val kiosks = _kiosks.value.ifEmpty { engine.kiosks() }
        val k = kiosks.firstOrNull()?.id ?: return emptyList<Video>() to null
        forYouFallback = k
        val feed = engine.kiosk(k, null)
        return feed.items.map { it.toVideo() } to feed.nextPageToken
    }

    // ------------------------------------------------------------------ search

    fun onQueryChange(q: String) {
        _query.value = q
        suggestJob?.cancel()
        if (q.isBlank()) {
            _suggestions.value = emptyList()
            return
        }
        suggestJob = viewModelScope.launch {
            delay(250)
            val list = withContext(Dispatchers.IO) {
                runCatching { EngineManager.get().suggestions(q) }.getOrDefault(emptyList())
            }
            _suggestions.value = list.distinct().take(8)
        }
    }

    fun clearSearch() {
        _query.value = ""
        _submitted.value = null
        _suggestions.value = emptyList()
        _results.value = FeedState()
    }

    /** @return a video to play directly when [q] is a YouTube link */
    fun submit(q: String): Video? {
        val text = q.trim()
        if (text.isEmpty()) return null
        if (videoKey(text) != text && text.contains("you")) {
            return Video(url = text, title = "", channel = "", thumbnail = null, duration = -1)
        }
        _query.value = text
        _submitted.value = text
        _suggestions.value = emptyList()
        remember(text)
        searchJob?.cancel()
        _results.value = FeedState(loading = true)
        searchJob = viewModelScope.launch { runSearch(text, null) }
        return null
    }

    fun loadMoreResults() {
        val r = _results.value
        val q = _submitted.value ?: return
        if (r.loading || r.next == null) return
        _results.update { it.copy(loading = true) }
        searchJob = viewModelScope.launch { runSearch(q, r.next) }
    }

    private suspend fun runSearch(q: String, token: String?) {
        try {
            val feed = withContext(Dispatchers.IO) { EngineManager.get().search(q, token) }
            val items = feed.items.map { it.toVideo() }
            _results.update {
                FeedState(
                    items = (if (token != null) it.items + items else items).distinctBy { v -> videoKey(v.url) },
                    next = feed.nextPageToken,
                    loaded = true,
                    suggestion = if (token == null) feed.suggestion else it.suggestion,
                )
            }
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            val kind = EngineManager.classify(t)
            _results.update { it.copy(loading = false, error = kind, loaded = true) }
            if (kind == ErrorKind.BROKEN || kind == ErrorKind.UNKNOWN) Updater.requestEngineCheck(app)
        }
    }

    private fun prefs() = app.getSharedPreferences("search", Context.MODE_PRIVATE)

    private fun loadRecent(): List<String> =
        prefs().getString("recent", "")!!.split('\n').filter { it.isNotBlank() }

    private fun remember(q: String) {
        val list = (listOf(q) + _recent.value.filterNot { it.equals(q, ignoreCase = true) }).take(12)
        _recent.value = list
        prefs().edit().putString("recent", list.joinToString("\n")).apply()
    }

    fun forgetRecent(q: String) {
        val list = _recent.value - q
        _recent.value = list
        prefs().edit().putString("recent", list.joinToString("\n")).apply()
    }
}
