@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package vn.lucbao.ui.screens

import android.content.Intent
import android.graphics.Color as AColor
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import kotlinx.coroutines.launch
import vn.lucbao.ui.components.Thumb
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import vn.lucbao.data.Format
import vn.lucbao.data.Prefs
import vn.lucbao.data.Video
import vn.lucbao.player.CaptionStatus
import vn.lucbao.player.PlayerController
import vn.lucbao.player.VoiceOver
import vn.lucbao.player.PlayerUi
import vn.lucbao.player.Quality
import vn.lucbao.player.QualityChoice
import vn.lucbao.ui.components.Avatar
import vn.lucbao.ui.components.ChannelSheet
import vn.lucbao.ui.components.DownloadSheet
import vn.lucbao.ui.components.FollowButton
import vn.lucbao.ui.components.IconTap
import vn.lucbao.ui.components.PillButton
import vn.lucbao.ui.components.SectionTitle
import vn.lucbao.ui.components.VideoRow
import vn.lucbao.ui.components.errorText
import vn.lucbao.ui.theme.Luc
import vn.lucbao.ui.theme.LucIcons

private val SPEEDS = listOf(1f, 1.25f, 1.5f, 2f, 0.75f)
private const val PANEL_HIDE_MS = 10_000L

private fun speedLabel(s: Float): String =
    (if (s == s.toInt().toFloat()) "${s.toInt()}.0" else s.toString()).replace('.', ',') + "×"

@Composable
fun PlayerScreen(
    ui: PlayerUi,
    fullscreen: Boolean,
    locked: Boolean,
    onLockChange: (Boolean) -> Unit,
    onCollapse: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onPip: () -> Unit,
    onPlay: (Video) -> Unit,
) {
    val c = Luc.colors
    var showQuality by remember { mutableStateOf(false) }
    var showVietnamese by remember { mutableStateOf(false) }
    var morePanel by remember { mutableStateOf(false) }
    // Kept outside the panel so reopening "Nhiều video hơn" shows the list where it was left.
    val panelList = rememberLazyListState()
    var panelTouch by remember { mutableLongStateOf(0L) }
    LaunchedEffect(fullscreen) { if (!fullscreen) morePanel = false }
    BackHandler(enabled = morePanel && !locked) { morePanel = false }
    // A different video has a different list: start that one from the top.
    LaunchedEffect(ui.video?.url) { panelList.scrollToItem(0) }
    // Hide the panel 10 s after the last touch / scroll; the video goes back to full screen.
    LaunchedEffect(morePanel, panelTouch, panelList.isScrollInProgress) {
        if (morePanel && !panelList.isScrollInProgress) {
            delay(PANEL_HIDE_MS)
            morePanel = false
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .background(if (fullscreen) Color.Black else c.surface)
        ) {
            if (!fullscreen) Box(Modifier.fillMaxWidth().background(Color.Black).statusBarsPadding())
            // One VideoArea call site so the video view is kept when entering/leaving fullscreen.
            Row(
                if (fullscreen) Modifier.fillMaxSize()
                else Modifier.fillMaxWidth().aspectRatio(16f / 9f)
            ) {
                VideoArea(
                    ui = ui,
                    fullscreen = fullscreen,
                    panelOpen = morePanel,
                    modifier = Modifier.weight(if (fullscreen && morePanel) 2f else 1f).fillMaxHeight(),
                    onCollapse = onCollapse,
                    onToggleFullscreen = onToggleFullscreen,
                    onQuality = { showQuality = true },
                    onLock = { onLockChange(true) },
                    onVietnamese = { showVietnamese = true },
                    onMore = {
                        panelTouch = System.currentTimeMillis()
                        morePanel = true
                    },
                )
                if (fullscreen && morePanel) {
                    RelatedPanel(
                        ui = ui,
                        state = panelList,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        onTouch = { panelTouch = System.currentTimeMillis() },
                        onClose = { morePanel = false },
                        onPlay = { v ->
                            // Picked: hide the list at once and play full screen.
                            morePanel = false
                            onPlay(v)
                        },
                    )
                }
            }
            if (!fullscreen) {
                Details(
                    ui, onPip = onPip, onPlay = onPlay, onQuality = { showQuality = true },
                    onVietnamese = { showVietnamese = true },
                )
            }
        }
        if (locked) LockOverlay(fullscreen = fullscreen, onUnlock = { onLockChange(false) })
    }

    if (showVietnamese && !locked) {
        VietnameseSheet(onDismiss = { showVietnamese = false })
    }

    if (showQuality && ui.choices.isNotEmpty() && !locked) {
        QualitySheet(ui, onDismiss = { showQuality = false }) {
            PlayerController.setQuality(it)
            showQuality = false
        }
    }
}

/**
 * Child lock: swallows every touch on the player. Unlocking needs the lock button to be
 * held for 1.5 seconds, which small children rarely do by accident.
 */
@Composable
private fun LockOverlay(fullscreen: Boolean, onUnlock: () -> Unit) {
    val c = Luc.colors
    val scope = rememberCoroutineScope()
    var hint by remember { mutableIntStateOf(1) }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(hint) {
        if (hint > 0) {
            delay(3000)
            if (progress.value == 0f) hint = 0
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent()
                        if (e.type == PointerEventType.Press) hint++
                        e.changes.forEach { it.consume() }
                    }
                }
            }
    ) {
        AnimatedVisibility(
            visible = hint > 0,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .then(if (fullscreen) Modifier else Modifier.statusBarsPadding())
                .padding(10.dp)
        ) {
            Column(horizontalAlignment = Alignment.End) {
                Box(
                    Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f))
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                val job = scope.launch {
                                    progress.animateTo(1f, tween(1500))
                                    onUnlock()
                                }
                                do {
                                    val ev = awaitPointerEvent()
                                    ev.changes.forEach { it.consume() }
                                } while (ev.changes.any { it.pressed })
                                if (progress.value < 1f) {
                                    job.cancel()
                                    scope.launch { progress.snapTo(0f) }
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        progress = { progress.value },
                        color = c.primary,
                        trackColor = Color.Transparent,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(52.dp)
                    )
                    Icon(LucIcons.Lock, "Mở khoá", tint = Color.White, modifier = Modifier.size(26.dp))
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Nhấn giữ để mở khoá",
                    color = Color.White, fontSize = 11.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

/** Right-hand list shown next to the video in fullscreen ("Nhiều video hơn"). */
@Composable
private fun RelatedPanel(
    ui: PlayerUi,
    state: LazyListState,
    modifier: Modifier,
    onTouch: () -> Unit,
    onClose: () -> Unit,
    onPlay: (Video) -> Unit,
) {
    Column(
        modifier
            .background(Color(0xF2071A13))
            // Any touch on the panel restarts the 10-second auto-hide (nothing is consumed).
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial)
                        onTouch()
                    }
                }
            }
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Video khác", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            IconTap(LucIcons.Close, "Đóng", size = 22, tint = Color.White, onClick = onClose)
        }
        LazyColumn(Modifier.fillMaxSize(), state = state) {
            itemsIndexed(ui.related, key = { i, r -> "p$i-${r.url}" }) { _, r ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onPlay(r) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Thumb(r.thumbnail, Modifier.width(112.dp), r.duration, r.live, corner = 8.dp)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            r.title, color = Color.White, fontSize = 12.sp, lineHeight = 16.sp,
                            fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            r.channel, color = Color.White.copy(alpha = 0.65f), fontSize = 10.5.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            item(key = "pad") { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
fun VideoSurface(modifier: Modifier = Modifier) {
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
                setShutterBackgroundColor(AColor.BLACK)
                setBackgroundColor(AColor.BLACK)
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                keepScreenOn = true
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
                )
                player = PlayerController.exo
            }
        },
        onRelease = { it.player = null },
        modifier = modifier
    )
}

@Composable
private fun VideoArea(
    ui: PlayerUi,
    fullscreen: Boolean,
    panelOpen: Boolean,
    modifier: Modifier,
    onCollapse: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onQuality: () -> Unit,
    onLock: () -> Unit,
    onVietnamese: () -> Unit,
    onMore: () -> Unit,
) {
    val c = Luc.colors
    val exo = PlayerController.exo
    val settings by Prefs.state.collectAsStateWithLifecycle()
    val caption by PlayerController.caption.collectAsStateWithLifecycle()
    var controls by remember { mutableStateOf(true) }
    var touch by remember { mutableIntStateOf(0) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var buffered by remember { mutableLongStateOf(0L) }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        while (true) {
            position = exo.currentPosition
            duration = exo.duration.coerceAtLeast(0)
            buffered = exo.bufferedPosition
            delay(250)
        }
    }
    // Hide the controls a few seconds after the last touch while playing.
    LaunchedEffect(controls, touch, ui.isPlaying) {
        if (controls && ui.isPlaying) {
            delay(3500)
            controls = false
        }
    }

    Box(
        modifier
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { controls = !controls; touch++ },
                    onDoubleTap = { o ->
                        if (o.x < size.width / 2) exo.seekBack() else exo.seekForward()
                        controls = true
                        touch++
                    }
                )
            }
    ) {
        if (ui.quality?.audioOnly == true) {
            AsyncImage(
                model = ui.video?.thumbnail, contentDescription = null,
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
            )
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
            Row(
                Modifier.align(Alignment.Center),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(LucIcons.Headphones, null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Đang phát chỉ âm thanh", color = Color.White, fontSize = 13.sp)
            }
        } else {
            VideoSurface(Modifier.fillMaxSize())
        }

        if (ui.loading) {
            CircularProgressIndicator(
                color = c.primary, strokeWidth = 3.dp,
                modifier = Modifier.align(Alignment.Center).size(42.dp)
            )
        }

        if (ui.error != null && !ui.loading) {
            Column(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.82f))
                    .padding(20.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(errorText(ui.error), color = Color.White, fontSize = 13.sp, textAlign = TextAlign.Center, lineHeight = 19.sp)
                Spacer(Modifier.height(12.dp))
                PillButton("Thử lại", LucIcons.Refresh, { PlayerController.retry() })
            }
        }

        // Vietnamese subtitles, moved up while the controls are showing.
        val line = caption
        if (PlayerController.showsText(settings.vietnamese) && !line.isNullOrBlank() && ui.error == null) {
            val controlsShown = controls || !ui.isPlaying
            Text(
                line,
                color = Color.White,
                fontSize = if (fullscreen) 19.sp else 13.sp,
                lineHeight = if (fullscreen) 25.sp else 17.sp,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = if (fullscreen) 48.dp else 14.dp)
                    .padding(
                        bottom = when {
                            controlsShown && fullscreen -> 112.dp
                            controlsShown -> 50.dp
                            fullscreen -> 26.dp
                            else -> 8.dp
                        }
                    )
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.62f))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }

        AnimatedVisibility(
            visible = (controls || !ui.isPlaying) && ui.error == null,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f))) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                        .align(Alignment.TopCenter),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconTap(
                        if (fullscreen) LucIcons.Back else LucIcons.Down, "Thu gọn",
                        tint = Color.White
                    ) { if (fullscreen) onToggleFullscreen() else onCollapse() }
                    if (fullscreen) {
                        Text(
                            ui.video?.title ?: "", color = Color.White, fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    ui.quality?.let { q ->
                        Text(
                            if (q.audioOnly) "Âm thanh" else q.label,
                            color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(5.dp))
                                .background(Color(0x333DDC97))
                                .clickable(onClick = onQuality)
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    IconTap(
                        LucIcons.Subtitles, "Dịch tiếng Việt", size = 22,
                        tint = if (settings.vietnamese > 0) c.primary else Color.White
                    ) { onVietnamese(); touch++ }
                    IconTap(LucIcons.Lock, "Khoá màn hình", size = 22, tint = Color.White) {
                        controls = false
                        onLock()
                    }
                }

                Row(
                    Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(26.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconTap(LucIcons.Prev, "Trước", size = 28, tint = Color.White) {
                        PlayerController.previous(); touch++
                    }
                    Box(
                        Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.18f))
                            .clickable { PlayerController.togglePlay(); touch++ },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (ui.isPlaying) LucIcons.Pause else LucIcons.Play, "Phát",
                            tint = Color.White, modifier = Modifier.size(32.dp)
                        )
                    }
                    IconTap(LucIcons.Next, "Tiếp", size = 28, tint = Color.White) {
                        PlayerController.next(); touch++
                    }
                }

                val upNext = ui.related.firstOrNull()
                val showMore = fullscreen && !panelOpen && upNext != null
                Row(
                    Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(
                            start = 12.dp, end = 4.dp,
                            bottom = when {
                                showMore -> 58.dp
                                fullscreen -> 12.dp
                                else -> 0.dp
                            }
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val live = ui.video?.live == true || ui.details?.live == true
                    if (live) {
                        Text(
                            "● TRỰC TIẾP", color = Color(0xFFFF6B6B), fontSize = 11.sp,
                            fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)
                        )
                    } else {
                        val shown = if (dragging) (dragValue * duration).toLong() else position
                        Text(Format.millis(shown), color = Color.White, fontSize = 11.sp)
                        Slider(
                            value = if (dragging) dragValue
                            else if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f,
                            onValueChange = { dragging = true; dragValue = it; touch++ },
                            onValueChangeFinished = {
                                exo.seekTo((dragValue * duration).toLong())
                                dragging = false
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = c.primary,
                                activeTrackColor = c.primary,
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                            ),
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                        )
                        Text(Format.millis(duration), color = Color.White, fontSize = 11.sp)
                    }
                    IconTap(
                        if (fullscreen) LucIcons.FullscreenExit else LucIcons.Fullscreen,
                        "Toàn màn hình", size = 33, tint = Color.White, box = 52,
                        onClick = onToggleFullscreen
                    )
                }

                if (showMore && upNext != null) {
                    // YouTube-style: "Nhiều video hơn" + thumbnail, under the seek bar on the right.
                    Row(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 16.dp, bottom = 10.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onMore() }
                            .padding(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Nhiều video hơn", color = Color.White, fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.width(10.dp))
                        Thumb(upNext.thumbnail, Modifier.width(76.dp), corner = 6.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun Details(
    ui: PlayerUi,
    onPip: () -> Unit,
    onPlay: (Video) -> Unit,
    onQuality: () -> Unit,
    onVietnamese: () -> Unit,
) {
    val c = Luc.colors
    val context = LocalContext.current
    val d = ui.details
    val v = ui.video ?: return
    val settings by Prefs.state.collectAsStateWithLifecycle()
    var expanded by remember(v.url) { mutableStateOf(false) }
    var channelSheet by remember { mutableStateOf(false) }
    var downloadSheet by remember { mutableStateOf(false) }
    val channelUrl = d?.channelUrl ?: v.channelUrl

    LazyColumn(Modifier.fillMaxSize()) {
        item(key = "info") {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)
            ) {
                Text(
                    v.title.ifEmpty { "Đang tải…" }, color = c.text, fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold, lineHeight = 21.sp,
                    maxLines = if (expanded) 6 else 2, overflow = TextOverflow.Ellipsis
                )
                Text(
                    Format.dot(Format.views(d?.views ?: v.views), d?.uploaded ?: v.uploaded) +
                        if (expanded) "" else "  …thêm",
                    color = c.muted, fontSize = 11.5.sp, modifier = Modifier.padding(top = 5.dp)
                )
                if (expanded && !d?.description.isNullOrBlank()) {
                    Text(
                        d?.description ?: "", color = c.text, fontSize = 12.5.sp, lineHeight = 18.sp,
                        modifier = Modifier
                            .padding(top = 10.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(c.surface2)
                            .padding(12.dp)
                    )
                }
            }
        }
        item(key = "channel") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(enabled = channelUrl != null) { channelSheet = true }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Avatar(v.channel, 36.dp, d?.channelAvatar)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(v.channel, color = c.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    val subs = d?.subscribers ?: -1
                    if (subs > 0) {
                        Text("${Format.count(subs)} người theo dõi", color = c.muted, fontSize = 11.sp)
                    }
                }
                if (channelUrl != null && v.channel.isNotBlank()) {
                    FollowButton(channelUrl, v.channel, d?.channelAvatar)
                }
            }
        }
        item(key = "actions") {
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                ActionChip(LucIcons.Heart, if (ui.favorite) "Đã thích" else "Yêu thích", ui.favorite) {
                    PlayerController.toggleFavorite()
                }
                val audioOnly = ui.quality?.audioOnly == true
                if (ui.choices.any { it.audioOnly }) {
                    ActionChip(LucIcons.Headphones, "Chỉ nghe", audioOnly) {
                        val target = if (audioOnly) {
                            Quality.pick(ui.choices, Prefs.current.qualityWifi)
                        } else ui.choices.firstOrNull { it.audioOnly }
                        target?.let { PlayerController.setQuality(it) }
                    }
                }
                if (!audioOnly) ActionChip(LucIcons.Pip, "Thu nhỏ", false, onPip)
                ActionChip(
                    LucIcons.Subtitles,
                    when (settings.vietnamese) {
                        1 -> "Phụ đề Việt"
                        2 -> "Phụ đề + Thuyết minh"
                        3 -> "Thuyết minh"
                        else -> "Dịch tiếng Việt"
                    },
                    settings.vietnamese > 0,
                    onVietnamese
                )
                ActionChip(LucIcons.Speed, speedLabel(ui.speed), ui.speed != 1f) {
                    val i = SPEEDS.indexOf(ui.speed)
                    PlayerController.setSpeed(SPEEDS[(i + 1) % SPEEDS.size])
                }
                if (ui.choices.isNotEmpty()) {
                    ActionChip(LucIcons.Tune, ui.quality?.label ?: "Chất lượng", false, onQuality)
                }
                if (d != null && !d.live) {
                    ActionChip(LucIcons.Download, "Tải về", false) { downloadSheet = true }
                }
                ActionChip(LucIcons.Share, "Chia sẻ", false) {
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT, v.url)
                    context.startActivity(Intent.createChooser(send, "Chia sẻ video"))
                }
            }
        }
        item(key = "nextTitle") {
            val loop by PlayerController.loopVideo.collectAsStateWithLifecycle()
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Tiếp theo", fontWeight = FontWeight.SemiBold, fontSize = 17.sp,
                    color = c.text, modifier = Modifier.weight(1f)
                )
                // Lặp lại: replay this video when it ends (wins over autoplay).
                SmallToggle(LucIcons.RepeatOne, "Lặp lại", loop) { PlayerController.setLoopVideo(!loop) }
                Spacer(Modifier.width(6.dp))
                SmallToggle(
                    LucIcons.Play, if (settings.autoplayNext) "Tự phát: Bật" else "Tự phát: Tắt",
                    settings.autoplayNext && !loop
                ) { Prefs.update { it.copy(autoplayNext = !it.autoplayNext) } }
            }
            if (loop) {
                Text(
                    "Video này sẽ tự phát lại khi hết.", color = c.muted, fontSize = 11.5.sp,
                    modifier = Modifier.padding(start = 16.dp, bottom = 4.dp)
                )
            }
        }
        if (d == null && ui.loading) {
            item(key = "loading") { vn.lucbao.ui.components.LoadingBox() }
        }
        itemsIndexed(ui.related, key = { i, r -> "$i-${r.url}" }) { _, r ->
            VideoRow(r, onClick = { onPlay(r) })
        }
        item(key = "pad") { Spacer(Modifier.height(24.dp)) }
    }

    if (downloadSheet && d != null) {
        DownloadSheet(d, onDismiss = { downloadSheet = false })
    }

    if (channelSheet && channelUrl != null) {
        ChannelSheet(
            url = channelUrl, name = v.channel, avatar = d?.channelAvatar,
            onDismiss = { channelSheet = false }, onPlay = onPlay
        )
    }
}

@Composable
private fun SmallToggle(icon: ImageVector, text: String, on: Boolean, onClick: () -> Unit) {
    val c = Luc.colors
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (on) c.primary.copy(alpha = 0.18f) else Color.Transparent)
            .border(1.dp, if (on) c.primary.copy(alpha = 0.6f) else c.line, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = if (on) c.primary else c.muted, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(text, color = if (on) c.primary else c.muted, fontSize = 11.5.sp)
    }
}

@Composable
private fun ActionChip(icon: ImageVector, text: String, on: Boolean, onClick: () -> Unit) {
    val c = Luc.colors
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(c.surface2)
            .border(1.dp, if (on) c.primary.copy(alpha = 0.5f) else c.line, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = if (on) c.primary else c.text, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, color = if (on) c.primary else c.text, fontSize = 12.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QualitySheet(ui: PlayerUi, onDismiss: () -> Unit, onPick: (QualityChoice) -> Unit) {
    val c = Luc.colors
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = c.card) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Chất lượng video", color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Text("Áp dụng cho cả các video sau", color = c.muted, fontSize = 11.sp)
        }
        Spacer(Modifier.height(6.dp))
        ui.choices.forEach { q ->
            val on = q.label == ui.quality?.label
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onPick(q) }
                    .padding(horizontal = 22.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .border(2.dp, if (on) c.primary else c.muted, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (on) Box(Modifier.size(9.dp).clip(CircleShape).background(c.primary))
                }
                Spacer(Modifier.width(14.dp))
                Text(q.label, color = c.text, fontSize = 14.sp)
                q.badge?.let {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        it, color = c.gold, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(c.gold.copy(alpha = 0.2f))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
                Spacer(Modifier.weight(1f))
                if (q.audioOnly) Text("tiết kiệm dữ liệu", color = c.muted, fontSize = 11.sp)
            }
        }
        Spacer(Modifier.height(28.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VietnameseSheet(onDismiss: () -> Unit) {
    val c = Luc.colors
    val context = LocalContext.current
    val settings by Prefs.state.collectAsStateWithLifecycle()
    val info by PlayerController.captionInfo.collectAsStateWithLifecycle()
    val voice by VoiceOver.state.collectAsStateWithLifecycle()
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // Back from the voice settings (or just opened): check again for a Vietnamese voice.
    LifecycleResumeEffect(Unit) {
        val s = VoiceOver.state.value
        if (PlayerController.speaks(Prefs.current.vietnamese) &&
            (s == VoiceOver.State.NO_VOICE || s == VoiceOver.State.NO_ENGINE)
        ) {
            VoiceOver.init(context, force = true)
        }
        onPauseOrDispose { }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = c.card) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp)) {
            Text("Dịch sang tiếng Việt", color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "Dùng phụ đề của video, YouTube tự dịch nếu video không có sẵn tiếng Việt. Áp dụng cho cả các video sau.",
                color = c.muted, fontSize = 11.5.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 3.dp)
            )
        }
        Spacer(Modifier.height(6.dp))
        listOf(
            0 to "Tắt",
            1 to "Phụ đề tiếng Việt",
            2 to "Phụ đề + Thuyết minh (giọng đọc tiếng Việt)",
            3 to "Chỉ thuyết minh (không hiện chữ)",
        ).forEach { (mode, label) ->
            val on = settings.vietnamese == mode
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { PlayerController.setVietnamese(mode) }
                    .padding(horizontal = 22.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .border(2.dp, if (on) c.primary else c.muted, CircleShape)
                        .padding(4.dp)
                        .clip(CircleShape)
                        .background(if (on) c.primary else Color.Transparent)
                )
                Spacer(Modifier.width(14.dp))
                Text(label, color = if (on) c.primary else c.text, fontSize = 14.sp)
            }
        }

        if (settings.vietnamese > 0) {
            val sourceName = info.source?.let { tag ->
                runCatching { java.util.Locale.forLanguageTag(tag).getDisplayLanguage(java.util.Locale("vi")) }
                    .getOrNull()?.takeIf { it.isNotBlank() }
            }
            val status = when (info.status) {
                CaptionStatus.LOADING -> "Đang tải phụ đề…"
                CaptionStatus.READY ->
                    if (info.translated) "Phụ đề được YouTube dịch tự động" + (sourceName?.let { " từ $it" } ?: "") + "."
                    else "Video có sẵn phụ đề tiếng Việt."
                CaptionStatus.NONE -> "Video này không có phụ đề nên chưa dịch được."
                CaptionStatus.ERROR -> "Chưa tải được phụ đề. Chọn lại để thử lần nữa."
                CaptionStatus.UNSUPPORTED -> "Bộ phát YouTube đang được cập nhật để hỗ trợ tính năng này. Thử lại sau ít phút."
                CaptionStatus.OFF -> "Phụ đề sẽ hiện khi video bắt đầu phát."
            }
            Text(
                status, color = c.muted, fontSize = 12.sp, lineHeight = 17.sp,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 4.dp)
            )
        }

        if (PlayerController.speaks(settings.vietnamese) &&
            (voice == VoiceOver.State.NO_VOICE || voice == VoiceOver.State.NO_ENGINE)
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(c.surface2)
                    .padding(12.dp)
            ) {
                Text(
                    "Máy chưa có giọng đọc tiếng Việt. Vào cài đặt Chuyển văn bản thành giọng nói, chọn Dịch vụ lời nói của Google và tải giọng Tiếng Việt, rồi quay lại đây.",
                    color = c.text, fontSize = 12.5.sp, lineHeight = 18.sp
                )
                Spacer(Modifier.height(10.dp))
                PillButton("Cài giọng đọc", LucIcons.Download, { VoiceOver.openVoiceSettings(context) })
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
