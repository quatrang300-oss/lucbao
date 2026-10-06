@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package vn.lucbao.player

import android.content.Context
import android.net.ConnectivityManager
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vn.lucbao.api.Playback
import vn.lucbao.data.Prefs
import vn.lucbao.data.Video
import vn.lucbao.data.videoKey
import vn.lucbao.engine.EngineManager

data class ShortsState(
    /** videoKey of the short being shown */
    val current: String? = null,
    val loading: Boolean = false,
    /** one of ErrorKind, null when fine */
    val error: Int? = null,
    val isPlaying: Boolean = false,
)

/**
 * A second, small player just for the Shorts tab: loops one short, and resolves the next
 * ones ahead of time so swiping feels instant. Kept apart from [PlayerController] so shorts
 * never touch the queue, history or the media notification.
 */
object ShortsPlayer {
    private const val TAG = "ShortsPlayer"
    private const val CACHE = 8

    private var exo: ExoPlayer? = null
    private lateinit var app: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var playJob: Job? = null

    /** url key -> resolved playback, newest last */
    private val cache = LinkedHashMap<String, Deferred<Playback>>(16, 0.75f, true)
    private var lastVideo: Video? = null
    private var retried: String? = null

    private val _state = MutableStateFlow(ShortsState())
    val state: StateFlow<ShortsState> = _state.asStateFlow()

    fun player(context: Context): ExoPlayer {
        exo?.let { return it }
        app = context.applicationContext
        val p = ExoPlayer.Builder(app)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
                true
            )
            .build()
        p.repeatMode = Player.REPEAT_MODE_ONE
        p.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.update { it.copy(isPlaying = isPlaying) }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                Log.w(TAG, "Short failed", error)
                val key = _state.value.current ?: return
                synchronized(cache) { cache.remove(key) }
                // Stream links expire after a few hours: fetch fresh ones once.
                val v = lastVideo
                if (retried != key && v != null && videoKey(v.url) == key) {
                    retried = key
                    _state.value = ShortsState()
                    play(app, v)
                    return
                }
                _state.update { it.copy(loading = false, error = vn.lucbao.api.ErrorKind.BROKEN) }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) _state.update { it.copy(loading = false) }
            }
        })
        exo = p
        return p
    }

    /** Resolves [video] in the background so it starts instantly when swiped to. */
    fun prefetch(video: Video) {
        if (exo == null) return
        load(video)
    }

    /** Shows and loops [video]. */
    fun play(context: Context, video: Video) {
        val p = player(context)
        lastVideo = video
        val key = videoKey(video.url)
        if (_state.value.current == key && _state.value.error == null) {
            p.play()
            return
        }
        playJob?.cancel()
        p.stop()
        p.clearMediaItems()
        _state.value = ShortsState(current = key, loading = true)
        // Shorts and the main player never play at the same time.
        runCatching { PlayerController.exo.pause() }
        playJob = scope.launch {
            try {
                val pb = load(video).await()
                val item = MediaItem.Builder().setMediaId(video.url).build()
                p.setMediaSource(MediaSources.build(pb, item))
                p.prepare()
                p.play()
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                Log.w(TAG, "Short failed to load", t)
                synchronized(cache) { cache.remove(key) }
                _state.update { it.copy(loading = false, error = EngineManager.classify(t)) }
            }
        }
    }

    fun togglePlay() {
        val p = exo ?: return
        if (p.isPlaying) p.pause() else p.play()
    }

    fun pause() {
        exo?.pause()
    }

    /** Frees the video decoder (tab or app left, or the main player opened). */
    fun stop() {
        playJob?.cancel()
        exo?.stop()
        exo?.clearMediaItems()
        _state.value = ShortsState()
    }

    /** Forget the current short (e.g. after an error) so the same one can be retried. */
    fun retry(context: Context, video: Video) {
        synchronized(cache) { cache.remove(videoKey(video.url)) }
        _state.value = ShortsState()
        play(context, video)
    }

    private fun load(video: Video): Deferred<Playback> {
        val key = videoKey(video.url)
        synchronized(cache) {
            cache[key]?.let { d ->
                if (!d.isCancelled) return d
            }
            val d = scope.async(Dispatchers.IO) {
                val engine = EngineManager.get()
                val details = engine.details(video.url)
                val choices = Quality.choices(details.videoOptions, details.audioOptions.isNotEmpty())
                val choice = Quality.pick(choices.filterNot { it.audioOnly }, maxP())
                val audio = Quality.bestAudio(details.audioOptions)
                engine.resolve(details.url, choice?.video?.id, audio?.id)
            }
            cache[key] = d
            val it = cache.keys.iterator()
            while (cache.size > CACHE && it.hasNext()) {
                val k = it.next()
                if (k != _state.value.current && k != key) it.remove()
            }
            return d
        }
    }

    /** Shorts are small on screen: cap at 720p on mobile data, 1080p on Wi-Fi. */
    private fun maxP(): Int {
        val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val s = Prefs.current
        return if (cm.isActiveNetworkMetered) minOf(s.qualityMobile, 720) else minOf(s.qualityWifi, 1080)
    }
}
