package vn.lucbao.data

import vn.lucbao.api.VideoDetails
import vn.lucbao.api.VideoItem

/** App-side description of a video, used by lists, history and favourites. */
data class Video(
    val url: String,
    val title: String,
    val channel: String,
    val thumbnail: String?,
    /** seconds, -1 when unknown */
    val duration: Long,
    val views: Long = -1,
    val uploaded: String? = null,
    val live: Boolean = false,
    val channelUrl: String? = null,
    /** upload time in ms when known exactly (channel feeds), else 0 */
    val publishedAt: Long = 0,
    /** from a channel the user follows */
    val followed: Boolean = false,
)

fun VideoItem.toVideo(): Video {
    val published = Format.parseInstant(uploaded)
    return Video(
        url = url,
        title = title ?: "",
        channel = channel ?: "",
        thumbnail = thumbnail,
        duration = duration,
        views = views,
        uploaded = if (published > 0) Format.ago(published) else uploaded,
        live = live,
        channelUrl = channelUrl,
        publishedAt = published,
    )
}

fun VideoDetails.toVideo() = Video(
    url = url,
    title = title ?: "",
    channel = channel ?: "",
    thumbnail = thumbnail,
    duration = duration,
    views = views,
    uploaded = uploaded,
    live = live,
    channelUrl = channelUrl,
)

/** Stable key used to match the same video across different URL shapes. */
private val KEY_WATCH = Regex("[?&]v=([A-Za-z0-9_-]{11})")
private val KEY_SHORT = Regex("youtu\\.be/([A-Za-z0-9_-]{11})")
private val KEY_PATH = Regex("/(?:shorts|live|embed)/([A-Za-z0-9_-]{11})")

fun videoKey(url: String): String {
    val v = KEY_WATCH.find(url)?.groupValues?.get(1)
        ?: KEY_SHORT.find(url)?.groupValues?.get(1)
        ?: KEY_PATH.find(url)?.groupValues?.get(1)
    return v ?: url
}
