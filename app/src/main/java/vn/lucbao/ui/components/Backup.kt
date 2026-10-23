package vn.lucbao.ui.components

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vn.lucbao.data.Library
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** "Sao lưu" and "Khôi phục" for the library, through the phone's own file picker. */
class BackupActions(val export: () -> Unit, val import: () -> Unit)

@Composable
fun rememberBackupActions(): BackupActions {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val text = Library.exportBackup()
                    context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray()) }
                }.isSuccess
            }
            Toast.makeText(
                context,
                if (ok) "Đã sao lưu. Mở tệp này bằng \"Khôi phục\" trên máy mới." else "Chưa lưu được tệp sao lưu.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val text = context.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
                    Library.importBackup(text)
                }
            }
            val msg = result.fold(
                onSuccess = { n ->
                    "Đã khôi phục: ${n.channels} kênh, ${n.favorites} yêu thích, ${n.watchLater} xem sau, " +
                        "${n.songs} bài hát, ${n.history} video đã xem."
                },
                onFailure = { "Tệp này không phải bản sao lưu của Lục Bảo." },
            )
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }

    return BackupActions(
        export = {
            val day = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())
            runCatching { save.launch("LucBao-sao-luu-$day.json") }
        },
        import = { runCatching { open.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*")) } },
    )
}
