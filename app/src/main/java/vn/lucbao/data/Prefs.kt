package vn.lucbao.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Settings(
    /** 0 = follow system, 1 = dark, 2 = light */
    val theme: Int = 0,
    /** preferred maximum height on Wi-Fi */
    val qualityWifi: Int = 1080,
    /** preferred maximum height on mobile data */
    val qualityMobile: Int = 720,
    val backgroundPlay: Boolean = true,
    val autoPip: Boolean = true,
    val autoplayNext: Boolean = true,
    val onboarded: Boolean = false,
    /** 0 = Roboto (bundled), 1 = phone's system font, 2 = Be Vietnam Pro, 3 = Noto Sans */
    val font: Int = 0,
)

object Prefs {
    private lateinit var sp: SharedPreferences
    private val _state = MutableStateFlow(Settings())
    val state: StateFlow<Settings> = _state.asStateFlow()
    val current: Settings get() = _state.value

    fun init(context: Context) {
        sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        _state.value = Settings(
            theme = sp.getInt("theme", 0),
            qualityWifi = sp.getInt("qualityWifi", 1080),
            qualityMobile = sp.getInt("qualityMobile", 720),
            backgroundPlay = sp.getBoolean("backgroundPlay", true),
            autoPip = sp.getBoolean("autoPip", true),
            autoplayNext = sp.getBoolean("autoplayNext", true),
            onboarded = sp.getBoolean("onboarded", false),
            font = sp.getInt("font", 0),
        )
    }

    fun update(block: (Settings) -> Settings) {
        val s = block(_state.value)
        _state.value = s
        sp.edit()
            .putInt("theme", s.theme)
            .putInt("qualityWifi", s.qualityWifi)
            .putInt("qualityMobile", s.qualityMobile)
            .putBoolean("backgroundPlay", s.backgroundPlay)
            .putBoolean("autoPip", s.autoPip)
            .putBoolean("autoplayNext", s.autoplayNext)
            .putBoolean("onboarded", s.onboarded)
            .putInt("font", s.font)
            .apply()
    }
}
