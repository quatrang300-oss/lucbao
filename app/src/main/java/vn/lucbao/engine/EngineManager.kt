package vn.lucbao.engine

import android.content.Context
import android.util.Log
import dalvik.system.DexClassLoader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import vn.lucbao.api.Engine
import vn.lucbao.api.ErrorKind
import java.io.File
import java.util.Locale

/**
 * Loads the YouTube engine (a separate signed APK containing NewPipeExtractor) and
 * swaps it for a newer one at runtime when the updater downloads it.
 *
 * Two sources exist:
 *  - the engine bundled in the app's assets (always works offline)
 *  - a newer engine downloaded by [vn.lucbao.update.Updater] into filesDir/engine
 */
object EngineManager {
    private const val TAG = "EngineManager"
    private const val ENTRY_CLASS = "vn.lucbao.engine.EngineImpl"

    private lateinit var app: Context
    private val lock = Any()

    @Volatile
    private var engine: Engine? = null

    private val _loaded = MutableStateFlow(LoadedEngine(0, ""))

    /** Version and description of the engine currently in use. */
    val loaded: StateFlow<LoadedEngine> = _loaded.asStateFlow()

    data class LoadedEngine(val version: Int, val description: String)

    fun init(context: Context) {
        app = context.applicationContext
    }

    private val prefs get() = app.getSharedPreferences("engine", Context.MODE_PRIVATE)

    val downloadDir: File get() = File(app.filesDir, "engine").apply { mkdirs() }

    /** Returns the engine, loading it on first use. Blocking: never call on the main thread. */
    fun get(): Engine {
        engine?.let { return it }
        synchronized(lock) {
            engine?.let { return it }
            val e = loadBest()
            engine = e
            return e
        }
    }

    /** Version of the newest engine available on this phone (bundled or downloaded). */
    fun bestAvailableVersion(): Int = maxOf(bundledVersion(), downloadedVersion())

    fun downloadedVersion(): Int {
        val v = prefs.getInt("downloaded", 0)
        return if (v > 0 && v != prefs.getInt("bad", -1) && fileFor(v).exists()) v else 0
    }

    fun fileFor(version: Int) = File(downloadDir, "engine-$version.apk")

    /** Called by the updater after it stored and verified a new engine file. */
    fun onEngineDownloaded(version: Int) {
        prefs.edit().putInt("downloaded", version).apply()
        // Remove older downloads.
        downloadDir.listFiles()?.forEach { f ->
            if (f.name.startsWith("engine-") && f.name != "engine-$version.apk") {
                f.setWritable(true)
                f.delete()
            }
        }
        // Hot swap: the next request uses the new engine.
        synchronized(lock) {
            if (version > _loaded.value.version) {
                try {
                    engine = load(fileFor(version), version)
                } catch (t: Throwable) {
                    Log.e(TAG, "New engine $version failed to load", t)
                    prefs.edit().putInt("bad", version).apply()
                }
            }
        }
    }

    fun classify(t: Throwable): Int = try {
        engine?.classifyError(t) ?: ErrorKind.UNKNOWN
    } catch (_: Throwable) {
        ErrorKind.UNKNOWN
    }

    private fun loadBest(): Engine {
        val bundled = bundledVersion()
        val downloaded = downloadedVersion()
        if (downloaded > bundled) {
            try {
                return load(fileFor(downloaded), downloaded)
            } catch (t: Throwable) {
                Log.e(TAG, "Downloaded engine $downloaded is broken, using bundled one", t)
                prefs.edit().putInt("bad", downloaded).apply()
            }
        }
        return load(extractBundled(bundled), bundled)
    }

    private fun bundledVersion(): Int = runCatching {
        app.assets.open("engine/engine.json").bufferedReader().use {
            JSONObject(it.readText()).getInt("version")
        }
    }.getOrDefault(1)

    private fun extractBundled(version: Int): File {
        val dir = File(app.codeCacheDir, "engine").apply { mkdirs() }
        val target = File(dir, "bundled-$version.apk")
        if (!target.exists()) {
            dir.listFiles()?.forEach { it.setWritable(true); it.delete() }
            val tmp = File(dir, "tmp.apk")
            app.assets.open("engine/engine.apk").use { input ->
                tmp.outputStream().use { input.copyTo(it) }
            }
            tmp.renameTo(target)
        }
        return target
    }

    private fun load(file: File, version: Int): Engine {
        // Android 14+ refuses to load writable code files.
        file.setReadOnly()
        val loader = DexClassLoader(file.absolutePath, null, null, Engine::class.java.classLoader)
        val instance = loader.loadClass(ENTRY_CLASS).getDeclaredConstructor().newInstance() as Engine
        check(instance.apiVersion() == Engine.API_VERSION) {
            "Engine api ${instance.apiVersion()} != ${Engine.API_VERSION}"
        }
        val locale = Locale.getDefault()
        val lang = locale.language.ifEmpty { "vi" }
        val country = locale.country.ifEmpty { "VN" }
        instance.init(app, lang, country)
        _loaded.value = LoadedEngine(version, instance.describe())
        Log.i(TAG, "Loaded engine $version: ${instance.describe()}")
        return instance
    }
}
