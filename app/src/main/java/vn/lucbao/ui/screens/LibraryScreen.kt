package vn.lucbao.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vn.lucbao.data.Channel
import vn.lucbao.data.Format
import vn.lucbao.data.HistoryEntry
import vn.lucbao.data.Library
import vn.lucbao.data.Video
import vn.lucbao.ui.components.Avatar
import vn.lucbao.ui.components.ChannelSheet
import vn.lucbao.ui.components.EmptyState
import vn.lucbao.ui.components.FollowButton
import vn.lucbao.ui.components.IconTap
import vn.lucbao.ui.components.VideoCard
import vn.lucbao.ui.components.VideoRow
import vn.lucbao.ui.theme.Luc
import vn.lucbao.ui.theme.LucIcons
import java.util.Calendar

@Composable
fun LibraryScreen(onPlay: (Video) -> Unit) {
    val c = Luc.colors
    val history by Library.history.collectAsStateWithLifecycle()
    val favorites by Library.favorites.collectAsStateWithLifecycle()
    val channels by Library.channels.collectAsStateWithLifecycle()
    var openChannel by remember { mutableStateOf<Channel?>(null) }
    var tab by rememberSaveable { mutableStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize()) {
        item(key = "bar") {
            Row(
                Modifier
                    .statusBarsPadding()
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Thư viện", fontWeight = FontWeight.SemiBold, fontSize = 24.sp,
                    color = c.text, modifier = Modifier.weight(1f)
                )
                Box {
                    IconTap(LucIcons.More, "Tuỳ chọn") { menu = true }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Xoá toàn bộ lịch sử") },
                            onClick = { menu = false; confirmClear = true }
                        )
                    }
                }
            }
        }
        item(key = "tabs") {
            Row(
                Modifier
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(c.surface2)
                    .padding(4.dp)
            ) {
                listOf(LucIcons.History to "Lịch sử", LucIcons.Heart to "Yêu thích", LucIcons.PersonAdd to "Kênh").forEachIndexed { i, (icon, label) ->
                    val on = tab == i
                    Row(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (on) c.card else androidx.compose.ui.graphics.Color.Transparent)
                            .clickable { tab = i }
                            .padding(vertical = 9.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(icon, null, tint = if (on) c.primary else c.muted, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            label, fontSize = 13.sp, color = if (on) c.text else c.muted,
                            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }
        }
        item(key = "stats") {
            val weekAgo = System.currentTimeMillis() - 7L * 24 * 3600 * 1000
            val weekMs = history.filter { it.watchedAt > weekAgo }.sumOf { it.positionMs }
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (tab == 0) {
                    Stat(history.size.toString(), "video đã xem", Modifier.weight(1f))
                    Stat(Format.hours(weekMs), "xem tuần này", Modifier.weight(1f))
                } else if (tab == 2) {
                    Stat(channels.size.toString(), "kênh đang theo dõi", Modifier.weight(1f))
                    Stat("Riêng tư", "không cần tài khoản", Modifier.weight(1f))
                } else {
                    Stat(favorites.size.toString(), "video yêu thích", Modifier.weight(1f))
                    Stat("Riêng tư", "chỉ lưu trên máy", Modifier.weight(1f))
                }
            }
        }

        if (tab == 0) {
            if (history.isEmpty()) {
                item(key = "emptyH") {
                    EmptyState("Chưa xem video nào", "Video bạn xem sẽ hiện ở đây, mở lại là xem tiếp đúng chỗ đang dừng.")
                }
            }
            val groups = history.groupBy { dayLabel(it.watchedAt) }
            groups.forEach { (label, entries) ->
                item(key = "d-$label") {
                    Text(
                        label.uppercase(), color = c.muted, fontSize = 11.sp, letterSpacing = 0.8.sp,
                        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 6.dp)
                    )
                }
                itemsIndexed(entries, key = { i, e -> "h-$label-$i-${e.video.url}" }) { _, e ->
                    VideoRow(
                        e.video, onClick = { onPlay(e.video) }, progress = progressOf(e),
                        onLongClick = { Library.removeHistory(e.video.url) }
                    )
                }
            }
            if (history.isNotEmpty()) item(key = "tipH") { Tip("Nhấn giữ một video để xoá khỏi lịch sử.") }
        } else if (tab == 2) {
            if (channels.isEmpty()) {
                item(key = "emptyC") {
                    EmptyState(
                        "Chưa theo dõi kênh nào",
                        "Khi xem video, bấm “Theo dõi” cạnh tên kênh. Video mới của kênh sẽ luôn hiện đầu tiên ở Trang chủ."
                    )
                }
            }
            itemsIndexed(channels, key = { i, ch -> "c-$i-${ch.url}" }) { _, ch ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { openChannel = ch }
                        .padding(horizontal = 16.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Avatar(ch.name, 42.dp, ch.avatar)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        ch.name, color = c.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, modifier = Modifier.weight(1f)
                    )
                    FollowButton(ch.url, ch.name, ch.avatar)
                }
            }
            if (channels.isNotEmpty()) item(key = "tipC") { Tip("Chỉ lưu trên máy này. Xoá dữ liệu hoặc gỡ ứng dụng sẽ mất danh sách.") }
        } else {
            if (favorites.isEmpty()) {
                item(key = "emptyF") {
                    EmptyState("Chưa có video yêu thích", "Bấm ♥ Yêu thích khi đang xem để lưu video vào đây.")
                }
            }
            itemsIndexed(favorites, key = { i, f -> "f-$i-${f.video.url}" }) { _, f ->
                VideoCard(f.video, onClick = { onPlay(f.video) }, onLongClick = { Library.toggleFavorite(f.video) })
            }
            if (favorites.isNotEmpty()) item(key = "tipF") { Tip("Nhấn giữ một video để bỏ khỏi Yêu thích.") }
        }
        item(key = "pad") { Spacer(Modifier.height(16.dp)) }
    }

    openChannel?.let { ch ->
        ChannelSheet(ch.url, ch.name, ch.avatar, onDismiss = { openChannel = null }, onPlay = onPlay)
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Xoá lịch sử?") },
            text = { Text("Toàn bộ lịch sử xem trên máy này sẽ bị xoá.") },
            confirmButton = {
                TextButton(onClick = { Library.clearHistory(); confirmClear = false }) { Text("Xoá") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Huỷ") } },
            containerColor = c.card
        )
    }
}

private fun progressOf(e: HistoryEntry): Float? {
    val d = e.video.duration
    if (d <= 0 || e.positionMs <= 0) return null
    return (e.positionMs / 1000f / d).coerceIn(0f, 1f)
}

private fun dayLabel(t: Long): String {
    val cal = Calendar.getInstance()
    val today = cal.apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val day = 24L * 3600 * 1000
    return when {
        t >= today -> "Hôm nay"
        t >= today - day -> "Hôm qua"
        t >= today - 7 * day -> "Tuần này"
        else -> "Trước đó"
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    val c = Luc.colors
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.linearGradient(listOf(c.primary.copy(alpha = 0.2f), c.surface2)))
            .border(1.dp, c.line, RoundedCornerShape(14.dp))
            .padding(12.dp)
    ) {
        Text(value, fontWeight = FontWeight.SemiBold, fontSize = 21.sp, color = c.text)
        Text(label, color = c.muted, fontSize = 11.sp)
    }
}

@Composable
private fun Tip(text: String) {
    Text(
        text, color = Luc.colors.muted, fontSize = 11.sp,
        modifier = Modifier.fillMaxWidth().padding(16.dp)
    )
}
