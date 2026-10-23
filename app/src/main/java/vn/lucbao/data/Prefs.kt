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
    /** 0 = off, 1 = Vietnamese subtitles, 2 = subtitles + voice-over, 3 = voice-over only */
    val vietnamese: Int = 0,
    /** which tabs show in the bottom bar (Trang chủ always does) */
    val tabShorts: Boolean = true,
    val tabSearch: Boolean = true,
    val tabMusic: Boolean = true,
    val tabLibrary: Boolean = true,
    /** SponsorBlock: which parts of a video to skip by themselves */
    val sbSponsor: Boolean = true,
    val sbSelfPromo: Boolean = true,
    val sbInteraction: Boolean = true,
    val sbIntroOutro: Boolean = false,
    val sbMusicOfftopic: Boolean = false,
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
            vietnamese = sp.getInt("vietnamese", 0),
            tabShorts = sp.getBoolean("tabShorts", true),
            tabSearch = sp.getBoolean("tabSearch", true),
            tabMusic = sp.getBoolean("tabMusic", true),
            tabLibrary = sp.getBoolean("tabLibrary", true),
            sbSponsor = sp.getBoolean("sbSponsor", true),
            sbSelfPromo = sp.getBoolean("sbSelfPromo", true),
            sbInteraction = sp.getBoolean("sbInteraction", true),
            sbIntroOutro = sp.getBoolean("sbIntroOutro", false),
            sbMusicOfftopic = sp.getBoolean("sbMusicOfftopic", false),
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
            .putInt("vietnamese", s.vietnamese)
            .putBoolean("tabShorts", s.tabShorts)
            .putBoolean("tabSearch", s.tabSearch)
            .putBoolean("tabMusic", s.tabMusic)
            .putBoolean("tabLibrary", s.tabLibrary)
            .putBoolean("sbSponsor", s.sbSponsor)
            .putBoolean("sbSelfPromo", s.sbSelfPromo)
            .putBoolean("sbInteraction", s.sbInteraction)
            .putBoolean("sbIntroOutro", s.sbIntroOutro)
            .putBoolean("sbMusicOfftopic", s.sbMusicOfftopic)
            .apply()
    }
}
