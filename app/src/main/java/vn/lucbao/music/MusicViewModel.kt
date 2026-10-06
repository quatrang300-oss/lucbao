package vn.lucbao.music

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vn.lucbao.data.videoKey
import vn.lucbao.engine.EngineManager
import vn.lucbao.ui.components.errorText

data class MusicList(
    val items: List<MusicItem> = emptyList(),
    val next: String? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val loaded: Boolean = false,
)

/** An album, playlist, artist or local list opened in the Music tab. */
data class MusicDetail(
    /** album, playlist, artist, liked, recent */
    val kind: String,
    val title: String,
    val subtitle: String = "",
    val thumb: String? = null,
    val url: String? = null,
    val list: MusicList = MusicList(loading = true),
    val id: Long = nextDetailId++,
)

private var nextDetailId = 1L

class MusicViewModel : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _submitted = MutableStateFlow<String?>(null)
    val submitted: StateFlow<String?> = _submitted.asStateFlow()

    private val _filter = MutableStateFlow(MusicRepo.SONGS)
    val filter: StateFlow<String> = _filter.asStateFlow()

    private val _results = MutableStateFlow(MusicList())
    val results: StateFlow<MusicList> = _results.asStateFlow()

    private val _suggestions = MutableStateFlow<List<String>>(emptyList())
    val suggestions: StateFlow<List<String>> = _suggestions.asStateFlow()

    private val _trending = MutableStateFlow(MusicList())
    val trending: StateFlow<MusicList> = _trending.asStateFlow()

    private val _detail = MutableStateFlow<MusicDetail?>(null)
    val detail: StateFlow<MusicDetail?> = _detail.asStateFlow()

    private var searchJob: Job? = null
    private var suggestJob: Job? = null
    /** Loading jobs of the open pages, by page id. */
    private val jobs = HashMap<Long, Job>()

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
            _suggestions.value = withContext(Dispatchers.IO) {
                runCatching { EngineManager.get().suggestions(q) }.getOrDefault(emptyList())
            }.distinct().take(8)
        }
    }

    fun submit(q: String) {
        val text = q.trim()
        if (text.isEmpty()) return
        suggestJob?.cancel()
        _suggestions.value = emptyList()
        _query.value = text
        _submitted.value = text
        runSearch(reset = true)
    }

    fun setFilter(f: String) {
        if (_filter.value == f) return
        _filter.value = f
        if (_submitted.value != null) runSearch(reset = true)
    }

    fun clearSearch() {
        searchJob?.cancel()
        _query.value = ""
        _submitted.value = null
        _suggestions.value = emptyList()
        _results.value = MusicList()
    }

    fun loadMoreResults() {
        val r = _results.value
        if (r.loading || r.next == null || _submitted.value == null) return
        runSearch(reset = false)
    }

    private fun runSearch(reset: Boolean) {
        val q = _submitted.value ?: return
        val f = _filter.value
        val token = if (reset) null else _results.value.next
        searchJob?.cancel()
        _results.update { if (reset) MusicList(loading = true) else it.copy(loading = true, error = null) }
        searchJob = viewModelScope.launch {
            try {
                val page = MusicRepo.search(q, f, token)
                _results.update {
                    val merged = (if (reset) page.items else it.items + page.items).distinctBy { m -> m.url }
                    MusicList(merged, page.next, loading = false, loaded = true)
                }
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                _results.update { it.copy(loading = false, loaded = true, error = describe(t)) }
            }
        }
    }

    // ------------------------------------------------------------------ browse

    fun loadTrending(force: Boolean = false) {
        val t = _trending.value
        if (!force && (t.loading || t.loaded)) return
        _trending.value = MusicList(loading = true)
        viewModelScope.launch {
            try {
                val page = MusicRepo.trending()
                _trending.value = MusicList(page.items.distinctBy { videoKey(it.url) }, null, loaded = true)
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                _trending.value = MusicList(loaded = true, error = describe(t))
            }
        }
    }

    // ------------------------------------------------------------------ detail pages
    // Pages opened from other pages (an artist from an album…) stack up; back returns.

    private val stack = ArrayList<MusicDetail>()

    private fun push(d: MusicDetail) {
        stack.add(d)
        _detail.value = d
    }

    /** Applies [block] to the page with [id] (it may no longer be on top). */
    private fun updateDetail(id: Long, block: (MusicDetail) -> MusicDetail) {
        val i = stack.indexOfFirst { it.id == id }
        if (i < 0) return
        stack[i] = block(stack[i])
        if (i == stack.lastIndex) _detail.value = stack[i]
    }

    fun openList(item: MusicItem) {
        if (item.type == "artist") {
            openArtist(item.title, item.artistUrl ?: item.url)
            return
        }
        open(MusicDetail(item.type, item.title, item.artist, item.thumb, item.url)) {
            MusicRepo.playlist(item.url, null)
        }
    }

    fun openSaved(url: String, title: String, artist: String, thumb: String?, type: String) {
        open(MusicDetail(type, title, artist, thumb, url)) { MusicRepo.playlist(url, null) }
    }

    fun openArtist(name: String, url: String) {
        open(MusicDetail("artist", name, "Nghệ sĩ", null, url)) { MusicRepo.artist(url) }
    }

    /** Local lists (liked / recent songs) are shown straight from the library. */
    fun openLocal(kind: String, title: String) {
        push(MusicDetail(kind, title, list = MusicList(loaded = true)))
    }

    /** Back: the previous page, or the Music home when this was the first one. */
    fun closeDetail() {
        if (stack.isNotEmpty()) {
            val top = stack.removeAt(stack.lastIndex)
            jobs.remove(top.id)?.cancel()
        }
        _detail.value = stack.lastOrNull()
    }

    fun loadMoreDetail() {
        val d = _detail.value ?: return
        val url = d.url ?: return
        val token = d.list.next ?: return
        if (d.list.loading) return
        updateDetail(d.id) { it.copy(list = it.list.copy(loading = true)) }
        jobs[d.id] = viewModelScope.launch {
            try {
                val page = MusicRepo.playlist(url, token)
                updateDetail(d.id) { cur ->
                    cur.copy(
                        list = MusicList(
                            (cur.list.items + page.items).distinctBy { it.url }, page.next, loaded = true
                        )
                    )
                }
            } catch (c: CancellationException) {
                updateDetail(d.id) { it.copy(list = it.list.copy(loading = false)) }
                throw c
            } catch (t: Throwable) {
                updateDetail(d.id) { it.copy(list = it.list.copy(loading = false, error = describe(t))) }
            }
        }
    }

    private fun open(start: MusicDetail, load: suspend () -> MusicPage) {
        push(start)
        jobs[start.id] = viewModelScope.launch {
            try {
                val page = load()
                updateDetail(start.id) { cur ->
                    cur.copy(
                        title = page.title ?: cur.title,
                        subtitle = page.uploader?.takeIf { cur.kind != "artist" } ?: cur.subtitle,
                        thumb = page.thumb ?: cur.thumb ?: page.items.firstOrNull()?.thumb,
                        list = MusicList(page.items.distinctBy { it.url }, page.next, loaded = true),
                    )
                }
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                updateDetail(start.id) { it.copy(list = MusicList(loaded = true, error = describe(t))) }
            }
        }
    }

    private fun describe(t: Throwable): String =
        if (t is MusicUnsupportedException) {
            "Bộ phát YouTube đang tự cập nhật để có mục Nhạc. Thử lại sau ít phút nhé."
        } else {
            errorText(EngineManager.classify(t))
        }
}
