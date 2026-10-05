package vn.lucbao.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vn.lucbao.BuildConfig
import vn.lucbao.data.Prefs
import vn.lucbao.engine.EngineManager
import vn.lucbao.player.Quality
import vn.lucbao.ui.components.LucChip
import vn.lucbao.ui.components.PillButton
import vn.lucbao.ui.theme.Luc
import vn.lucbao.ui.theme.LucIcons
import vn.lucbao.update.Updater
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun canAutoUpdate(context: Context): Boolean =
    context.packageManager.canRequestPackageInstalls()

fun hasNotificationPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
        context, Manifest.permission.POST_NOTIFICATIONS
    ) == PackageManager.PERMISSION_GRANTED

fun openInstallPermission(context: Context) {
    runCatching {
        context.startActivity(
            Intent(
                AndroidSettings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen() {
    val c = Luc.colors
    val context = LocalContext.current
    val s by Prefs.state.collectAsStateWithLifecycle()
    val engine by EngineManager.loaded.collectAsStateWithLifecycle()
    val lastCheck by Updater.lastCheck.collectAsStateWithLifecycle()
    val status by Updater.status.collectAsStateWithLifecycle()
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }
    val installOk = remember(refresh) { canAutoUpdate(context) }
    val notifOk = remember(refresh) { hasNotificationPermission(context) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(bottom = 24.dp)
    ) {
        Text(
            "Cài đặt", fontWeight = FontWeight.SemiBold, fontSize = 24.sp,
            color = c.text, modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 8.dp)
        )

        if (!installOk || !notifOk) {
            Card("Cần bật một lần") {
                if (!installOk) {
                    Body("Cho phép Lục Bảo tự cài bản cập nhật, để sau này bạn không phải làm gì.")
                    PillButton("Cho phép tự cập nhật", LucIcons.Shield, { openInstallPermission(context) },
                        modifier = Modifier.padding(top = 8.dp, bottom = 6.dp))
                }
                if (!notifOk && Build.VERSION.SDK_INT >= 33) {
                    Body("Bật thông báo để điều khiển nhạc khi tắt màn hình.")
                    PillButton("Bật thông báo", null, {
                        notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }

        Card("Giao diện") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf(0 to "Theo máy", 1 to "Tối · Rừng đêm", 2 to "Sáng · Lá non").forEach { (v, label) ->
                    LucChip(label, s.theme == v, onClick = { Prefs.update { it.copy(theme = v) } })
                }
            }
        }

        Card("Chất lượng mặc định") {
            Label("Khi dùng Wi-Fi")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf(2160 to "4K", 1440 to "1440p", 1080 to "1080p", 720 to "720p", 480 to "480p").forEach { (v, label) ->
                    LucChip(label, s.qualityWifi == v, onClick = { Prefs.update { it.copy(qualityWifi = v) } })
                }
            }
            Spacer(Modifier.height(12.dp))
            Label("Khi dùng 4G / 5G")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf(1080 to "1080p", 720 to "720p", 480 to "480p", 360 to "360p", Quality.AUDIO_ONLY to "Chỉ âm thanh").forEach { (v, label) ->
                    LucChip(label, s.qualityMobile == v, onClick = { Prefs.update { it.copy(qualityMobile = v) } })
                }
            }
            Body("Nếu video không có mức đã chọn, Lục Bảo dùng mức cao nhất thấp hơn. Máy không giải mã được 4K sẽ tự bỏ qua mức đó.")
        }

        Card("Phát video") {
            Toggle("Nghe tiếp khi thoát app / tắt màn hình", s.backgroundPlay) { v -> Prefs.update { it.copy(backgroundPlay = v) } }
            Toggle("Tự thu nhỏ thành cửa sổ nổi khi về màn hình chính", s.autoPip) { v -> Prefs.update { it.copy(autoPip = v) } }
            Toggle("Tự phát video tiếp theo", s.autoplayNext) { v -> Prefs.update { it.copy(autoplayNext = v) } }
        }

        Card("Cập nhật tự động") {
            Body("Lục Bảo tự cập nhật — bạn không cần làm gì. Khi YouTube thay đổi, bộ phát mới được tải về và dùng ngay, không cần cài lại.")
            Spacer(Modifier.height(8.dp))
            Info("Ứng dụng", "v${BuildConfig.VERSION_NAME}")
            Info("Bộ phát YouTube", if (engine.version > 0) "#${engine.version} · ${engine.description}" else "đang nạp…")
            Info(
                "Kiểm tra lần cuối",
                if (lastCheck > 0) SimpleDateFormat("HH:mm dd/MM/yyyy", Locale.ROOT).format(Date(lastCheck)) else "chưa"
            )
            if (status.isNotEmpty()) Body(status)
            if (Updater.enabled) {
                PillButton("Kiểm tra ngay", LucIcons.Refresh, {
                    Updater.requestEngineCheck(context, minIntervalMs = 0)
                }, filled = false, modifier = Modifier.padding(top = 10.dp))
            } else {
                Body("(Bản dựng thử nghiệm: chưa gắn kho cập nhật.)")
            }
        }

    }
}

@Composable
private fun Card(title: String, content: @Composable ColumnScope.() -> Unit) {
    val c = Luc.colors
    Column(
        Modifier
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(c.surface2)
            .border(1.dp, c.line, RoundedCornerShape(18.dp))
            .padding(16.dp)
    ) {
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = c.text)
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun Label(text: String) {
    Text(text, color = Luc.colors.muted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 7.dp))
}

@Composable
private fun Body(text: String) {
    Text(text, color = Luc.colors.muted, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 6.dp))
}

@Composable
private fun Info(label: String, value: String) {
    val c = Luc.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, color = c.muted, fontSize = 12.5.sp, modifier = Modifier.weight(0.42f))
        Text(value, color = c.text, fontSize = 12.5.sp, modifier = Modifier.weight(0.58f))
    }
}

@Composable
private fun Toggle(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    val c = Luc.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!value) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = c.text, fontSize = 13.5.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = value, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = c.onPrimary, checkedTrackColor = c.primary,
                uncheckedTrackColor = c.card, uncheckedBorderColor = c.line
            )
        )
    }
}
