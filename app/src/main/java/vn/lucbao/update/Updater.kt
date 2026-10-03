package vn.lucbao.update

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import vn.lucbao.BuildConfig
import vn.lucbao.api.Engine
import vn.lucbao.engine.EngineManager
import vn.lucbao.player.PlayerController
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Keeps Lục Bảo up to date without anyone doing anything:
 *  - the YouTube engine is downloaded and hot-swapped silently;
 *  - app updates are downloaded and handed to Android's installer.
 * Files come from the GitHub releases "engine-latest" and "app-latest" of [BuildConfig.UPDATE_REPO]
 * and must be signed with the same key as the installed app.
 */
object Updater {
    private const val TAG = "Updater"
    private const val PERIODIC = "lucbao-periodic-update"
    private const val NOW = "lucbao-engine-now"

    private val _lastCheck = MutableStateFlow(0L)
    val lastCheck: StateFlow<Long> = _lastCheck.asStateFlow()

    private val _status = MutableStateFlow("")
    val status: StateFlow<String> = _status.asStateFlow()

    val enabled: Boolean get() = BuildConfig.UPDATE_REPO.isNotBlank()

    private fun base(file: String, tag: String) =
        "https://github.com/${BuildConfig.UPDATE_REPO}/releases/download/$tag/$file"

    fun init(context: Context) {
        _lastCheck.value = prefs(context).getLong("lastCheck", 0)
    }

    /** Periodic background check (every 6 hours, any network). */
    fun schedule(context: Context) {
        if (!enabled) return
        val request = PeriodicWorkRequestBuilder<UpdateWorker>(6, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Check right away (app start, or after YouTube broke something). Rate limited. */
    fun requestEngineCheck(context: Context, minIntervalMs: Long = 10 * 60_000L) {
        if (!enabled) return
        val p = prefs(context)
        val now = System.currentTimeMillis()
        if (now - p.getLong("lastRequest", 0) < minIntervalMs) return
        p.edit().putLong("lastRequest", now).apply()
        val request = OneTimeWorkRequestBuilder<UpdateWorker>()
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.KEEP, request)
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences("updater", Context.MODE_PRIVATE)

    /** Runs both checks. Called from [UpdateWorker] on a background thread. */
    fun runChecks(context: Context) {
        if (!enabled) return
        try {
            checkEngine(context)
        } catch (t: Throwable) {
            Log.w(TAG, "Engine check failed", t)
        }
        try {
            checkApp(context)
        } catch (t: Throwable) {
            Log.w(TAG, "App check failed", t)
        }
        val now = System.currentTimeMillis()
        prefs(context).edit().putLong("lastCheck", now).apply()
        _lastCheck.value = now
    }

    // ------------------------------------------------------------------ engine

    private fun checkEngine(context: Context) {
        val info = JSONObject(httpText(base("engine.json", "engine-latest")))
        val version = info.getInt("version")
        val api = info.optInt("api", 1)
        if (api != Engine.API_VERSION) return
        if (version <= EngineManager.bestAvailableVersion()) return

        _status.value = "Đang tải bộ phát YouTube mới…"
        val tmp = File(EngineManager.downloadDir, "download.tmp")
        download(base(info.optString("file", "engine.apk"), "engine-latest"), tmp)
        verifySha256(tmp, info.optString("sha256"))
        verifySignature(context, tmp, expectedPackage = null)

        val target = EngineManager.fileFor(version)
        if (target.exists()) {
            target.setWritable(true)
            target.delete()
        }
        check(tmp.renameTo(target)) { "rename failed" }
        target.setReadOnly()
        EngineManager.onEngineDownloaded(version)
        _status.value = ""
        Log.i(TAG, "Engine updated to $version")
    }

    // ------------------------------------------------------------------ app

    private fun checkApp(context: Context) {
        val info = JSONObject(httpText(base("app.json", "app-latest")))
        val versionCode = info.getLong("versionCode")
        if (versionCode <= currentVersionCode(context)) return

        val dir = File(context.filesDir, "updates").apply { mkdirs() }
        val apk = File(dir, "LucBao-$versionCode.apk")
        if (!apk.exists()) {
            dir.listFiles()?.forEach { it.delete() }
            _status.value = "Đang tải bản cập nhật Lục Bảo…"
            val tmp = File(dir, "download.tmp")
            download(base(info.optString("file", "LucBao.apk"), "app-latest"), tmp)
            verifySha256(tmp, info.optString("sha256"))
            verifySignature(context, tmp, expectedPackage = context.packageName)
            check(tmp.renameTo(apk)) { "rename failed" }
            _status.value = ""
        }
        // Never interrupt someone who is watching: retry at the next check.
        if (PlayerController.isPlayingNow) return
        AppInstaller.install(context, apk)
    }

    @Suppress("DEPRECATION")
    private fun currentVersionCode(context: Context): Long {
        val pi = context.packageManager.getPackageInfo(context.packageName, 0)
        return if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else pi.versionCode.toLong()
    }

    // ------------------------------------------------------------------ helpers

    private fun open(url: String): HttpURLConnection {
        var current = URL(url)
        repeat(6) {
            val c = current.openConnection() as HttpURLConnection
            c.connectTimeout = 20_000
            c.readTimeout = 60_000
            c.instanceFollowRedirects = false
            c.setRequestProperty("User-Agent", "LucBao-Updater")
            c.setRequestProperty("Cache-Control", "no-cache")
            val code = c.responseCode
            if (code in 300..399) {
                val location = c.getHeaderField("Location") ?: error("Redirect without location")
                current = URL(current, location)
                c.disconnect()
            } else {
                if (code != 200) {
                    c.disconnect()
                    error("HTTP $code for $current")
                }
                return c
            }
        }
        error("Too many redirects")
    }

    private fun httpText(url: String): String {
        val c = open("$url?t=${System.currentTimeMillis() / 60_000}")
        return try {
            c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    private fun download(url: String, target: File) {
        val c = open(url)
        try {
            target.delete()
            c.inputStream.use { input -> target.outputStream().use { input.copyTo(it) } }
        } finally {
            c.disconnect()
        }
    }

    private fun verifySha256(file: File, expected: String) {
        if (expected.isBlank()) return
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        val actual = md.digest().joinToString("") { "%02x".format(it) }
        check(actual.equals(expected, ignoreCase = true)) {
            file.delete()
            "Checksum mismatch"
        }
    }

    /** The downloaded APK must be signed with the same certificate as this app. */
    @SuppressLint("PackageManagerGetSignatures")
    @Suppress("DEPRECATION")
    private fun verifySignature(context: Context, file: File, expectedPackage: String?) {
        val pm = context.packageManager
        val ok = try {
            fun matches(flags: Int): Boolean? {
                val archive = pm.getPackageArchiveInfo(file.absolutePath, flags) ?: return false
                if (expectedPackage != null && archive.packageName != expectedPackage) return false
                val theirs = certs(archive, flags)
                if (theirs.isEmpty()) return null // this Android version did not report them
                return theirs == certs(pm.getPackageInfo(context.packageName, flags), flags)
            }
            val modern = if (Build.VERSION.SDK_INT >= 28) matches(PackageManager.GET_SIGNING_CERTIFICATES) else null
            modern ?: (matches(PackageManager.GET_SIGNATURES) ?: false)
        } catch (t: Throwable) {
            Log.w(TAG, "Signature check error", t)
            false
        }
        if (!ok) {
            file.delete()
            error("Signature mismatch")
        }
    }

    @Suppress("DEPRECATION")
    private fun certs(pi: PackageInfo, flags: Int): Set<String> {
        val sigs: Array<android.content.pm.Signature>? =
            if (Build.VERSION.SDK_INT >= 28 && flags == PackageManager.GET_SIGNING_CERTIFICATES) {
                val si = pi.signingInfo
                when {
                    si == null -> null
                    si.hasMultipleSigners() -> si.apkContentsSigners
                    else -> si.signingCertificateHistory
                }
            } else {
                pi.signatures
            }
        if (sigs == null) return emptySet()
        val md = MessageDigest.getInstance("SHA-256")
        return sigs.map { s -> md.digest(s.toByteArray()).joinToString("") { "%02x".format(it) } }.toSet()
    }
}
