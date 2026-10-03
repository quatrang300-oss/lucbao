package vn.lucbao.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import vn.lucbao.api.ErrorKind
import vn.lucbao.data.Format
import vn.lucbao.data.Video
import vn.lucbao.ui.theme.Luc
import vn.lucbao.ui.theme.LucIcons
import vn.lucbao.ui.theme.Playfair

fun errorText(kind: Int?): String = when (kind) {
    ErrorKind.NETWORK -> "Không có kết nối mạng. Kiểm tra Wi-Fi hoặc 4G rồi thử lại."
    ErrorKind.BROKEN -> "YouTube vừa thay đổi. Lục Bảo đang tự cập nhật bản sửa — bạn không cần làm gì, thử lại sau ít phút nhé."
    ErrorKind.UNAVAILABLE -> "Video này không còn xem được."
    ErrorKind.AGE_RESTRICTED -> "Video giới hạn độ tuổi (cần đăng nhập YouTube) nên Lục Bảo chưa phát được."
    ErrorKind.GEO_BLOCKED -> "Video không xem được ở quốc gia của bạn."
    ErrorKind.PRIVATE -> "Đây là video riêng tư."
    ErrorKind.BOT_CHECK -> "YouTube đang tạm chặn vì nghi là máy tự động. Thử lại sau ít phút hoặc đổi mạng (Wi-Fi ↔ 4G)."
    ErrorKind.PAID -> "Video này yêu cầu trả phí."
    ErrorKind.NOT_STARTED_YET -> "Buổi phát trực tiếp / công chiếu chưa bắt đầu."
    else -> "Có lỗi xảy ra. Thử lại nhé."
}

@Composable
fun Thumb(
    url: String?,
    modifier: Modifier = Modifier,
    duration: Long = -1,
    live: Boolean = false,
    progress: Float? = null,
    corner: Dp = 16.dp,
) {
    val c = Luc.colors
    Box(
        modifier
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(corner))
            .background(c.card)
    ) {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        val badge = if (live) "TRỰC TIẾP" else Format.duration(duration)
        if (badge.isNotEmpty()) {
            Text(
                badge,
                color = Color.White,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(7.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(if (live) Color(0xFFD64545) else Color.Black.copy(alpha = 0.72f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
        if (progress != null && progress > 0f) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .height(3.dp)
                    .background(c.primary)
            )
        }
    }
}

@Composable
fun Avatar(name: String, size: Dp = 30.dp, image: String? = null) {
    val c = Luc.colors
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(c.primary.copy(alpha = 0.75f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            name.trim().take(1).uppercase(),
            color = c.onPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.4f).sp
        )
        if (image != null) {
            AsyncImage(
                model = image, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

fun metaLine(v: Video): String = Format.dot(v.channel, Format.views(v.views), v.uploaded)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoCard(v: Video, onClick: () -> Unit, progress: Float? = null, onLongClick: (() -> Unit)? = null) {
    val c = Luc.colors
    Column(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 7.dp)
    ) {
        Thumb(v.thumbnail, Modifier.fillMaxWidth(), v.duration, v.live, progress)
        Row(Modifier.padding(top = 9.dp)) {
            Avatar(v.channel)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    v.title, color = c.text, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp,
                    lineHeight = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                Text(
                    metaLine(v), color = c.muted, fontSize = 11.5.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoRow(v: Video, onClick: () -> Unit, progress: Float? = null, onLongClick: (() -> Unit)? = null) {
    val c = Luc.colors
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Thumb(v.thumbnail, Modifier.width(140.dp), v.duration, v.live, progress, corner = 12.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                v.title, color = c.text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                lineHeight = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            Text(v.channel, color = c.muted, fontSize = 11.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
            val sub = Format.dot(Format.views(v.views), v.uploaded)
            if (sub.isNotEmpty()) Text(sub, color = c.muted, fontSize = 11.sp, maxLines = 1)
        }
    }
}

@Composable
fun HeroCard(v: Video, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .fillMaxWidth()
            .aspectRatio(16f / 10f)
            .clip(RoundedCornerShape(22.dp))
            .background(Luc.colors.card)
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = v.thumbnail, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.35f to Color.Transparent, 1f to Color(0xE6030F0A)
                    )
                )
        )
        Column(Modifier.align(Alignment.BottomStart).padding(14.dp)) {
            Text(
                "NỔI BẬT",
                color = Color(0xFF9FF0C9), fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.8.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0x383DDC97))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                v.title, color = Color.White, fontFamily = Playfair, fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp, lineHeight = 23.sp, maxLines = 3, overflow = TextOverflow.Ellipsis
            )
            Text(
                Format.dot(v.channel, Format.views(v.views), Format.duration(v.duration)),
                color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
fun LucChip(text: String, selected: Boolean, onClick: () -> Unit, icon: ImageVector? = null) {
    val c = Luc.colors
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) c.primary else c.surface2)
            .border(1.dp, if (selected) Color.Transparent else c.line, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (selected) c.onPrimary else c.primary, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text, fontSize = 12.5.sp,
            color = if (selected) c.onPrimary else c.text,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
fun SectionTitle(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    val c = Luc.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 6.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Text(title, fontFamily = Playfair, fontWeight = FontWeight.SemiBold, fontSize = 17.sp,
            color = c.text, modifier = Modifier.weight(1f))
        if (action != null) {
            Text(action, color = c.primary, fontSize = 12.sp,
                modifier = Modifier.clickable { onAction?.invoke() }.padding(4.dp))
        }
    }
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Luc.colors.primary, strokeWidth = 3.dp, modifier = Modifier.size(30.dp))
    }
}

@Composable
fun ErrorBox(kind: Int?, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val c = Luc.colors
    Column(
        modifier.fillMaxWidth().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(errorText(kind), color = c.text, fontSize = 14.sp, textAlign = TextAlign.Center, lineHeight = 20.sp)
        PillButton("Thử lại", LucIcons.Refresh, onRetry)
    }
}

@Composable
fun PillButton(text: String, icon: ImageVector?, onClick: () -> Unit, filled: Boolean = true, modifier: Modifier = Modifier) {
    val c = Luc.colors
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(if (filled) c.primary else c.surface2)
            .border(1.dp, if (filled) Color.Transparent else c.line, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (filled) c.onPrimary else c.primary, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(7.dp))
        }
        Text(text, color = if (filled) c.onPrimary else c.text, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp)
    }
}

@Composable
fun EmptyState(title: String, body: String) {
    val c = Luc.colors
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, fontFamily = Playfair, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = c.text)
        Text(body, color = c.muted, fontSize = 13.sp, textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp), lineHeight = 19.sp)
    }
}

val ListBottomPadding = PaddingValues(bottom = 16.dp)

@Composable
fun VerticalDivider(color: Color) {
    Box(Modifier.width(1.dp).fillMaxHeight().background(color))
}
