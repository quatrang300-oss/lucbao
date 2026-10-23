package vn.lucbao.player

import android.util.Log
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
import org.json.JSONObject
import vn.lucbao.engine.EngineManager

/** One chapter set by the uploader. */
data class Chapter(val startMs: Long, val title: String)

/** One comment (read only). */
data class Comment(
    val author: String,
    val avatar: String?,
    val text: String,
    val likes: Int,
    val time: String,
    val replyCount: Int,
    /** token for [Comments.page] to load the replies; null when there are none */
    val replies: String?,
    val pinned: Boolean,
    val hearted: Boolean,
    val creator: Boolean,
)

data class CommentPage(
    val items: List<Comment>,
    val next: String?,
    /** -1 when unknown (only on the first page) */
    val count: Int = -1,
    val disabled: Boolean = false,
)

/** Calls an engine extra by name, so engines and apps of different ages keep working together. */
internal suspend fun engineCall(name: String, vararg args: String?): String? {
    val engine = withContext(Dispatchers.IO) { EngineManager.get() }
    val types = Array(args.size) { String::class.java }
    val method = runCatching { engine.javaClass.getMethod(name, *types) }.getOrNull()
        ?: throw UnsupportedOperationException("engine has no $name")
    return withContext(Dispatchers.IO) {
        try {
            method.invoke(engine, *args) as String?
        } catch (e: java.lang.reflect.InvocationTargetException) {
            throw e.targetException ?: e
        }
    }
}

/** Chapters of the video that is playing. */
object Chapters {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null
    private val _list = MutableStateFlow<List<Chapter>>(emptyList())
    val list: StateFlow<List<Chapter>> = _list.asStateFlow()

    fun load(url: String, live: Boolean) {
        job?.cancel()
        _list.value = emptyList()
        if (live) return
        job = scope.launch {
            try {
                val json = engineCall("chaptersJson", url) ?: return@launch
                val arr = JSONObject(json).optJSONArray("chapters") ?: return@launch
                val out = ArrayList<Chapter>(arr.length())
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    out.add(Chapter(o.optLong("start"), o.optString("title").trim()))
                }
                // A single "chapter" is no chapter list.
                _list.value = if (out.size >= 2) out.sortedBy { it.startMs } else emptyList()
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                Log.i("Chapters", "No chapters: ${t.message}")
            }
        }
    }

    fun clear() {
        job?.cancel()
        _list.value = emptyList()
    }

    /** Index of the chapter playing at [posMs], or -1. */
    fun indexAt(list: List<Chapter>, posMs: Long): Int = list.indexOfLast { it.startMs <= posMs }
}

/** Comments, page by page. */
object Comments {
    /** @param token null for the first page, a "next" token, or a comment's [Comment.replies]. */
    suspend fun page(url: String, token: String?): CommentPage {
        val json = engineCall("commentsJson", url, token) ?: return CommentPage(emptyList(), null)
        val o = JSONObject(json)
        val arr = o.optJSONArray("items")
        val items = ArrayList<Comment>(arr?.length() ?: 0)
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val c = arr.optJSONObject(i) ?: continue
                items.add(
                    Comment(
                        author = c.optString("author"),
                        avatar = c.optString("avatar").ifBlank { null },
                        text = c.optString("text"),
                        likes = c.optInt("likes"),
                        time = c.optString("time"),
                        replyCount = c.optInt("replyCount"),
                        replies = c.optString("replies").ifBlank { null },
                        pinned = c.optBoolean("pinned"),
                        hearted = c.optBoolean("hearted"),
                        creator = c.optBoolean("creator"),
                    )
                )
            }
        }
        return CommentPage(
            items = items,
            next = o.optString("next").ifBlank { null },
            count = if (o.has("count")) o.optInt("count", -1) else -1,
            disabled = o.optBoolean("disabled"),
        )
    }
}
