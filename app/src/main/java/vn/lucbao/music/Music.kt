package vn.lucbao.music

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import vn.lucbao.data.Video
import vn.lucbao.data.toVideo
import vn.lucbao.engine.EngineManager
import vn.lucbao.update.Updater

/** A song, music video, album, playlist or artist from YouTube Music. */
data class MusicItem(
    /** song, video, album, playlist or artist */
    val type: String,
    val url: String,
    val title: String,
    val artist: String,
    val artistUrl: String?,
    val thumb: String?,
    /** seconds, -1 when unknown */
    val duration: Long,
    /** views for songs, number of songs for albums/playlists */
    val count: Long,
) {
    val playable get() = type == "song" || type == "video"
    val isList get() = type == "album" || type == "playlist"

    fun toVideo() = Video(
        url = url, title = title, channel = artist, thumbnail = thumb,
        duration = duration, channelUrl = artistUrl,
    )
}

fun Video.toMusicItem() = MusicItem(
    type = "song", url = url, title = title, artist = channel, artistUrl = channelUrl,
    thumb = thumbnail, duration = duration, count = views,
)

class MusicPage(
    val items: List<MusicItem>,
    val next: String?,
    val title: String? = null,
    val uploader: String? = null,
    val thumb: String? = null,
    val count: Long = -1,
)

/** The installed YouTube engine is older than the music feature (it updates itself). */
class MusicUnsupportedException : Exception("Engine has no music support yet")

/**
 * YouTube Music through the engine. The music calls are found by name, so an older engine
 * (still being updated) simply reports [MusicUnsupportedException] instead of crashing.
 */
object MusicRepo {
    const val SONGS = "music_songs"
    const val VIDEOS = "music_videos"
    const val ALBUMS = "music_albums"
    const val PLAYLISTS = "music_playlists"

    suspend fun search(query: String, filter: String, token: String?): MusicPage =
        parse(call("musicSearchJson", query, filter, token))

    suspend fun playlist(url: String, token: String?): MusicPage =
        parse(call("playlistJson", url, token))

    /** YouTube's endless "radio" mix that starts with [video]. */
    suspend fun radio(video: Video): MusicPage {
        val id = Regex("[?&]v=([A-Za-z0-9_-]{11})").find(video.url)?.groupValues?.get(1)
            ?: Regex("youtu\\.be/([A-Za-z0-9_-]{11})").find(video.url)?.groupValues?.get(1)
            ?: throw IllegalArgumentException("No video id in ${video.url}")
        return playlist("https://www.youtube.com/watch?v=$id&list=RD$id", null)
    }

    /** Latest uploads of an artist's channel. */
    suspend fun artist(url: String): MusicPage = withContext(Dispatchers.IO) {
        val feed = EngineManager.get().kiosk("feed:$url", null)
        MusicPage(feed.items.map { it.toVideo().toMusicItem() }, null)
    }

    /** Popular music right now (YouTube's music chart). */
    suspend fun trending(): MusicPage = withContext(Dispatchers.IO) {
        val feed = EngineManager.get().kiosk("trending_music", null)
        MusicPage(feed.items.map { it.toVideo().toMusicItem() }, null)
    }

    private suspend fun call(name: String, vararg args: String?): String = withContext(Dispatchers.IO) {
        val engine = EngineManager.get()
        val types = Array(args.size) { String::class.java }
        val method = runCatching { engine.javaClass.getMethod(name, *types) }.getOrNull()
        if (method == null) {
            Updater.requestEngineCheck(vn.lucbao.player.PlayerController.appContext)
            throw MusicUnsupportedException()
        }
        try {
            method.invoke(engine, *args) as String? ?: "{}"
        } catch (e: java.lang.reflect.InvocationTargetException) {
            throw e.targetException ?: e
        }
    }

    private fun parse(json: String): MusicPage {
        val o = JSONObject(json)
        val arr = o.optJSONArray("items")
        val items = ArrayList<MusicItem>()
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val it = arr.optJSONObject(i) ?: continue
                val url = it.optString("url")
                if (url.isBlank()) continue
                items.add(
                    MusicItem(
                        type = it.optString("type", "song"),
                        url = url,
                        title = it.optString("title"),
                        artist = it.optString("artist"),
                        artistUrl = it.optString("artistUrl").ifBlank { null },
                        thumb = it.optString("thumb").ifBlank { null },
                        duration = it.optLong("duration", -1),
                        count = it.optLong("count", -1),
                    )
                )
            }
        }
        val next = if (o.isNull("next")) null else o.optString("next").ifBlank { null }
        return MusicPage(
            items = items,
            next = next,
            title = o.optString("title").ifBlank { null },
            uploader = o.optString("uploader").ifBlank { null },
            thumb = o.optString("thumb").ifBlank { null },
            count = o.optLong("count", -1),
        )
    }
}
