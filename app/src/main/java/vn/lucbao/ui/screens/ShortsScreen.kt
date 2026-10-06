@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package vn.lucbao.ui.screens

import android.content.Intent
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import vn.lucbao.data.Format
import vn.lucbao.data.Library
import vn.lucbao.data.Video
import vn.lucbao.data.videoKey
import vn.lucbao.player.ShortsPlayer
import vn.lucbao.shorts.SHORTS_TOPICS
import vn.lucbao.shorts.ShortsViewModel
import vn.lucbao.ui.components.LucChip
import vn.lucbao.ui.components.PillButton
import vn.lucbao.ui.components.errorText
import vn.lucbao.ui.theme.Luc
import vn.lucbao.ui.theme.LucIcons

/** Short vertical videos, swiped up and down. */
@Composable
fun ShortsScreen(active: Boolean, onOpenVideo: (Video) -> Unit) {
    val c = Luc.colors
    val context = LocalContext.current
    val vm: ShortsViewModel = viewModel()
    val topic by vm.topic.collectAsStateWithLifecycle()
    val feed by vm.feed.collectAsStateWithLifecycle()
    val items = feed.items
    val pager = rememberPagerState { items.size }

    // Leaving the tab or the app (or opening the main player) stops the short and frees the
    // video decoder; coming back plays it again.
    DisposableEffect(Unit) { onDispose { ShortsPlayer.stop() } }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { ShortsPlayer.stop() }
    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        if (active) items.getOrNull(pager.settledPage)?.let { ShortsPlayer.play(context, it) }
    }
    LaunchedEffect(active) { if (!active) ShortsPlayer.stop() }

    // A new topic (or "Thử lại") starts at the first short.
    LaunchedEffect(items.isEmpty()) {
        if (items.isNotEmpty() && pager.currentPage != 0) pager.scrollToPage(0)
    }

    val cur = items.getOrNull(pager.settledPage)
    LaunchedEffect(cur?.url, active) {
        if (active && cur != null) ShortsPlayer.play(context, cur)
    }
    LaunchedEffect(pager.settledPage, items.size) {
        items.getOrNull(pager.settledPage + 1)?.let { ShortsPlayer.prefetch(it) }
        items.getOrNull(pager.settledPage + 2)?.let { ShortsPlayer.prefetch(it) }
        if (items.isNotEmpty() && pager.settledPage >= items.size - 4) vm.loadMore()
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (items.isNotEmpty()) {
            VerticalPager(
                state = pager,
                beyondViewportPageCount = 1,
                key = { i -> "s$i-${items.getOrNull(i)?.url}" },
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val v = items.getOrNull(page)
                if (v != null) ShortPage(v, isCurrent = page == pager.settledPage, onOpenVideo = onOpenVideo)
            }
        } else {
            Column(
                Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (feed.loading) {
                    CircularProgressIndicator(color = c.primary, strokeWidth = 3.dp, modifier = Modifier.size(36.dp))
                } else {
                    Text(
                        feed.error ?: "Chưa có video ngắn nào.", color = Color.White, fontSize = 14.sp,
                        textAlign = TextAlign.Center, lineHeight = 20.sp
                    )
                    Spacer(Modifier.height(14.dp))
                    PillButton("Thử lại", LucIcons.Refresh, { vm.refresh() })
                }
            }
        }

        // Topic chips over the top of the video.
        Row(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent)))
                .statusBarsPadding()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SHORTS_TOPICS.forEach { t -> LucChip(t.label, t.id == topic.id, { vm.selectTopic(t) }) }
        }

        if (items.isNotEmpty() && feed.loading && pager.settledPage >= items.size - 1) {
            CircularProgressIndicator(
                color = c.primary, strokeWidth = 3.dp,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp).size(28.dp)
            )
        }
    }
}

@Composable
private fun ShortPage(v: Video, isCurrent: Boolean, onOpenVideo: (Video) -> Unit) {
    val c = Luc.colors
    val context = LocalContext.current
    val state by ShortsPlayer.state.collectAsStateWithLifecycle()
    val favorites by Library.favorites.collectAsStateWithLifecycle()
    val channels by Library.channels.collectAsStateWithLifecycle()
    val key = remember(v.url) { videoKey(v.url) }
    val showing = isCurrent && state.current == key
    val liked = favorites.any { videoKey(it.video.url) == key }
    val following = remember(channels, v.channelUrl) { Library.isFollowing(v.channelUrl) }
    var flash by remember { mutableIntStateOf(0) }
    var heart by remember { mutableIntStateOf(0) }
    var progress by remember { mutableFloatStateOf(0f) }
    // Gesture handlers outlive recompositions: read the latest values through these.
    val showingNow by rememberUpdatedState(showing)
    val likedNow by rememberUpdatedState(liked)

    LaunchedEffect(showing) {
        progress = 0f
        while (showing) {
            val p = ShortsPlayer.player(context)
            val d = p.duration
            progress = if (d > 0) (p.currentPosition.toFloat() / d).coerceIn(0f, 1f) else 0f
            delay(200)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(v.url) {
                detectTapGestures(
                    onTap = { if (showingNow) { ShortsPlayer.togglePlay(); flash++ } },
                    onDoubleTap = { if (!likedNow) Library.toggleFavorite(v); heart++ }
                )
            }
    ) {
        AsyncImage(
            model = v.thumbnail, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        if (showing && state.error == null) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        keepScreenOn = true
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        player = ShortsPlayer.player(ctx)
                    }
                },
                onRelease = { it.player = null },
                modifier = Modifier.fillMaxSize()
            )
        }
        if (isCurrent && state.current == key && state.loading) {
            CircularProgressIndicator(
                color = c.primary, strokeWidth = 3.dp,
                modifier = Modifier.align(Alignment.Center).size(40.dp)
            )
        }
        if (isCurrent && state.current == key && state.error != null) {
            Column(
                Modifier.align(Alignment.Center).background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(14.dp)).padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(errorText(state.error), color = Color.White, fontSize = 13.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(10.dp))
                PillButton("Thử lại", LucIcons.Refresh, { ShortsPlayer.retry(context, v) })
                Text("hoặc vuốt lên để xem video khác", color = Color.White.copy(alpha = 0.7f), fontSize = 11.5.sp,
                    modifier = Modifier.padding(top = 8.dp))
            }
        }
        // Play / pause flash in the middle.
        AnimatedVisibility(
            visible = flash > 0 && showing,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                Modifier.size(72.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (state.isPlaying) LucIcons.Play else LucIcons.Pause, null,
                    tint = Color.White, modifier = Modifier.size(38.dp)
                )
            }
        }
        LaunchedEffect(flash) {
            if (flash > 0) {
                delay(700)
                flash = 0
            }
        }
        // Paused: a steady play icon so it doesn't look stuck.
        if (showing && flash == 0 && !state.isPlaying && !state.loading && state.error == null) {
            Box(
                Modifier.align(Alignment.Center).size(72.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center
            ) { Icon(LucIcons.Play, "Đang tạm dừng", tint = Color.White, modifier = Modifier.size(38.dp)) }
        }
        // Double tap: a heart pops up.
        AnimatedVisibility(
            visible = heart > 0,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Icon(LucIcons.Heart, null, tint = c.primary, modifier = Modifier.size(96.dp))
        }
        LaunchedEffect(heart) {
            if (heart > 0) {
                delay(800)
                heart = 0
            }
        }

        // Bottom: title and channel
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))))
                .padding(start = 14.dp, end = 80.dp, top = 40.dp, bottom = 16.dp)
        ) {
            Text(
                "@" + v.channel.ifBlank { "YouTube" }, color = Color.White, fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(
                v.title, color = Color.White, fontSize = 13.sp, lineHeight = 18.sp,
                maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp)
            )
            if (v.views > 0) {
                Text(Format.views(v.views), color = Color.White.copy(alpha = 0.7f), fontSize = 11.5.sp, modifier = Modifier.padding(top = 2.dp))
            }
        }

        // Right: actions
        Column(
            Modifier.align(Alignment.BottomEnd).padding(end = 10.dp, bottom = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SideAction(if (liked) LucIcons.Heart else LucIcons.HeartOutline, if (liked) "Đã thích" else "Thích", if (liked) c.primary else Color.White) {
                Library.toggleFavorite(v)
            }
            val url = v.channelUrl
            if (url != null && v.channel.isNotBlank()) {
                SideAction(if (following) LucIcons.Check else LucIcons.PersonAdd, if (following) "Đã theo dõi" else "Theo dõi",
                    if (following) c.primary else Color.White) {
                    Library.toggleFollow(url, v.channel, null)
                }
            }
            SideAction(LucIcons.Share, "Chia sẻ", Color.White) {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, v.url)
                context.startActivity(Intent.createChooser(send, "Chia sẻ video"))
            }
            SideAction(LucIcons.Fullscreen, "Mở video", Color.White) {
                ShortsPlayer.pause()
                onOpenVideo(v)
            }
        }

        // Thin progress line at the very bottom.
        if (showing) {
            Box(
                Modifier.align(Alignment.BottomStart).fillMaxWidth(progress).height(2.dp).background(c.primary)
            )
        }
    }
}

@Composable
private fun SideAction(icon: ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(46.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.35f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, label, tint = tint, modifier = Modifier.size(26.dp))
        }
        Text(label, color = Color.White, fontSize = 10.5.sp, modifier = Modifier.padding(top = 3.dp).width(64.dp), textAlign = TextAlign.Center, maxLines = 1)
    }
}
