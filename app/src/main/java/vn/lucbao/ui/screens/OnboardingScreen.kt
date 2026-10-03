package vn.lucbao.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import vn.lucbao.ui.components.PillButton
import vn.lucbao.ui.theme.LeafLogo
import vn.lucbao.ui.theme.Luc
import vn.lucbao.ui.theme.LucIcons
import vn.lucbao.ui.theme.Playfair

/** Shown once: explains the app and asks for the two permissions that make it hands-free. */
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val c = Luc.colors
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }
    val installOk = remember(refresh) { canAutoUpdate(context) }
    val notifOk = remember(refresh) { hasNotificationPermission(context) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(c.surface2, c.surface)))
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(40.dp))
        LeafLogo(84.dp)
        Spacer(Modifier.height(14.dp))
        Row {
            Text("Lục ", fontFamily = Playfair, fontWeight = FontWeight.SemiBold, fontSize = 38.sp, color = c.text)
            Text("Bảo", fontFamily = Playfair, fontWeight = FontWeight.SemiBold, fontStyle = FontStyle.Italic, fontSize = 38.sp, color = c.primary)
        }
        Text(
            "Xem YouTube trọn vẹn — không một quảng cáo.",
            color = c.muted, fontSize = 14.sp, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(26.dp))
        Feature(LucIcons.Shield, "Không quảng cáo", "Kể cả lúc mở app, tạm dừng hay tìm kiếm.")
        Feature(LucIcons.Headphones, "Nghe nền & tắt màn hình", "Nhạc vẫn chạy khi bạn làm việc khác.")
        Feature(LucIcons.Pip, "Cửa sổ nổi", "Vừa xem vừa nhắn tin.")
        Feature(LucIcons.Refresh, "Tự cập nhật", "Cài một lần, sau này không phải làm gì.")

        Spacer(Modifier.height(18.dp))
        Text("Bật 2 thứ này một lần:", color = c.text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
            modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        Step(
            done = installOk,
            title = "Cho phép tự cập nhật",
            body = "Mở cài đặt → bật “Cho phép từ nguồn này” → quay lại."
        ) { openInstallPermission(context) }
        if (Build.VERSION.SDK_INT >= 33) {
            Step(
                done = notifOk,
                title = "Cho phép thông báo",
                body = "Để điều khiển nhạc trên màn hình khoá."
            ) { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
        }
        Spacer(Modifier.height(22.dp))
        PillButton("Bắt đầu xem", LucIcons.Play, onDone, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun Feature(icon: ImageVector, title: String, body: String) {
    val c = Luc.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = c.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, color = c.text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(body, color = c.muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun Step(done: Boolean, title: String, body: String, onClick: () -> Unit) {
    val c = Luc.colors
    Row(
        Modifier
            .padding(vertical = 5.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(c.card)
            .border(1.dp, if (done) c.primary.copy(alpha = 0.5f) else c.line, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = c.text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(body, color = c.muted, fontSize = 12.sp, lineHeight = 16.sp)
        }
        if (done) {
            Icon(LucIcons.Check, "Xong", tint = c.primary, modifier = Modifier.size(24.dp))
        } else {
            PillButton("Bật", null, onClick)
        }
    }
}
