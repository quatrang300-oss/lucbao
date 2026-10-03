@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package vn.lucbao.player

import android.content.Context
import android.net.ConnectivityManager
import android.net.Uri
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vn.lucbao.api.ErrorKind
import vn.lucbao.api.VideoDetails
import vn.lucbao.data.Library
import vn.lucbao.data.Prefs
import vn.lucbao.data.Video
import vn.lucbao.data.toVideo
import vn.lucbao.data.videoKey
import vn.lucbao.engine.EngineManager
import vn.lucbao.update.Updater

data class PlayerUi(
    val video: Video? = null,
    val details: VideoDetails? = null,
    val loading: Boolean = false,
    /** one of [ErrorKind], null when fine */
    val error: Int? = null,
    val choices: List<QualityChoice> = emptyList(),
    val quality: QualityChoice? = null,
    val speed: Float = 1f,
    val isPlaying: Boolean = false,
    val related: List<Video> = emptyList(),
    val favorite: Boolean = false,
)

/** Single app-wide player shared by the UI, the media notification and picture-in-picture. */
object PlayerController {
    private const val TAG = "PlayerController"

    private lateinit var app: Context
    lateinit var exo: ExoPlayer
        private set

    /** Player given to the media session: adds next/previous for the notification & headset. */
    lateinit var sessionPlayer: Player
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _ui = MutableStateFlow(PlayerUi())
    val ui: StateFlow<PlayerUi> = _ui.asStateFlow()

    private var loadJob: Job? = null
    private val backStack = ArrayDeque<Video>()
    private var retried = false

    /** Quality picked by hand in this session (applies to the next videos too). */
    private var manualMaxP: Int? = null

    /** Safe to read from any thread. */
    val isPlayingNow: Boolean get() = _ui.value.isPlaying

    fun init(context: Context) {
        if (::exo.isInitialized) return
        app = context.applicationContext
        exo = ExoPlayer.Builder(app)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
        exo.addListener(listener)

        sessionPlayer = object : ForwardingPlayer(exo) {
            private val extra = intArrayOf(
                Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM
            )

            override fun getAvailableCommands(): Player.Commands =
                super.getAvailableCommands().buildUpon().addAll(*extra).build()

            override fun isCommandAvailable(command: Int): Boolean =
                command in extra || super.isCommandAvailable(command)

            override fun seekToNext() = this@PlayerController.next()
            override fun seekToNextMediaItem() = this@PlayerController.next()
            override fun seekToPrevious() = this@PlayerController.previous()
            override fun seekToPreviousMediaItem() = this@PlayerController.previous()
        }

        scope.launch {
            while (true) {
                delay(5_000)
                if (exo.isPlaying) savePosition()
            }
        }
    }

    // ------------------------------------------------------------------ public actions

    fun play(video: Video, rememberCurrent: Boolean = true) {
        val current = _ui.value
        if (current.video != null && videoKey(current.video.url) == videoKey(video.url) &&
            current.details != null && current.error == null
        ) {
            exo.play()
            return
        }
        if (rememberCurrent && current.video != null) {
            backStack.addLast(current.video)
            while (backStack.size > 50) backStack.removeFirst()
        }
        savePosition()
        exo.stop()
        exo.clearMediaItems()
        retried = false
        _ui.value = PlayerUi(
            video = video, loading = true, favorite = Library.isFavorite(video.url),
            speed = current.speed
        )
        Library.recordWatch(video)
        var resume = Library.positionOf(video.url)
        if (video.live || (video.duration > 0 && resume > video.duration * 1000 - 15_000)) resume = 0
        startLoad(video, resume, null)
    }

    fun retry() {
        val v = _ui.value.video ?: return
        retried = false
        _ui.update { it.copy(loading = true, error = null) }
        startLoad(v, exo.currentPosition.coerceAtLeast(0), _ui.value.quality)
    }

    fun setQuality(choice: QualityChoice) {
        manualMaxP = if (choice.audioOnly) Quality.AUDIO_ONLY else choice.p
        val details = _ui.value.details ?: return
        val position = exo.currentPosition
        val wasPlaying = exo.playWhenReady
        _ui.update { it.copy(quality = choice) }
        loadJob?.cancel()
        loadJob = scope.launch {
            try {
                val audio = Quality.bestAudio(details.audioOptions)
                val pb = withContext(Dispatchers.IO) {
                    EngineManager.get().resolve(details.url, choice.video?.id, audio?.id)
                }
                exo.setMediaSource(MediaSources.build(pb, mediaItemFor(details)), position)
                exo.prepare()
                exo.playWhenReady = wasPlaying
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                fail(t)
            }
        }
    }

    fun setSpeed(speed: Float) {
        exo.playbackParameters = PlaybackParameters(speed)
        _ui.update { it.copy(speed = speed) }
    }

    fun toggleFavorite() {
        val v = _ui.value.video ?: return
        Library.toggleFavorite(v)
        _ui.update { it.copy(favorite = Library.isFavorite(v.url)) }
    }

    fun togglePlay() {
        if (exo.playbackState == Player.STATE_ENDED) exo.seekTo(0)
        if (exo.isPlaying) exo.pause() else exo.play()
    }

    fun next() {
        val n = upNext() ?: return
        play(n)
    }

    fun previous() {
        if (exo.currentPosition > 5_000) {
            exo.seekTo(0)
            return
        }
        val prev = backStack.removeLastOrNull()
        if (prev != null) play(prev, rememberCurrent = false) else exo.seekTo(0)
    }

    fun stop() {
        savePosition()
        loadJob?.cancel()
        exo.stop()
        exo.clearMediaItems()
        backStack.clear()
        _ui.value = PlayerUi(speed = _ui.value.speed)
    }

    fun upNext(): Video? {
        val recent = Library.history.value.take(40).map { videoKey(it.video.url) }.toSet()
        val related = _ui.value.related
        return related.firstOrNull { videoKey(it.url) !in recent } ?: related.firstOrNull()
    }

    // ------------------------------------------------------------------ loading

    private fun startLoad(video: Video, startMs: Long, forced: QualityChoice?) {
        loadJob?.cancel()
        loadJob = scope.launch {
            try {
                val details = withContext(Dispatchers.IO) { EngineManager.get().details(video.url) }
                val full = details.toVideo()
                Library.recordWatch(full)
                val choices = Quality.choices(details.videoOptions, details.audioOptions.isNotEmpty())
                val choice = forced?.let { f -> choices.firstOrNull { it.label == f.label } }
                    ?: Quality.pick(choices, preferredMaxP())
                val audio = Quality.bestAudio(details.audioOptions)
                val pb = withContext(Dispatchers.IO) {
                    EngineManager.get().resolve(details.url, choice?.video?.id, audio?.id)
                }
                exo.setMediaSource(MediaSources.build(pb, mediaItemFor(details)), startMs)
                exo.playbackParameters = PlaybackParameters(_ui.value.speed)
                exo.prepare()
                exo.playWhenReady = true
                _ui.update {
                    it.copy(
                        video = full,
                        details = details,
                        loading = false,
                        error = null,
                        choices = if (details.live) emptyList() else choices,
                        quality = if (details.live) null else choice,
                        related = details.related.map { r -> r.toVideo() }
                            .distinctBy { r -> videoKey(r.url) },
                    )
                }
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                fail(t)
            }
        }
    }

    private fun fail(t: Throwable) {
        Log.e(TAG, "Playback failed", t)
        val kind = EngineManager.classify(t)
        _ui.update { it.copy(loading = false, error = kind) }
        if (kind == ErrorKind.BROKEN || kind == ErrorKind.UNKNOWN) {
            Updater.requestEngineCheck(app)
        }
    }

    private fun preferredMaxP(): Int {
        manualMaxP?.let { return it }
        val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val s = Prefs.current
        return if (cm.isActiveNetworkMetered) s.qualityMobile else s.qualityWifi
    }

    private fun mediaItemFor(d: VideoDetails): MediaItem {
        val meta = MediaMetadata.Builder()
            .setTitle(d.title)
            .setArtist(d.channel)
            .apply { d.thumbnail?.let { setArtworkUri(Uri.parse(it)) } }
            .build()
        return MediaItem.Builder()
            .setMediaId(d.url)
            .setMediaMetadata(meta)
            .build()
    }

    private fun savePosition() {
        val v = _ui.value.video ?: return
        if (!::exo.isInitialized || v.live) return
        val pos = exo.currentPosition
        if (pos > 0) Library.updatePosition(v.url, pos)
    }

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _ui.update { it.copy(isPlaying = isPlaying) }
            if (!isPlaying) savePosition()
        }

        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_ENDED) {
                savePosition()
                if (Prefs.current.autoplayNext) next()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e(TAG, "Player error ${error.errorCodeName}", error)
            val video = _ui.value.video ?: return
            val cause = error.cause
            val http = cause as? HttpDataSource.InvalidResponseCodeException
            val network = error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
            if (!retried && !network) {
                // Stream links expire after a few hours: fetch fresh ones once.
                retried = true
                _ui.update { it.copy(loading = true) }
                startLoad(video, exo.currentPosition.coerceAtLeast(0), _ui.value.quality)
                return
            }
            val kind = when {
                network -> ErrorKind.NETWORK
                http != null -> ErrorKind.BROKEN
                else -> ErrorKind.BROKEN
            }
            _ui.update { it.copy(loading = false, error = kind) }
            if (kind == ErrorKind.BROKEN) Updater.requestEngineCheck(app)
        }
    }
}
