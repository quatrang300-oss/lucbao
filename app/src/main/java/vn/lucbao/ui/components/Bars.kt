package vn.lucbao.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import vn.lucbao.player.PlayerController
import vn.lucbao.player.PlayerUi
import vn.lucbao.ui.Tab
import vn.lucbao.ui.theme.Luc
import vn.lucbao.ui.theme.LucIcons

@Composable
fun MiniPlayer(ui: PlayerUi, onExpand: () -> Unit) {
    val c = Luc.colors
    val v = ui.video ?: return
    var progress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(v.url) {
        while (true) {
            val d = PlayerController.exo.duration
            progress = if (d > 0) PlayerController.exo.currentPosition.toFloat() / d else 0f
            delay(500)
        }
    }
    Box(
        Modifier
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(c.card)
            .border(1.dp, c.line, RoundedCornerShape(16.dp))
            .clickable(onClick = onExpand)
    ) {
        Row(Modifier.padding(7.dp), verticalAlignment = Alignment.CenterVertically) {
            Thumb(v.thumbnail, Modifier.width(62.dp), corner = 8.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    v.title.ifEmpty { "Đang tải…" }, color = c.text, fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (ui.quality?.audioOnly == true) "${v.channel} · Chỉ âm thanh" else v.channel,
                    color = c.muted, fontSize = 10.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            IconTap(if (ui.isPlaying) LucIcons.Pause else LucIcons.Play, "Phát/Tạm dừng") {
                PlayerController.togglePlay()
            }
            IconTap(LucIcons.Close, "Đóng", size = 20) { PlayerController.stop() }
        }
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(2.dp)
                .background(c.primary)
        )
    }
}

@Composable
fun IconTap(icon: ImageVector, desc: String, size: Int = 24, tint: androidx.compose.ui.graphics.Color? = null, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, desc, tint = tint ?: Luc.colors.text, modifier = Modifier.size(size.dp))
    }
}

@Composable
fun BottomNav(current: Tab, onSelect: (Tab) -> Unit) {
    val c = Luc.colors
    val items = listOf(
        Triple(Tab.HOME, LucIcons.Home, "Trang chủ"),
        Triple(Tab.SEARCH, LucIcons.Search, "Khám phá"),
        Triple(Tab.LIBRARY, LucIcons.Library, "Thư viện"),
        Triple(Tab.SETTINGS, LucIcons.Tune, "Cài đặt"),
    )
    Column(Modifier.fillMaxWidth().background(c.surface)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(top = 6.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            items.forEach { (tab, icon, label) ->
                val on = tab == current
                Column(
                    Modifier
                        .width(76.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onSelect(tab) },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        Modifier
                            .width(54.dp)
                            .height(30.dp)
                            .clip(RoundedCornerShape(50))
                            .background(if (on) c.primary.copy(alpha = 0.2f) else androidx.compose.ui.graphics.Color.Transparent),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, label, tint = if (on) c.primary else c.muted, modifier = Modifier.size(22.dp))
                    }
                    Text(
                        label, fontSize = 10.5.sp, color = if (on) c.text else c.muted,
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }
            }
        }
    }
}
