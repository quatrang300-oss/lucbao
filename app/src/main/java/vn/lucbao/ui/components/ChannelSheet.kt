package vn.lucbao.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import vn.lucbao.data.Library
import vn.lucbao.data.Video
import vn.lucbao.data.toVideo
import vn.lucbao.engine.EngineManager
import vn.lucbao.ui.theme.Luc
import vn.lucbao.ui.theme.LucIcons

/** "Theo dõi" / "Đang theo dõi" — saved on this phone only, no account needed. */
@Composable
fun FollowButton(url: String, name: String, avatar: String?) {
    val c = Luc.colors
    val channels by Library.channels.collectAsStateWithLifecycle()
    val on = remember(channels, url) { Library.isFollowing(url) }
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (on) Color.Transparent else c.primary)
            .border(1.dp, if (on) c.line else Color.Transparent, RoundedCornerShape(50))
            .clickable { Library.toggleFollow(url, name, avatar) }
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (on) LucIcons.Check else LucIcons.PersonAdd, null,
            tint = if (on) c.muted else c.onPrimary, modifier = Modifier.size(15.dp)
        )
        Spacer(Modifier.width(5.dp))
        Text(
            if (on) "Đang theo dõi" else "Theo dõi",
            color = if (on) c.muted else c.onPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold
        )
    }
}

/** Latest uploads of one channel, with the follow button. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelSheet(
    url: String,
    name: String,
    avatar: String?,
    onDismiss: () -> Unit,
    onPlay: (Video) -> Unit,
) {
    val c = Luc.colors
    var videos by remember(url) { mutableStateOf<List<Video>?>(null) }
    var failed by remember(url) { mutableStateOf(false) }
    LaunchedEffect(url) {
        val r = withContext(Dispatchers.IO) {
            runCatching {
                EngineManager.get().kiosk("feed:$url", null).items.map { it.toVideo() }
                    .sortedByDescending { it.publishedAt }
            }
        }
        videos = r.getOrNull()
        failed = r.isFailure
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.card
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Avatar(name, 44.dp, avatar)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    name, color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text("Video mới nhất", color = c.muted, fontSize = 11.5.sp)
            }
            FollowButton(url, name, avatar)
        }
        Spacer(Modifier.height(8.dp))
        val list = videos
        when {
            list == null && !failed -> LoadingBox()
            list.isNullOrEmpty() -> Text(
                "Chưa tải được video của kênh này. Thử lại sau nhé.",
                color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(24.dp)
            )
            else -> LazyColumn(Modifier.heightIn(max = 560.dp)) {
                itemsIndexed(list ?: emptyList(), key = { i, v -> "$i-${v.url}" }) { _, v ->
                    VideoRow(v, onClick = { onPlay(v); onDismiss() })
                }
                item(key = "pad") { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}
