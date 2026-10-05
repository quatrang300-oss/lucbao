package vn.lucbao.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import vn.lucbao.api.VideoDetails
import vn.lucbao.download.DownloadChoice
import vn.lucbao.download.DownloadChoices
import vn.lucbao.download.Downloads
import vn.lucbao.ui.theme.Luc
import vn.lucbao.ui.theme.LucIcons

fun sizeText(bytes: Long): String = when {
    bytes <= 0 -> ""
    bytes < 1024L * 1024 -> "${bytes / 1024} KB"
    bytes < 1024L * 1024 * 1024 -> "~${bytes / (1024 * 1024)} MB"
    else -> String.format(java.util.Locale.ROOT, "~%.1f GB", bytes / (1024.0 * 1024 * 1024)).replace('.', ',')
}

/** Lets the user pick a resolution (or audio only) and starts the download. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadSheet(details: VideoDetails, onDismiss: () -> Unit) {
    val c = Luc.colors
    val context = LocalContext.current
    val choices = remember(details) { DownloadChoices.of(details) }
    var pending by remember { mutableStateOf<DownloadChoice?>(null) }

    fun start(choice: DownloadChoice) {
        Downloads.enqueue(context, details, choice)
        Toast.makeText(context, "Đang tải về… Xem ở Thư viện › Đã tải", Toast.LENGTH_LONG).show()
        onDismiss()
    }

    val storagePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        val p = pending
        if (ok && p != null) start(p)
        else if (!ok) Toast.makeText(context, "Cần quyền lưu trữ để lưu video", Toast.LENGTH_LONG).show()
    }

    fun pick(choice: DownloadChoice) {
        val needsPermission = Build.VERSION.SDK_INT < 29 && ContextCompat.checkSelfPermission(
            context, Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            pending = choice
            storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            start(choice)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.card
    ) {
        Text(
            "Tải về máy", color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 22.dp)
        )
        Text(
            "Video lưu vào thư mục Movies/LucBao, nhạc lưu vào Music/LucBao.",
            color = c.muted, fontSize = 11.5.sp, modifier = Modifier.padding(horizontal = 22.dp, vertical = 4.dp)
        )
        if (choices.isEmpty()) {
            Text(
                "Video này không tải được (ví dụ: đang phát trực tiếp).",
                color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(22.dp)
            )
        }
        LazyColumn(Modifier.heightIn(max = 520.dp)) {
            items(choices, key = { it.key }) { ch ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { pick(ch) }
                        .padding(horizontal = 22.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (ch.video == null) LucIcons.Headphones else LucIcons.Download, null,
                        tint = c.primary, modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(14.dp))
                    Text(ch.label, color = c.text, fontSize = 14.sp)
                    ch.badge?.let {
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
                    Column(horizontalAlignment = Alignment.End) {
                        Text(ch.format, color = c.muted, fontSize = 11.sp)
                        val size = sizeText(ch.bytes)
                        if (size.isNotEmpty()) Text(size, color = c.muted, fontSize = 11.sp)
                    }
                }
            }
            item(key = "pad") { Spacer(Modifier.height(24.dp)) }
        }
    }
}
