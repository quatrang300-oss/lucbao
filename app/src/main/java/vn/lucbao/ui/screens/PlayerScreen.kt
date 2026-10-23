@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package vn.lucbao.ui.screens

import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.BoxWithConstraints
import kotlin.math.roundToInt
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.AnnotatedString
import vn.lucbao.data.videoKey
import vn.lucbao.data.Library
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.geometry.Size
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.media.AudioManager
import android.provider.Settings
import android.view.Window
import android.view.WindowManager
import android.graphics.Color as AColor
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
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
import vn.lucbao.player.PlayerStats
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

/** Playback speeds, 0,25× to 10×. */
private val SPEEDS = listOf(
    0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f, 3.5f, 4f,
    4.5f, 5f, 5.5f, 6f, 6.5f, 7f, 7.5f, 8f, 8.5f, 9f, 9.5f, 10f,
)

/** The next speed up; after 10× it starts again at 0,25×. */
private fun nextSpeed(s: Float): Float = SPEEDS.firstOrNull { it > s + 0.001f } ?: SPEEDS.first()

private fun speedIndex(s: Float): Int =
    SPEEDS.indices.minByOrNull { kotlin.math.abs(SPEEDS[it] - s) } ?: SPEEDS.indexOf(1f)

/** "1x", "1.25x", "10x" */
private fun speedText(s: Float): String =
    (if (s == s.toInt().toFloat()) s.toInt().toString() else s.toString()) + "x"

/** "Tốc độ" at normal speed, else with the speed: "Tốc độ 1.25x". */
private fun speedButtonText(s: Float): String = if (s == 1f) "Tốc độ" else "Tốc độ " + speedText(s)

/** Tap: [onTap]. Held for 1,5 s: [onHold] (the rest of that touch is then ignored). */
@Composable
private fun Modifier.speedPress(onTap: () -> Unit, onHold: () -> Unit): Modifier {
    val tap by rememberUpdatedState(onTap)
    val hold by rememberUpdatedState(onHold)
    return pointerInput(Unit) { speedGesture({ tap() }, { hold() }) }
}

private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.speedGesture(
    onTap: () -> Unit,
    onHold: () -> Unit,
) {
    awaitEachGesture {
        awaitFirstDown().consume()
        var released = false
        val up = withTimeoutOrNull(1_500L) {
            waitForUpOrCancellation().also { released = true }
        }
        if (released) {
            if (up != null) {
                up.consume()
                onTap()
            }
        } else {
            onHold()
            do {
                val e = awaitPointerEvent()
                e.changes.forEach { it.consume() }
            } while (e.changes.any { it.pressed })
        }
    }
}
private const val PANEL_HIDE_MS = 10_000L

/** Brightness picked by swiping in full screen; reused the next time full screen opens. */
private var sessionBrightness: Float? = null

private const val GESTURE_NONE = 0
private const val GESTURE_BRIGHTNESS = 1
private const val GESTURE_VOLUME = 2

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

private fun setWindowBrightness(w: Window, value: Float) {
    val lp = w.attributes
    lp.screenBrightness = value
    w.attributes = lp
}

/** 0..1, from the window override or else (roughly) from the system setting. */
private fun currentBrightness(activity: Activity?): Float {
    val own = activity?.window?.attributes?.screenBrightness ?: -1f
    if (own >= 0f) return own
    val cr = activity?.contentResolver ?: return 0.5f
    // With auto-brightness the saved value is not what the screen shows: start from the middle.
    val auto = runCatching {
        Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE) ==
            Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
    }.getOrDefault(false)
    if (auto) return 0.5f
    val sys = runCatching { Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS) }.getOrDefault(128)
    return (sys / 255f).coerceIn(0.01f, 1f)
}

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
    // The speed slider (held "Tốc độ" button); shown on the video, above the time bar.
    var speedSlider by remember { mutableStateOf(false) }
    LaunchedEffect(locked) { if (locked) speedSlider = false }
    var showVietnamese by remember { mutableStateOf(false) }
    var showChapters by remember { mutableStateOf(false) }
    var showComments by remember { mutableStateOf(false) }
    val chapters by vn.lucbao.player.Chapters.list.collectAsStateWithLifecycle()
    LaunchedEffect(ui.video?.url) { showChapters = false; showComments = false }
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
                    speedSlider = speedSlider,
                    onSpeedSlider = { speedSlider = it },
                    onLock = { onLockChange(true) },
                    onVietnamese = { showVietnamese = true },
                    onChapters = { showChapters = true },
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
                    onSpeedHold = { speedSlider = true },
                    onVietnamese = { showVietnamese = true },
                    onChapters = { showChapters = true },
                    onComments = { showComments = true },
                )
            }
        }
        if (locked) LockOverlay(fullscreen = fullscreen, onUnlock = { onLockChange(false) })
    }

    if (showVietnamese && !locked) {
        VietnameseSheet(onDismiss = { showVietnamese = false })
    }

    if (showChapters && chapters.isNotEmpty() && !locked) {
        val pos = PlayerController.exo.currentPosition
        ChaptersSheet(
            chapters, vn.lucbao.player.Chapters.indexAt(chapters, pos),
            onPick = { ch ->
                PlayerController.exo.seekTo(ch.startMs)
                showChapters = false
            },
            onDismiss = { showChapters = false },
        )
    }

    val commentsUrl = ui.details?.url ?: ui.video?.url
    if (showComments && commentsUrl != null && !locked) {
        CommentsSheet(
            commentsUrl,
            onPlayVideo = { v -> showComments = false; onPlay(v) },
            onDismiss = { showComments = false },
        )
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
fun VideoSurface(modifier: Modifier = Modifier, zoom: Float = 1f, pan: Offset = Offset.Zero) {
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
        // Phóng to: the player itself is laid out [zoom] times bigger than the screen (and moved by
        // [pan]), so the picture really grows past its black bars. Scaling the video surface
        // instead would keep it cut to its old size.
        modifier = modifier.layout { measurable, constraints ->
            val w = constraints.maxWidth
            val h = constraints.maxHeight
            if (zoom <= 1.001f || w == Constraints.Infinity || h == Constraints.Infinity) {
                val p = measurable.measure(constraints)
                layout(p.width, p.height) { p.place(0, 0) }
            } else {
                val zw = (w * zoom).roundToInt()
                val zh = (h * zoom).roundToInt()
                val p = measurable.measure(Constraints.fixed(zw, zh))
                layout(w, h) {
                    p.place((w - zw) / 2 + pan.x.roundToInt(), (h - zh) / 2 + pan.y.roundToInt())
                }
            }
        }
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
    speedSlider: Boolean,
    onSpeedSlider: (Boolean) -> Unit,
    onLock: () -> Unit,
    onVietnamese: () -> Unit,
    onChapters: () -> Unit,
    onMore: () -> Unit,
) {
    val c = Luc.colors
    val exo = PlayerController.exo
    val chapters by vn.lucbao.player.Chapters.list.collectAsStateWithLifecycle()
    val skipNotice by vn.lucbao.player.SponsorSkip.notice.collectAsStateWithLifecycle()
    LaunchedEffect(skipNotice) {
        if (skipNotice != null) {
            delay(6_000)
            vn.lucbao.player.SponsorSkip.dismissNotice()
        }
    }
    val settings by Prefs.state.collectAsStateWithLifecycle()
    val caption by PlayerController.caption.collectAsStateWithLifecycle()
    var controls by remember { mutableStateOf(true) }
    var touch by remember { mutableIntStateOf(0) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var buffered by remember { mutableLongStateOf(0L) }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }

    // Phóng to / thu nhỏ (full screen, two fingers): shape of the video for "Lấp đầy".
    var videoAspect by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            position = exo.currentPosition
            duration = exo.duration.coerceAtLeast(0)
            buffered = exo.bufferedPosition
            val vs = exo.videoSize
            videoAspect = if (vs.width > 0 && vs.height > 0) vs.width * vs.pixelWidthHeightRatio / vs.height else 0f
            delay(250)
        }
    }
    val haptic = LocalHapticFeedback.current
    var areaSize by remember { mutableStateOf(IntSize.Zero) }
    var zoom by remember { mutableFloatStateOf(1f) }
    // Unsnapped zoom, so a pinch can move on past the "Lấp đầy" mark.
    var rawZoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var pinching by remember { mutableStateOf(false) }
    fun resetZoom() {
        zoom = 1f
        rawZoom = 1f
        pan = Offset.Zero
    }
    // Another video, leaving full screen or opening "Nhiều video hơn": back to the video's own shape.
    LaunchedEffect(ui.video?.url, fullscreen, panelOpen) { resetZoom() }
    val zoomable = fullscreen && !panelOpen && ui.quality?.audioOnly != true && ui.error == null &&
        videoAspect > 0f && areaSize.width > 0 && areaSize.height > 0
    val areaW = areaSize.width.toFloat()
    val areaH = areaSize.height.toFloat()
    // Size of the picture at 1x (fitted inside the screen, black bars around it).
    val wide = areaH > 0f && videoAspect > areaW / areaH
    val fitW = if (wide) areaW else areaH * videoAspect
    val fitH = if (wide && videoAspect > 0f) areaW / videoAspect else areaH
    val fillScale = if (zoomable && fitW > 0f && fitH > 0f) maxOf(areaW / fitW, areaH / fitH) else 1f
    val canFill = zoomable && fillScale > 1.02f
    fun clampPan(p: Offset, z: Float): Offset {
        val mx = maxOf(0f, (fitW * z - areaW) / 2f)
        val my = maxOf(0f, (fitH * z - areaH) / 2f)
        return Offset(p.x.coerceIn(-mx, mx), p.y.coerceIn(-my, my))
    }
    val zoomOk by rememberUpdatedState(zoomable)
    val onPinch by rememberUpdatedState<(Float, Offset) -> Unit> { z, p ->
        val before = zoom
        rawZoom = (rawZoom * z).coerceIn(1f, 4f)
        var nz = rawZoom
        // Sticks to "Lấp đầy" when passing by it.
        if (canFill && kotlin.math.abs(nz - fillScale) < 0.05f * fillScale) nz = fillScale
        val hitFill = canFill && nz == fillScale && before != fillScale
        val hitOne = nz == 1f && before != 1f
        if (hitFill || hitOne) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        zoom = nz
        pan = clampPan(pan + p, nz)
    }
    // Gesture handlers live across recompositions: read the slider state through these.
    val sliderOpen by rememberUpdatedState(speedSlider && !(ui.video?.live == true || ui.details?.live == true))
    val setSlider by rememberUpdatedState(onSpeedSlider)
    val isLiveVideo = ui.video?.live == true || ui.details?.live == true
    val sliderOn = speedSlider && !isLiveVideo
    var sliderTouch by remember { mutableIntStateOf(0) }
    var sliderDragging by remember { mutableStateOf(false) }
    // The slider sits above the time bar: keep the controls up while it shows.
    LaunchedEffect(sliderOn) { if (sliderOn) controls = true }
    // Another video: close it.
    LaunchedEffect(ui.video?.url) { if (speedSlider) onSpeedSlider(false) }
    // Closes 3 s after the last touch on it.
    LaunchedEffect(sliderOn, sliderTouch, sliderDragging) {
        if (sliderOn && !sliderDragging) {
            delay(3_000)
            onSpeedSlider(false)
        }
    }
    // Hide the controls a few seconds after the last touch while playing.
    LaunchedEffect(controls, touch, ui.isPlaying, sliderOn) {
        if (controls && ui.isPlaying && !sliderOn) {
            delay(3500)
            controls = false
        }
    }

    // Full screen: swipe up/down on the left half for brightness, on the right half for volume.
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val audio = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    var gestureKind by remember { mutableIntStateOf(GESTURE_NONE) }
    var gestureLevel by remember { mutableFloatStateOf(0f) }
    var gestureActive by remember { mutableStateOf(false) }
    // Hide the brightness / volume bar a moment after the finger is lifted.
    LaunchedEffect(gestureActive, gestureKind) {
        if (!gestureActive && gestureKind != GESTURE_NONE) {
            delay(700)
            gestureKind = GESTURE_NONE
        }
    }
    // The brightness only applies in full screen: leaving it gives the phone's brightness back.
    DisposableEffect(fullscreen, activity) {
        val w = activity?.window
        if (fullscreen && w != null) sessionBrightness?.let { setWindowBrightness(w, it) }
        onDispose {
            if (fullscreen && w != null) {
                setWindowBrightness(w, WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
            }
        }
    }

    Box(
        modifier
            .background(Color.Black)
            .onSizeChanged { areaSize = it }
            // Two fingers: phóng to / thu nhỏ and move the picture. One finger keeps its gestures
            // (tap, double tap to seek, brightness / volume swipes).
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    var active = false
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.changes.none { it.pressed }) break
                        if (zoomOk && event.changes.count { it.pressed } >= 2) {
                            if (!active) {
                                active = true
                                pinching = true
                            }
                            val z = event.calculateZoom()
                            val p = event.calculatePan()
                            if (z != 1f || p != Offset.Zero) onPinch(z, p)
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    }
                    if (active) pinching = false
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        // A tap anywhere else closes the speed slider at once.
                        if (sliderOpen) setSlider(false) else controls = !controls
                        touch++
                    },
                    onDoubleTap = { o ->
                        if (o.x < size.width / 2) exo.seekBack() else exo.seekForward()
                        controls = true
                        touch++
                    }
                )
            }
            .pointerInput(fullscreen) {
                if (!fullscreen) return@pointerInput
                var kind = GESTURE_NONE
                var level = 0f
                var volMax = 1
                detectVerticalDragGestures(
                    onDragStart = { o ->
                        // Leave the top and bottom edges to the system's own swipe gestures.
                        val edge = size.height * 0.08f
                        kind = when {
                            o.y < edge || o.y > size.height - edge -> GESTURE_NONE
                            sliderOpen -> GESTURE_NONE
                            controls && o.y > size.height * 0.72f -> GESTURE_NONE // seek bar / buttons
                            o.x < size.width / 2 -> GESTURE_BRIGHTNESS
                            audio.isVolumeFixed -> GESTURE_NONE
                            else -> GESTURE_VOLUME
                        }
                        level = when (kind) {
                            GESTURE_BRIGHTNESS -> currentBrightness(activity)
                            GESTURE_VOLUME -> {
                                volMax = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                                audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / volMax
                            }
                            else -> 0f
                        }
                        if (kind != GESTURE_NONE) {
                            gestureKind = kind
                            gestureLevel = level
                            gestureActive = true
                        }
                    },
                    onDragEnd = { gestureActive = false },
                    onDragCancel = { gestureActive = false },
                    onVerticalDrag = { change, dy ->
                        if (kind != GESTURE_NONE) {
                            change.consume()
                            // A swipe over most of the screen height goes from 0 to 100 %.
                            level = (level - dy / (size.height * 0.8f)).coerceIn(0f, 1f)
                            when (kind) {
                                GESTURE_BRIGHTNESS -> {
                                    val b = level.coerceAtLeast(0.01f)
                                    sessionBrightness = b
                                    activity?.window?.let { setWindowBrightness(it, b) }
                                }
                                GESTURE_VOLUME -> {
                                    val v = Math.round(level * volMax)
                                    if (v != audio.getStreamVolume(AudioManager.STREAM_MUSIC)) {
                                        runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0) }
                                    }
                                }
                            }
                            gestureLevel = level
                        }
                    },
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
            VideoSurface(Modifier.fillMaxSize(), zoom, pan)
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

        if (gestureKind != GESTURE_NONE) {
            GestureLevel(gestureKind, gestureLevel, Modifier.align(Alignment.TopCenter).padding(top = 48.dp))
        }

        if (pinching) {
            Text(
                zoomText(zoom), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        skipNotice?.let { n ->
            val controlsShown = controls || !ui.isPlaying
            SkipNoticeChip(
                n, onRewatch = { vn.lucbao.player.SponsorSkip.rewatch(exo) },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(
                        start = if (fullscreen) 24.dp else 10.dp,
                        bottom = when {
                            controlsShown && fullscreen -> 112.dp
                            controlsShown -> 50.dp
                            fullscreen -> 26.dp
                            else -> 10.dp
                        }
                    )
            )
        }

        val statsOn by PlayerController.showStats.collectAsStateWithLifecycle()
        if (statsOn && ui.error == null && ui.quality?.audioOnly != true) {
            StatsOverlay(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(start = if (fullscreen) 24.dp else 10.dp, top = if (fullscreen) 56.dp else 44.dp)
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

                // Zoomed in: "2.1x · Về gốc" under the top bar; tap to go back to the video's own shape.
                if (zoomable && zoom > 1.01f) {
                    Text(
                        zoomText(zoom) + "  ·  Về gốc", color = Color.White, fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 54.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color.Black.copy(alpha = 0.5f))
                            .clickable { resetZoom(); touch++ }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }

                // In the small player the speed slider would cover these: hidden while it is open.
                if (!(sliderOn && !fullscreen)) Row(
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
                val isLive = ui.video?.live == true || ui.details?.live == true
                // Full screen: playback speed at the bottom left, on the "Nhiều video hơn" row.
                val showSpeedButton = fullscreen && !isLive
                val showFill = canFill
                val chapterIdx = vn.lucbao.player.Chapters.indexAt(chapters, if (dragging) (dragValue * duration).toLong() else position)
                if (chapters.isNotEmpty() && chapterIdx >= 0 && !isLive) {
                    // Current chapter above the time bar; tap for the list.
                    Text(
                        "• " + chapters[chapterIdx].title.ifBlank { "Chương ${chapterIdx + 1}" } + "  ›",
                        color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(
                                start = 16.dp, end = 80.dp,
                                bottom = when {
                                    showMore || showSpeedButton || showFill -> 104.dp
                                    fullscreen -> 58.dp
                                    else -> 44.dp
                                }
                            )
                            .clip(RoundedCornerShape(50))
                            .clickable { onChapters(); touch++ }
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(
                            start = 12.dp, end = 4.dp,
                            bottom = when {
                                showMore || showSpeedButton || showFill -> 58.dp
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
                        Box(Modifier.weight(1f).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
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
                                modifier = Modifier.fillMaxWidth()
                            )
                            // Chapters: a small gap in the time bar where each one starts.
                            if (chapters.size > 1 && duration > 0) {
                                Canvas(Modifier.matchParentSize()) {
                                    val inset = 10.dp.toPx()
                                    val w = size.width - inset * 2
                                    chapters.drop(1).forEach { ch ->
                                        val x = inset + w * (ch.startMs.toFloat() / duration).coerceIn(0f, 1f)
                                        drawRect(
                                            Color.Black.copy(alpha = 0.75f),
                                            topLeft = Offset(x - 1.dp.toPx(), size.height / 2 - 4.dp.toPx()),
                                            size = Size(2.dp.toPx(), 8.dp.toPx())
                                        )
                                    }
                                }
                            }
                        }
                        Text(Format.millis(duration), color = Color.White, fontSize = 11.sp)
                    }
                    IconTap(
                        if (fullscreen) LucIcons.FullscreenExit else LucIcons.Fullscreen,
                        "Toàn màn hình", size = 33, tint = Color.White, box = 52,
                        onClick = onToggleFullscreen
                    )
                }

                if (showSpeedButton || showFill) Row(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 16.dp, bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                if (showSpeedButton) {
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = if (ui.speed != 1f) 0.22f else 0.12f))
                            .speedPress(
                                onTap = {
                                    PlayerController.setSpeed(nextSpeed(PlayerController.ui.value.speed))
                                    touch++
                                },
                                onHold = { setSlider(true); touch++ },
                            )
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            speedButtonText(ui.speed),
                            color = if (ui.speed != 1f) c.primary else Color.White,
                            fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                if (showFill) {
                    // "Lấp đầy": zoom just enough to lose the black bars; "Vừa màn hình": back.
                    val filled = kotlin.math.abs(zoom - fillScale) < 0.01f
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = if (filled) 0.22f else 0.12f))
                            .clickable {
                                if (filled) resetZoom() else {
                                    zoom = fillScale
                                    rawZoom = fillScale
                                    pan = Offset.Zero
                                }
                                touch++
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(LucIcons.AspectRatio, null, tint = if (filled) c.primary else Color.White, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (filled) "Vừa màn hình" else "Lấp đầy",
                            color = if (filled) c.primary else Color.White,
                            fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                        )
                    }
                }
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

        // Speed slider: bottom left, just above the time bar, half the width.
        if (sliderOn) {
            SpeedRuler(
                speed = ui.speed,
                onPick = { if (it != PlayerController.ui.value.speed) PlayerController.setSpeed(it) },
                onTouch = { sliderTouch++ },
                onDragging = { sliderDragging = it },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = if (fullscreen) 30.dp else 14.dp, bottom = if (fullscreen) 106.dp else 46.dp)
                    .fillMaxWidth(0.5f)
            )
        }
    }
}

/**
 * The speed slider: a straight ruler with a mark for every speed (longer marks at 1×, 2×, 3×…),
 * no numbers except the speed under the finger. Transparent; the marks have a soft shadow so
 * they stay readable on bright videos.
 */
@Composable
private fun SpeedRuler(
    speed: Float,
    onPick: (Float) -> Unit,
    onTouch: () -> Unit,
    onDragging: (Boolean) -> Unit,
    modifier: Modifier,
) {
    val c = Luc.colors
    val measurer = rememberTextMeasurer()
    val pick by rememberUpdatedState(onPick)
    val touched by rememberUpdatedState(onTouch)
    val dragging by rememberUpdatedState(onDragging)
    val index = speedIndex(speed)
    Canvas(
        modifier
            .height(60.dp)
            .pointerInput(Unit) {
                fun at(x: Float) {
                    val i = Math.round(x / size.width * SPEEDS.lastIndex).coerceIn(0, SPEEDS.lastIndex)
                    pick(SPEEDS[i])
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    touched()
                    dragging(true)
                    at(down.position.x)
                    drag(down.id) { ch ->
                        ch.consume()
                        touched()
                        at(ch.position.x)
                    }
                    dragging(false)
                    touched()
                }
            }
    ) {
        val w = size.width
        val base = size.height - 8.dp.toPx()
        val step = w / SPEEDS.lastIndex
        val shade = Color.Black.copy(alpha = 0.45f)
        fun mark(x: Float, top: Float, bottom: Float, color: Color, width: Float, cap: StrokeCap = StrokeCap.Butt) {
            drawLine(shade, Offset(x + 1f, top + 1.5f), Offset(x + 1f, bottom + 1.5f), width + 1.5f, cap)
            drawLine(color, Offset(x, top), Offset(x, bottom), width, cap)
        }
        drawLine(shade, Offset(0f, base + 1.5f), Offset(w, base + 1.5f), 3f)
        drawLine(Color.White.copy(alpha = 0.6f), Offset(0f, base), Offset(w, base), 1.5.dp.toPx())
        SPEEDS.forEachIndexed { i, rate ->
            val x = i * step
            val major = rate == rate.toInt().toFloat()
            mark(
                x, base - (if (major) 16.dp else 9.dp).toPx(), base,
                Color.White.copy(alpha = if (major) 0.95f else 0.7f),
                (if (major) 2.dp else 1.4.dp).toPx()
            )
            if (i < SPEEDS.lastIndex) {
                mark(x + step / 2, base - 5.dp.toPx(), base, Color.White.copy(alpha = 0.4f), 1.dp.toPx())
            }
        }
        val x = index * step
        mark(x, base - 24.dp.toPx(), base + 4.dp.toPx(), c.primary, 4.dp.toPx(), StrokeCap.Round)
        val label = measurer.measure(
            speedText(SPEEDS[index]),
            TextStyle(
                color = c.primary, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                shadow = Shadow(Color.Black.copy(alpha = 0.7f), Offset(0f, 1f), 4f)
            )
        )
        val lx = (x - label.size.width / 2f).coerceIn(0f, (w - label.size.width).coerceAtLeast(0f))
        drawText(label, topLeft = Offset(lx, 0f))
    }
}

@Composable
private fun Details(
    ui: PlayerUi,
    onPip: () -> Unit,
    onPlay: (Video) -> Unit,
    onQuality: () -> Unit,
    onSpeedHold: () -> Unit,
    onVietnamese: () -> Unit,
    onChapters: () -> Unit,
    onComments: () -> Unit,
) {
    val c = Luc.colors
    val context = LocalContext.current
    val d = ui.details
    val watchLater by Library.watchLater.collectAsStateWithLifecycle()
    val chapters by vn.lucbao.player.Chapters.list.collectAsStateWithLifecycle()
    var sleepSheet by remember { mutableStateOf(false) }
    val v = ui.video ?: return
    val settings by Prefs.state.collectAsStateWithLifecycle()
    var expanded by remember(v.url) { mutableStateOf(false) }
    var channelSheet by remember { mutableStateOf(false) }
    var downloadSheet by remember { mutableStateOf(false) }
    val channelUrl = d?.channelUrl ?: v.channelUrl
    // Chế độ Trẻ em: no channel page, following, downloads or sharing (ways out of the kids' list).
    val kids by vn.lucbao.kids.KidsStore.active.collectAsStateWithLifecycle()

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    LazyColumn(Modifier.fillMaxSize(), state = listState) {
        item(key = "info") {
            Column(
                Modifier
                    .fillMaxWidth()
                    // Tap opens the description; it closes with "Rút gọn" (links inside stay tappable).
                    .clickable(enabled = !expanded) { expanded = true }
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
                if (expanded) {
                    if (!d?.description.isNullOrBlank()) {
                        Text(
                            // Chế độ Trẻ em: no links (they lead out of the kids' list).
                            if (kids) AnnotatedString(d?.description ?: "")
                            else rememberLinkedText(d?.description ?: "", onPlay),
                            color = c.text, fontSize = 12.5.sp, lineHeight = 18.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(c.surface2)
                                .padding(12.dp)
                        )
                    }
                    Text(
                        "Rút gọn", color = c.primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(top = 6.dp)
                            .clip(RoundedCornerShape(50))
                            .clickable {
                                expanded = false
                                // Back to the title, no scrolling up by hand.
                                scope.launch { listState.animateScrollToItem(0) }
                            }
                            .padding(horizontal = 18.dp, vertical = 8.dp)
                    )
                }
            }
        }
        item(key = "channel") {
            // Left: avatar + name (at most 3/5 of the line, 2 lines, centred on the avatar);
            // tap opens the channel sheet (it has Theo dõi). Right: "Yêu thích video".
            BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                val nameMax = maxWidth * 0.6f - 50.dp
                val favWidth = (maxWidth - 16.dp) / 3
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(enabled = channelUrl != null && !kids) { channelSheet = true }
                            .padding(vertical = 2.dp, horizontal = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Avatar(v.channel, 40.dp, d?.channelAvatar)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            v.channel, color = c.text, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold,
                            lineHeight = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = nameMax.coerceAtLeast(60.dp))
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    // As wide as one of the three tools below; heart above, name under it.
                    FavoriteCell(ui.favorite, Modifier.width(favWidth)) {
                        PlayerController.toggleFavorite()
                    }
                }
            }
        }
        item(key = "actions") {
            // Two rows of three, in this order; tools that don't apply leave and the rest move up.
            val audioOnly = ui.quality?.audioOnly == true
            val later = watchLater.any { videoKey(it.video.url) == videoKey(v.url) }
            val sleepAt by PlayerController.sleepAt.collectAsStateWithLifecycle()
            val (sleepText, sleepOn) = videoSleepLabel()
            val tools = buildList {
                if (d != null && !d.live && !kids) add(Tool(LucIcons.Download, "Tải về", false) { downloadSheet = true })
                if (ui.choices.any { it.audioOnly }) add(Tool(LucIcons.Headphones, "Chỉ nghe", audioOnly) {
                    val target = if (audioOnly) Quality.pick(ui.choices, Prefs.current.qualityWifi)
                    else ui.choices.firstOrNull { it.audioOnly }
                    target?.let { PlayerController.setQuality(it) }
                })
                if (!audioOnly) add(Tool(LucIcons.Pip, "Thu nhỏ", false, onPip))
                add(
                    Tool(
                        LucIcons.Timer,
                        when {
                            sleepAt == -1L -> "Hết video tắt"
                            sleepOn -> sleepText.replace("Tắt sau", "Còn")
                            else -> "Hẹn giờ tắt"
                        },
                        sleepOn
                    ) { sleepSheet = true }
                )
                add(Tool(LucIcons.Bookmark, if (later) "Đã lưu" else "Xem sau", later) {
                    Library.toggleWatchLater(ui.video ?: v)
                })
                if (!kids) add(Tool(LucIcons.Share, "Chia sẻ", false) {
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, v.url)
                    context.startActivity(Intent.createChooser(send, "Chia sẻ video"))
                })
            }
            Column(
                Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                tools.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { t -> ToolCell(t, Modifier.weight(1f)) }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        // Bình luận (left) and Chương (right); one alone takes the whole row.
        val showComments = !kids && d != null && !d.live
        val showChapters = chapters.isNotEmpty()
        if (showComments || showChapters) {
            item(key = "commentsChapters") {
                Row(
                    Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (showComments) WideCell(LucIcons.Comment, "Bình luận", Modifier.weight(1f), onComments)
                    if (showChapters) WideCell(LucIcons.Chapters, "Chương", Modifier.weight(1f), onChapters)
                }
            }
        }
        item(key = "nextTitle") {
            val loop by PlayerController.loopVideo.collectAsStateWithLifecycle()
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Tiếp theo", fontWeight = FontWeight.SemiBold, fontSize = 17.sp,
                    color = c.text, modifier = Modifier.weight(1f)
                )
                // Lặp lại: replay this video when it ends (wins over autoplay).
                SmallToggle(LucIcons.RepeatOne, "Lặp lại", loop) { PlayerController.setLoopVideo(!loop) }
                Spacer(Modifier.width(8.dp))
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

    if (sleepSheet) {
        SleepTimerSheet(onDismiss = { sleepSheet = false })
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
    // Easy to see: filled green when on, a clear outline when off.
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (on) c.primary else c.surface2)
            .border(1.5.dp, if (on) c.primary else c.text.copy(alpha = 0.35f), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = if (on) c.onPrimary else c.text, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(5.dp))
        Text(text, color = if (on) c.onPrimary else c.text, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** One tool under the video (Tải về, Chỉ nghe, …). */
private class Tool(val icon: ImageVector, val text: String, val on: Boolean, val onClick: () -> Unit)

/** A tool in the 3-per-row grid: one line, icon and name, framed like the old chips. */
@Composable
private fun ToolCell(t: Tool, modifier: Modifier) {
    val c = Luc.colors
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(c.surface2)
            .border(1.dp, if (t.on) c.primary.copy(alpha = 0.5f) else c.line, RoundedCornerShape(50))
            .clickable(onClick = t.onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(t.icon, null, tint = if (t.on) c.primary else c.text, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(5.dp))
        Text(
            t.text, color = if (t.on) c.primary else c.text, fontSize = 12.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private val HeartRed = Color(0xFFE53935)

/** "Yêu thích video": framed, heart above (red when liked; name and frame turn green), name under it. */
@Composable
private fun FavoriteCell(on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = Luc.colors
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(c.surface2)
            .border(1.dp, if (on) c.primary.copy(alpha = 0.6f) else c.line, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            if (on) LucIcons.Heart else LucIcons.HeartOutline, null,
            tint = if (on) HeartRed else c.text, modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.height(3.dp))
        Text(
            "Yêu thích video", color = if (on) c.primary else c.text, fontSize = 11.5.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Bình luận / Chương: framed like the tools, icon and name side by side. */
@Composable
private fun WideCell(icon: ImageVector, text: String, modifier: Modifier, onClick: () -> Unit) {
    val c = Luc.colors
    Row(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(c.surface2)
            .border(1.dp, c.line, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = c.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, color = c.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
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
        Text(
            "Mạng yếu thì video tự giảm độ nét một chút, mạng ổn lại tự nét như mức đã chọn.",
            color = c.muted, fontSize = 11.5.sp, lineHeight = 16.sp,
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 6.dp)
        )
        val statsOn by PlayerController.showStats.collectAsStateWithLifecycle()
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Thông số video", color = c.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
            SmallToggle(LucIcons.Tune, if (statsOn) "Đang hiện" else "Hiện", statsOn) {
                PlayerController.setShowStats(!statsOn)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** The bar shown while swiping for brightness or volume in full screen. */
@Composable
private fun GestureLevel(kind: Int, level: Float, modifier: Modifier) {
    val c = Luc.colors
    val icon = when {
        kind == GESTURE_BRIGHTNESS -> LucIcons.Brightness
        level <= 0f -> LucIcons.VolumeOff
        else -> LucIcons.Volume
    }
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.65f))
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, if (kind == GESTURE_BRIGHTNESS) "Độ sáng" else "Âm lượng", tint = Color.White, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.width(130.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.25f))) {
            Box(Modifier.fillMaxWidth(level.coerceIn(0f, 1f)).fillMaxHeight().background(c.primary))
        }
        Spacer(Modifier.width(10.dp))
        Text("${Math.round(level * 100)}%", color = Color.White, fontSize = 12.sp, modifier = Modifier.width(36.dp))
    }
}

/** "Thông số video": what is really playing and how well the network keeps up. */
@Composable
private fun StatsOverlay(modifier: Modifier) {
    var st by remember { mutableStateOf(PlayerStats()) }
    LaunchedEffect(Unit) {
        while (true) {
            st = PlayerController.stats()
            delay(1_000)
        }
    }
    fun mbps(bits: Long) = if (bits <= 0) "—" else String.format(java.util.Locale.ROOT, "%.1f Mb/s", bits / 1_000_000.0).replace('.', ',')
    val playing = if (st.width > 0 && st.height > 0) {
        "${st.width}×${st.height}" + (if (st.fps > 0) " · ${st.fps}fps" else "") +
            (st.codec?.let { " · " + it.substringBefore('.') } ?: "")
    } else "—"
    Column(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.68f))
            .clickable { PlayerController.setShowStats(false) }
            .padding(horizontal = 10.dp, vertical = 7.dp)
    ) {
        StatLine("Đang phát", playing)
        StatLine("Mức đã chọn", st.cap ?: "—")
        StatLine("Độ nặng video", mbps(st.videoBitrate.toLong()))
        StatLine("Tốc độ mạng", mbps(st.network))
        StatLine("Đã tải trước", "${st.bufferedMs / 1000} giây")
        StatLine("Số lần phải chờ tải", st.stalls.toString())
        Text("Chạm để ẩn", color = Color.White.copy(alpha = 0.55f), fontSize = 10.sp, modifier = Modifier.padding(top = 3.dp))
    }
}

@Composable
private fun StatLine(label: String, value: String) {
    Row {
        Text("$label: ", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
        Text(value, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
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
