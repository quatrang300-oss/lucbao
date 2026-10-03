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
)

fun VideoItem.toVideo() = Video(
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
fun videoKey(url: String): String {
    val v = Regex("[?&]v=([A-Za-z0-9_-]{11})").find(url)?.groupValues?.get(1)
        ?: Regex("youtu\\.be/([A-Za-z0-9_-]{11})").find(url)?.groupValues?.get(1)
        ?: Regex("/(?:shorts|live|embed)/([A-Za-z0-9_-]{11})").find(url)?.groupValues?.get(1)
    return v ?: url
}
