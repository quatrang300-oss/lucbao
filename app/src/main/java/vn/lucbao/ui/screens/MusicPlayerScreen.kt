package vn.lucbao.ui.screens

import android.content.res.Configuration

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import vn.lucbao.data.Format
import vn.lucbao.data.Library
import vn.lucbao.data.videoKey
import vn.lucbao.player.PlayerController
import vn.lucbao.player.PlayerUi
import vn.lucbao.player.QueueState
import vn.lucbao.ui.components.DownloadSheet
import vn.lucbao.ui.components.IconTap
import vn.lucbao.ui.components.PillButton
import vn.lucbao.ui.components.errorText
import vn.lucbao.ui.theme.Luc
import vn.lucbao.ui.theme.LucIcons

/** Full-screen player for songs played from the Music tab. */
@Composable
fun MusicPlayerScreen(ui: PlayerUi, onCollapse: () -> Unit) {
    val c = Luc.colors
    val queue by PlayerController.queue.collectAsStateWithLifecycle()
    val likedKeys by Library.likedKeys.collectAsStateWithLifecycle()
    val sleepAt by PlayerController.sleepAt.collectAsStateWithLifecycle()
    val v = ui.video ?: return
    val isLiked = remember(likedKeys, v.url) { videoKey(v.url) in likedKeys }
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    var showQueue by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    // Slow clock for the sleep-timer countdown label.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(sleepAt) {
        while (sleepAt > 0) {
            now = System.currentTimeMillis()
            delay(20_000)
        }
    }
    var showDownload by remember { mutableStateOf(false) }

    val cover: @Composable (Modifier) -> Unit = { mod ->
        Box(mod.clip(RoundedCornerShape(22.dp)).background(c.card), contentAlignment = Alignment.Center) {
            AsyncImage(
                model = ui.details?.thumbnail ?: v.thumbnail, contentDescription = null,
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
            )
            if (ui.loading) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = c.primary, strokeWidth = 3.dp, modifier = Modifier.size(42.dp))
                }
            }
            if (ui.error != null && !ui.loading) {
                Column(
                    Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.8f)).padding(20.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(errorText(ui.error), color = Color.White, fontSize = 13.sp, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PillButton("Thử lại", LucIcons.Refresh, { PlayerController.retry() }, filled = false)
                        if (queue.items.size > 1) PillButton("Bài sau", LucIcons.Next, { PlayerController.next() })
                    }
                }
            }
        }
    }

    val details: @Composable () -> Unit = {
        // Title + like
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    v.title.ifEmpty { "Đang tải…" }, color = c.text, fontSize = 20.sp,
                    fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 25.sp
                )
                Text(v.channel, color = c.muted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconTap(
                if (isLiked) LucIcons.Heart else LucIcons.HeartOutline, if (isLiked) "Bỏ thích" else "Thích",
                size = 26, tint = if (isLiked) c.primary else c.text
            ) { Library.toggleLikedSong(ui.video ?: v) }
        }
        SeekBar()
        // Controls
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconTap(LucIcons.Shuffle, "Trộn bài", size = 24, tint = if (queue.shuffle) c.primary else c.muted) {
                PlayerController.toggleShuffle()
            }
            IconTap(LucIcons.Prev, "Bài trước", size = 36, box = 52) { PlayerController.previous() }
            Box(
                Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(c.primary)
                    .clickable { PlayerController.togglePlay() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (ui.isPlaying) LucIcons.Pause else LucIcons.Play, if (ui.isPlaying) "Tạm dừng" else "Phát",
                    tint = c.onPrimary, modifier = Modifier.size(38.dp)
                )
            }
            IconTap(LucIcons.Next, "Bài sau", size = 36, box = 52) { PlayerController.next() }
            IconTap(
                if (queue.repeat == 2) LucIcons.RepeatOne else LucIcons.Repeat, "Lặp lại", size = 24,
                tint = if (queue.repeat != 0) c.primary else c.muted
            ) { PlayerController.cycleRepeat() }
        }
        Spacer(Modifier.height(14.dp))
        // Extras
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Extra(LucIcons.QueueMusic, "Hàng chờ") { showQueue = true }
            Extra(LucIcons.Radio, "Bài tương tự") { PlayerController.startRadio() }
            Extra(LucIcons.Timer, sleepLabel(sleepAt, now), on = sleepAt != 0L) { showSleep = true }
            if (ui.details != null) Extra(LucIcons.Download, "Tải về") { showDownload = true }
            Extra(LucIcons.Video, "Xem video") { PlayerController.watchVideo() }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(c.surface)
            .background(Brush.verticalGradient(listOf(c.primary.copy(alpha = 0.28f), Color.Transparent, Color.Transparent)))
    ) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top bar
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconTap(LucIcons.Down, "Thu gọn", size = 28, onClick = onCollapse)
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("ĐANG PHÁT TỪ", color = c.muted, fontSize = 10.sp, letterSpacing = 1.sp)
                    Text(
                        queue.source.ifBlank { "Nhạc" }, color = c.text, fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                IconTap(LucIcons.QueueMusic, "Hàng chờ", size = 24) { showQueue = true }
            }

            if (landscape) {
                // Side by side: cover on the left, everything else scrolls on the right.
                Row(Modifier.fillMaxSize().padding(top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    cover(Modifier.fillMaxHeight().aspectRatio(1f))
                    Spacer(Modifier.width(24.dp))
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { details() }
                }
            } else {
                Spacer(Modifier.height(18.dp))
                // As big as fits; shrinks on short screens so the controls stay visible.
                cover(
                    Modifier
                        .widthIn(max = 420.dp)
                        .weight(1f, fill = false)
                        .aspectRatio(1f, matchHeightConstraintsFirst = true)
                )
                Spacer(Modifier.height(22.dp))
                details()
            }
        }
    }

    if (showQueue) QueueSheet(queue) { showQueue = false }
    if (showSleep) SleepSheet(sleepAt) { showSleep = false }
    val d = ui.details
    if (showDownload && d != null) DownloadSheet(d, onDismiss = { showDownload = false })
}

/** Seek bar with its own clock, so only it redraws a few times per second. */
@Composable
private fun SeekBar() {
    val c = Luc.colors
    val exo = PlayerController.exo
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            position = exo.currentPosition
            duration = exo.duration.coerceAtLeast(0)
            delay(300)
        }
    }
    Column(Modifier.fillMaxWidth()) {
        val shown = if (dragging) (dragValue * duration).toLong() else position
        Slider(
            value = if (dragging) dragValue else if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f,
            onValueChange = { dragging = true; dragValue = it },
            onValueChangeFinished = {
                exo.seekTo((dragValue * duration).toLong())
                dragging = false
            },
            colors = SliderDefaults.colors(
                thumbColor = c.primary, activeTrackColor = c.primary,
                inactiveTrackColor = c.text.copy(alpha = 0.18f),
            ),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
        Row(Modifier.fillMaxWidth()) {
            Text(Format.millis(shown), color = c.muted, fontSize = 11.5.sp)
            Spacer(Modifier.weight(1f))
            Text(Format.millis(duration), color = c.muted, fontSize = 11.5.sp)
        }
    }
}

private fun sleepLabel(at: Long, now: Long = System.currentTimeMillis()): String = when {
    at == 0L -> "Hẹn giờ tắt"
    at < 0 -> "Tắt sau bài này"
    else -> {
        val min = ((at - now) / 60_000L + 1).coerceAtLeast(1)
        "Tắt sau $min phút"
    }
}

@Composable
private fun Extra(icon: ImageVector, text: String, on: Boolean = false, onClick: () -> Unit) {
    val c = Luc.colors
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (on) c.primary.copy(alpha = 0.2f) else c.card)
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = if (on) c.primary else c.text, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, color = if (on) c.primary else c.text, fontSize = 12.5.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueSheet(queue: QueueState, onDismiss: () -> Unit) {
    val c = Luc.colors
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val list = rememberLazyListState(initialFirstVisibleItemIndex = (queue.index - 1).coerceAtLeast(0))
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = c.card) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Hàng chờ", color = c.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    Format.dot(queue.source.ifBlank { null }, "${queue.items.size} bài"),
                    color = c.muted, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            if (queue.shuffle) Text("Đang trộn bài", color = c.primary, fontSize = 11.5.sp)
        }
        if (queue.items.isEmpty()) {
            Text(
                "Chưa có bài nào trong hàng chờ.", color = c.muted, fontSize = 13.sp,
                modifier = Modifier.padding(20.dp)
            )
        }
        LazyColumn(state = list, modifier = Modifier.fillMaxWidth().height(480.dp)) {
            itemsIndexed(queue.items, key = { i, s -> "q$i-${s.url}" }) { i, s ->
                val current = i == queue.index
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(if (current) c.primary.copy(alpha = 0.12f) else Color.Transparent)
                        .clickable { if (!current) PlayerController.jumpTo(i) }
                        .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Art(s.thumbnail, 46.dp, corner = 8.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            s.title, color = if (current) c.primary else c.text, fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            Format.dot(s.channel, Format.duration(s.duration)), color = c.muted, fontSize = 11.5.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (current) {
                        Icon(LucIcons.Music, null, tint = c.primary, modifier = Modifier.padding(end = 12.dp).size(18.dp))
                    } else {
                        IconTap(LucIcons.Close, "Bỏ khỏi hàng chờ", size = 18, tint = c.muted) {
                            PlayerController.removeFromQueue(i)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SleepSheet(sleepAt: Long, onDismiss: () -> Unit) {
    val c = Luc.colors
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = c.card) {
        Text(
            "Hẹn giờ tắt nhạc", color = c.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 4.dp)
        )
        if (sleepAt != 0L) {
            Text(sleepLabel(sleepAt), color = c.primary, fontSize = 12.5.sp, modifier = Modifier.padding(horizontal = 22.dp))
        }
        Spacer(Modifier.height(6.dp))
        listOf(
            0 to "Tắt hẹn giờ", 15 to "15 phút", 30 to "30 phút", 45 to "45 phút",
            60 to "1 giờ", 90 to "1 giờ 30 phút", -1 to "Hết bài đang phát",
        ).forEach { (min, label) ->
            Text(
                label, color = c.text, fontSize = 14.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        PlayerController.setSleepTimer(min)
                        onDismiss()
                    }
                    .padding(horizontal = 22.dp, vertical = 13.dp)
            )
        }
        Spacer(Modifier.height(20.dp))
    }
}
