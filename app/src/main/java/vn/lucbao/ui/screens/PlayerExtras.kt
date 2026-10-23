package vn.lucbao.ui.screens

import vn.lucbao.data.videoKey
import vn.lucbao.data.Video
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import vn.lucbao.data.Format
import vn.lucbao.player.Chapter
import vn.lucbao.player.Comment
import vn.lucbao.player.Comments
import vn.lucbao.player.PlayerController
import vn.lucbao.player.SkipNotice
import vn.lucbao.ui.components.Avatar
import vn.lucbao.ui.theme.Luc
import vn.lucbao.ui.theme.LucIcons
import java.util.Locale
import kotlin.math.max

/** "1.5x" — with a dot, like the speeds. */
internal fun zoomText(z: Float): String = String.format(Locale.ROOT, "%.1fx", z)

/** Label for the sleep timer chip: "Hẹn giờ tắt", "Tắt sau 23 phút", "Tắt khi hết video". */
@Composable
internal fun videoSleepLabel(): Pair<String, Boolean> {
    val sleepAt by PlayerController.sleepAt.collectAsStateWithLifecycle()
    val now by produceState(System.currentTimeMillis(), sleepAt) {
        while (true) {
            value = System.currentTimeMillis()
            delay(20_000)
        }
    }
    return when {
        sleepAt == -1L -> "Tắt khi hết video" to true
        sleepAt > 0 -> "Tắt sau ${max(1, ((sleepAt - now + 59_999) / 60_000).toInt())} phút" to true
        else -> "Hẹn giờ tắt" to false
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SleepTimerSheet(onDismiss: () -> Unit) {
    val c = Luc.colors
    val sleepAt by PlayerController.sleepAt.collectAsStateWithLifecycle()
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = c.card) {
        Text(
            "Hẹn giờ tắt", color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 4.dp)
        )
        listOf(15 to "15 phút", 30 to "30 phút", 45 to "45 phút", 60 to "1 giờ", 90 to "1 giờ 30 phút", -1 to "Khi hết video này")
            .forEach { (m, label) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { PlayerController.setSleepTimer(m); onDismiss() }
                        .padding(horizontal = 22.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(LucIcons.Timer, null, tint = if (m == -1 && sleepAt == -1L) c.primary else c.muted, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(14.dp))
                    Text(label, color = c.text, fontSize = 14.sp)
                }
            }
        if (sleepAt != 0L) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { PlayerController.setSleepTimer(0); onDismiss() }
                    .padding(horizontal = 22.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(LucIcons.Close, null, tint = c.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(14.dp))
                Text("Tắt hẹn giờ", color = c.primary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Text(
            "Hết giờ thì video tạm dừng, kể cả khi đang nghe tắt màn hình.",
            color = c.muted, fontSize = 11.5.sp, modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp)
        )
        Spacer(Modifier.height(20.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChaptersSheet(chapters: List<Chapter>, current: Int, onPick: (Chapter) -> Unit, onDismiss: () -> Unit) {
    val c = Luc.colors
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val list = rememberLazyListState(initialFirstVisibleItemIndex = max(0, current - 2))
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = c.card) {
        Text(
            "Chương (${chapters.size})", color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 4.dp)
        )
        LazyColumn(state = list, modifier = Modifier.fillMaxWidth()) {
            itemsIndexed(chapters) { i, ch ->
                val on = i == current
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(if (on) c.primary.copy(alpha = 0.14f) else Color.Transparent)
                        .clickable { onPick(ch) }
                        .padding(horizontal = 22.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        Format.millis(ch.startMs), color = if (on) c.primary else c.muted, fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold, modifier = Modifier.width(58.dp)
                    )
                    Text(
                        ch.title.ifBlank { "Chương ${i + 1}" }, color = if (on) c.primary else c.text, fontSize = 14.sp,
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

/** "Đã bỏ qua đoạn tài trợ · Xem lại" on the video, a few seconds after a skip. */
@Composable
internal fun SkipNoticeChip(notice: SkipNotice, onRewatch: () -> Unit, modifier: Modifier = Modifier) {
    val c = Luc.colors
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.72f))
            .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(LucIcons.SkipAhead, null, tint = c.primary, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp))
        Text("Đã bỏ qua ${notice.label}", color = Color.White, fontSize = 12.sp)
        Spacer(Modifier.width(6.dp))
        Text(
            "Xem lại", color = c.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .clickable(onClick = onRewatch)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

// ---------------------------------------------------------------------- comments

private class CommentsState {
    val items = mutableStateListOf<Comment>()
    var next by mutableStateOf<String?>(null)
    var loading by mutableStateOf(false)
    var failed by mutableStateOf(false)
    var count by mutableStateOf(-1)
    var disabled by mutableStateOf(false)
    var started by mutableStateOf(false)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CommentsSheet(url: String, onPlayVideo: (Video) -> Unit, onDismiss: () -> Unit) {
    val c = Luc.colors
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val s = remember(url) { CommentsState() }
    val scope = rememberCoroutineScope()
    val list = rememberLazyListState()
    // Replies of a comment, by its replies token.
    val replies = remember(url) { mutableStateMapOf<String, List<Comment>>() }
    val repliesLoading = remember(url) { mutableStateMapOf<String, Boolean>() }

    fun load() {
        if (s.loading || (s.started && s.next == null)) return
        s.loading = true
        s.failed = false
        scope.launch {
            try {
                val page = Comments.page(url, if (s.started) s.next else null)
                if (!s.started) {
                    s.count = page.count
                    s.disabled = page.disabled
                }
                s.items.addAll(page.items)
                s.next = page.next
                s.started = true
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                s.failed = true
            } finally {
                s.loading = false
            }
        }
    }

    fun loadReplies(token: String) {
        if (repliesLoading[token] == true || replies.containsKey(token)) return
        repliesLoading[token] = true
        scope.launch {
            try {
                replies[token] = Comments.page(url, token).items
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                // tap again to retry
            } finally {
                repliesLoading[token] = false
            }
        }
    }

    LaunchedEffect(url) { load() }
    // Next page when the end of the list comes into view.
    LaunchedEffect(list) {
        snapshotFlow { list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { last -> if (last >= s.items.size - 4 && s.next != null) load() }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = c.card) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Bình luận", color = c.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            if (s.count > 0) {
                Spacer(Modifier.width(8.dp))
                Text(Format.count(s.count.toLong()), color = c.muted, fontSize = 13.sp)
            }
        }
        LazyColumn(state = list, modifier = Modifier.fillMaxWidth().fillMaxHeight(0.88f)) {
            if (s.disabled) {
                item { Hint("Video này đã tắt bình luận.") }
            } else if (s.started && s.items.isEmpty() && !s.loading) {
                item { Hint("Chưa có bình luận nào.") }
            }
            itemsIndexed(s.items, key = { i, cm -> "$i-${cm.author}-${cm.text.hashCode()}" }) { _, cm ->
                CommentRow(cm, onPlayVideo = onPlayVideo)
                val token = cm.replies
                if (token != null && cm.replyCount > 0) {
                    val loaded = replies[token]
                    if (loaded == null) {
                        Row(
                            Modifier
                                .padding(start = 62.dp, bottom = 6.dp)
                                .clip(RoundedCornerShape(50))
                                .clickable { loadReplies(token) }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (repliesLoading[token] == true) {
                                CircularProgressIndicator(color = c.primary, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(8.dp))
                            }
                            Text("Xem ${cm.replyCount} phản hồi", color = c.primary, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                        }
                    } else {
                        Column(Modifier.padding(start = 46.dp)) { loaded.forEach { CommentRow(it, small = true, onPlayVideo = onPlayVideo) } }
                    }
                }
            }
            if (s.loading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = c.primary, strokeWidth = 3.dp, modifier = Modifier.size(26.dp))
                    }
                }
            }
            if (s.failed) {
                item {
                    Text(
                        "Chưa tải được bình luận. Chạm để thử lại.", color = c.primary, fontSize = 13.sp,
                        modifier = Modifier.fillMaxWidth().clickable { load() }.padding(20.dp)
                    )
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, color = Luc.colors.muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp))
}

@Composable
private fun CommentRow(cm: Comment, small: Boolean = false, onPlayVideo: (Video) -> Unit) {
    val c = Luc.colors
    var expanded by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Avatar(cm.author.removePrefix("@"), if (small) 26.dp else 34.dp, cm.avatar)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            if (cm.pinned) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(LucIcons.Pin, null, tint = c.muted, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Đã ghim", color = c.muted, fontSize = 11.sp)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                // The video's own creator: name on a green label, like YouTube.
                Text(
                    cm.author, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (cm.creator) c.onPrimary else c.text,
                    modifier = Modifier.weight(1f, fill = false).creatorBadge(cm.creator),
                )
                if (cm.time.isNotBlank()) {
                    Text("  ·  ${cm.time}", color = c.muted, fontSize = 11.sp, maxLines = 1)
                }
            }
            Text(
                rememberLinkedText(cm.text, onPlayVideo), color = c.text, fontSize = 13.5.sp, lineHeight = 19.sp,
                maxLines = if (expanded) Int.MAX_VALUE else 5, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(LucIcons.ThumbUp, null, tint = c.muted, modifier = Modifier.size(13.dp))
                if (cm.likes > 0) {
                    Spacer(Modifier.width(5.dp))
                    Text(Format.count(cm.likes.toLong()), color = c.muted, fontSize = 11.sp)
                }
                if (cm.hearted) {
                    Spacer(Modifier.width(12.dp))
                    Text("♥ tác giả đã thích", color = c.primary, fontSize = 11.sp)
                }
            }
        }
    }
}

/** A small rounded label, e.g. the creator's name. */
@Composable
internal fun Modifier.creatorBadge(on: Boolean): Modifier {
    val c = Luc.colors
    return if (on) this.clip(RoundedCornerShape(50)).background(c.primary).padding(horizontal = 6.dp)
    else this
}

@Composable
internal fun SectionRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, value: String, onClick: () -> Unit) {
    val c = Luc.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(c.surface2)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        Icon(icon, null, tint = c.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(title, color = c.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(8.dp))
        Text(
            value, color = c.muted, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text("›", color = c.muted, fontSize = 18.sp)
    }
}

// ---------------------------------------------------------------------- links in descriptions

private val URL_RE = Regex(
    "(?i)((?:https?://|www\\.)[^\\s<>\"]+|\\b[a-z0-9][a-z0-9-]*(?:\\.[a-z0-9-]+)*\\.[a-z]{2,}/[^\\s<>\"]*)"
)
private val TIME_RE = Regex("(?<![\\d:])(?:(\\d{1,2}):)?(\\d{1,2}):(\\d{2})(?![\\d:])")
private val YT_HOST = Regex("(?i)^https?://(?:www\\.|m\\.|music\\.)?(?:youtube\\.com|youtu\\.be)/")

/** A YouTube video link → a video Lục Bảo can play itself; anything else → null. */
internal fun youtubeVideoOf(href: String): Video? {
    if (!YT_HOST.containsMatchIn(href)) return null
    val id = videoKey(href).takeIf { it.length == 11 && it != href } ?: return null
    return Video(
        url = "https://www.youtube.com/watch?v=$id", title = "", channel = "",
        thumbnail = "https://i.ytimg.com/vi/$id/hqdefault.jpg", duration = -1,
    )
}

/**
 * Description / comment text with tappable parts: web links open in the browser (or the shop's
 * app, e.g. Shopee), YouTube video links play in Lục Bảo, and times like "12:34" jump there.
 */
@Composable
internal fun rememberLinkedText(text: String, onPlayVideo: (Video) -> Unit): AnnotatedString {
    val c = Luc.colors
    val uri = LocalUriHandler.current
    val play by rememberUpdatedState(onPlayVideo)
    return remember(text, c.primary) {
        val linkStyle = TextLinkStyles(SpanStyle(color = c.primary))
        data class Hit(val start: Int, val end: Int, val annotation: LinkAnnotation)
        val hits = ArrayList<Hit>()
        URL_RE.findAll(text).forEach { m ->
            var raw = m.value
            // A link at the end of a sentence: the dot / bracket is not part of it.
            while (raw.isNotEmpty() && raw.last() in ".,;:!?)]}\"'…" &&
                !(raw.last() == ')' && raw.count { it == '(' } >= raw.count { it == ')' })
            ) raw = raw.dropLast(1)
            if (raw.length < 4) return@forEach
            val href = if (raw.startsWith("http", ignoreCase = true)) raw else "https://$raw"
            val annotation = LinkAnnotation.Clickable("link", linkStyle) {
                val v = youtubeVideoOf(href)
                if (v != null) play(v) else runCatching { uri.openUri(href) }
            }
            hits.add(Hit(m.range.first, m.range.first + raw.length, annotation))
        }
        TIME_RE.findAll(text).forEach { m ->
            if (hits.any { m.range.first < it.end && m.range.last >= it.start }) return@forEach
            val h = m.groupValues[1].toIntOrNull() ?: 0
            val min = m.groupValues[2].toInt()
            val sec = m.groupValues[3].toInt()
            if (sec > 59 || (h > 0 && min > 59)) return@forEach
            val ms = ((h * 3600L) + min * 60L + sec) * 1000L
            val annotation = LinkAnnotation.Clickable("time", linkStyle) {
                val d = PlayerController.exo.duration
                if (d <= 0 || ms < d) PlayerController.exo.seekTo(ms)
            }
            hits.add(Hit(m.range.first, m.range.last + 1, annotation))
        }
        hits.sortBy { it.start }
        buildAnnotatedString {
            var at = 0
            for (hit in hits) {
                if (hit.start < at) continue
                append(text.substring(at, hit.start))
                withLink(hit.annotation) { append(text.substring(hit.start, hit.end)) }
                at = hit.end
            }
            if (at < text.length) append(text.substring(at))
        }
    }
}
