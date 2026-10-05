package vn.lucbao.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

data class HistoryEntry(
    val video: Video,
    /** last playback position */
    val positionMs: Long,
    val watchedAt: Long,
)

/** A channel followed on this phone (no account). */
data class Channel(
    val url: String,
    val name: String,
    val avatar: String?,
    val addedAt: Long,
)

data class FavoriteEntry(
    val video: Video,
    val addedAt: Long,
)

/** Watch history and favourites, stored privately on the phone (no account needed). */
object Library {
    private const val MAX_HISTORY = 500
    private lateinit var file: File
    private val io = Executors.newSingleThreadExecutor()

    private val _history = MutableStateFlow<List<HistoryEntry>>(emptyList())
    val history: StateFlow<List<HistoryEntry>> = _history.asStateFlow()

    private val _favorites = MutableStateFlow<List<FavoriteEntry>>(emptyList())
    val favorites: StateFlow<List<FavoriteEntry>> = _favorites.asStateFlow()

    private val _channels = MutableStateFlow<List<Channel>>(emptyList())
    val channels: StateFlow<List<Channel>> = _channels.asStateFlow()

    fun init(context: Context) {
        file = File(context.filesDir, "library.json")
        runCatching {
            if (file.exists()) {
                val root = JSONObject(file.readText())
                _history.value = root.optJSONArray("history").toList { o ->
                    HistoryEntry(o.getJSONObject("v").toVideo(), o.optLong("p"), o.optLong("t"))
                }
                _favorites.value = root.optJSONArray("favorites").toList { o ->
                    FavoriteEntry(o.getJSONObject("v").toVideo(), o.optLong("t"))
                }
                _channels.value = root.optJSONArray("channels").toList { o ->
                    Channel(
                        o.getString("url"), o.optString("name"),
                        o.optString("avatar").ifEmpty { null }, o.optLong("t")
                    )
                }
            }
        }
    }

    fun recordWatch(video: Video) {
        val key = videoKey(video.url)
        val old = _history.value.firstOrNull { videoKey(it.video.url) == key }
        val entry = HistoryEntry(video, old?.positionMs ?: 0L, System.currentTimeMillis())
        _history.value = (listOf(entry) + _history.value.filterNot { videoKey(it.video.url) == key })
            .take(MAX_HISTORY)
        save()
    }

    fun updatePosition(url: String, positionMs: Long) {
        val key = videoKey(url)
        var changed = false
        _history.value = _history.value.map {
            if (videoKey(it.video.url) == key) {
                changed = true
                it.copy(positionMs = positionMs)
            } else it
        }
        if (changed) save()
    }

    fun positionOf(url: String): Long {
        val key = videoKey(url)
        return _history.value.firstOrNull { videoKey(it.video.url) == key }?.positionMs ?: 0L
    }

    fun removeHistory(url: String) {
        val key = videoKey(url)
        _history.value = _history.value.filterNot { videoKey(it.video.url) == key }
        save()
    }

    fun clearHistory() {
        _history.value = emptyList()
        save()
    }

    fun isFavorite(url: String): Boolean {
        val key = videoKey(url)
        return _favorites.value.any { videoKey(it.video.url) == key }
    }

    fun toggleFavorite(video: Video) {
        val key = videoKey(video.url)
        _favorites.value = if (isFavorite(video.url)) {
            _favorites.value.filterNot { videoKey(it.video.url) == key }
        } else {
            listOf(FavoriteEntry(video, System.currentTimeMillis())) + _favorites.value
        }
        save()
    }

    private fun channelKey(url: String) = url.trimEnd('/').substringAfterLast('/')

    fun isFollowing(channelUrl: String?): Boolean {
        if (channelUrl.isNullOrBlank()) return false
        val key = channelKey(channelUrl)
        return _channels.value.any { channelKey(it.url) == key }
    }

    fun toggleFollow(channelUrl: String, name: String, avatar: String?) {
        val key = channelKey(channelUrl)
        _channels.value = if (isFollowing(channelUrl)) {
            _channels.value.filterNot { channelKey(it.url) == key }
        } else {
            listOf(Channel(channelUrl, name, avatar, System.currentTimeMillis())) + _channels.value
        }
        save()
    }

    private fun save() {
        val h = _history.value
        val f = _favorites.value
        val ch = _channels.value
        io.execute {
            runCatching {
                val root = JSONObject()
                root.put("history", JSONArray().apply {
                    h.forEach {
                        put(JSONObject().put("v", it.video.toJson()).put("p", it.positionMs)
                            .put("t", it.watchedAt))
                    }
                })
                root.put("favorites", JSONArray().apply {
                    f.forEach { put(JSONObject().put("v", it.video.toJson()).put("t", it.addedAt)) }
                })
                root.put("channels", JSONArray().apply {
                    ch.forEach {
                        put(JSONObject().put("url", it.url).put("name", it.name)
                            .put("avatar", it.avatar ?: "").put("t", it.addedAt))
                    }
                })
                val tmp = File(file.parentFile, "library.json.tmp")
                tmp.writeText(root.toString())
                tmp.renameTo(file)
            }
        }
    }

    private fun Video.toJson() = JSONObject()
        .put("url", url).put("title", title).put("channel", channel)
        .put("thumb", thumbnail ?: "").put("dur", duration).put("views", views)
        .put("up", uploaded ?: "").put("live", live).put("curl", channelUrl ?: "")

    private fun JSONObject.toVideo() = Video(
        url = getString("url"),
        title = optString("title"),
        channel = optString("channel"),
        thumbnail = optString("thumb").ifEmpty { null },
        duration = optLong("dur", -1),
        views = optLong("views", -1),
        uploaded = optString("up").ifEmpty { null },
        live = optBoolean("live"),
        channelUrl = optString("curl").ifEmpty { null },
    )

    private fun <T> JSONArray?.toList(map: (JSONObject) -> T): List<T> {
        if (this == null) return emptyList()
        val out = ArrayList<T>(length())
        for (i in 0 until length()) {
            runCatching { out.add(map(getJSONObject(i))) }
        }
        return out
    }
}
