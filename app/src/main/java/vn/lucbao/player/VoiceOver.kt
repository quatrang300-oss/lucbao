package vn.lucbao.player

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Vietnamese voice-over ("thuyết minh"): reads the Vietnamese subtitles aloud with the
 * phone's own text-to-speech voice, and turns the original sound down while it speaks.
 */
object VoiceOver {
    private const val TAG = "VoiceOver"

    /** Characters per second of the Vietnamese voice at rate 1.0 (rough). */
    private const val CHARS_PER_SECOND = 13f
    private const val DUCK_VOLUME = 0.22f

    enum class State { OFF, STARTING, READY, NO_VOICE, NO_ENGINE }

    private val _state = MutableStateFlow(State.OFF)
    val state: StateFlow<State> = _state.asStateFlow()

    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var lastId: String? = null
    private var counter = 0L

    /** Starts the speech engine (once). Safe to call again after the user installed a voice. */
    fun init(context: Context, force: Boolean = false) {
        if (!force && tts != null && (_state.value == State.READY || _state.value == State.STARTING)) return
        tts?.let { runCatching { it.shutdown() } }
        tts = null
        _state.value = State.STARTING
        val app = context.applicationContext
        try {
            var created: TextToSpeech? = null
            created = TextToSpeech(app) { status -> main.post { onReady(created, status) } }
            tts = created
        } catch (t: Throwable) {
            Log.w(TAG, "No text-to-speech engine", t)
            _state.value = State.NO_ENGINE
        }
    }

    private fun onReady(engine: TextToSpeech?, status: Int) {
        val t = engine ?: tts ?: return
        if (t !== tts) return
        if (status != TextToSpeech.SUCCESS) {
            _state.value = State.NO_ENGINE
            return
        }
        val r = runCatching { t.setLanguage(Locale("vi", "VN")) }.getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
            _state.value = State.NO_VOICE
            return
        }
        t.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                main.post { if (utteranceId == lastId) duck(true) }
            }

            override fun onDone(utteranceId: String?) {
                main.post { if (utteranceId == lastId) duck(false) }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                main.post { if (utteranceId == lastId) duck(false) }
            }

            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                main.post { if (utteranceId == lastId) duck(false) }
            }
        })
        _state.value = State.READY
    }

    /**
     * Reads [text], a bit faster when the line is long for the time it is on screen,
     * so the voice keeps up with the video. Interrupts whatever was being read.
     */
    fun speak(text: String, availableMs: Long) {
        val t = tts ?: return
        if (_state.value != State.READY) return
        val seconds = (availableMs.coerceAtLeast(600)) / 1000f
        val rate = (text.length / CHARS_PER_SECOND / seconds).coerceIn(1.0f, 2.0f)
        val id = "lb${++counter}"
        lastId = id
        val ok = runCatching {
            t.setSpeechRate(rate)
            t.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) == TextToSpeech.SUCCESS
        }.getOrDefault(false)
        if (!ok) {
            lastId = null
            duck(false)
            return
        }
        // Safety net: never leave the original sound turned down if a callback goes missing.
        main.postDelayed({ if (lastId == id) duck(false) }, (seconds * 1000).toLong() + 4_000)
    }

    /** Releases the speech engine (voice-over turned off). */
    fun shutdown() {
        stop()
        tts?.let { runCatching { it.shutdown() } }
        tts = null
        _state.value = State.OFF
    }

    fun stop() {
        lastId = null
        tts?.let { runCatching { it.stop() } }
        duck(false)
    }

    private fun duck(on: Boolean) {
        runCatching { PlayerController.exo.volume = if (on) DUCK_VOLUME else 1f }
    }

    /** Opens the phone's text-to-speech settings so a Vietnamese voice can be installed. */
    fun openVoiceSettings(context: Context) {
        val intents = listOf(
            Intent("com.android.settings.TTS_SETTINGS"),
            Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA),
        )
        for (i in intents) {
            val ok = runCatching {
                context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.isSuccess
            if (ok) return
        }
    }
}
