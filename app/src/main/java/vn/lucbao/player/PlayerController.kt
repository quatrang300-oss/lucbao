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
import org.json.JSONObject
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

enum class CaptionStatus { OFF, LOADING, READY, NONE, ERROR, UNSUPPORTED }

/** One subtitle line. */
class Caption(val startMs: Long, val endMs: Long, val text: String)

/** State of the Vietnamese subtitles for the current video. */
data class CaptionInfo(
    val status: CaptionStatus = CaptionStatus.OFF,
    /** true when YouTube translated them automatically */
    val translated: Boolean = false,
    /** language tag of the video's own subtitles, e.g. "en" */
    val source: String? = null,
)

/** Songs played in order from the Music tab. Empty for ordinary videos. */
data class QueueState(
    val items: List<Video> = emptyList(),
    val index: Int = -1,
    /** played from the Music tab: audio only, shown in the music player */
    val music: Boolean = false,
    val shuffle: Boolean = false,
    /** 0 = off, 1 = repeat all, 2 = repeat one */
    val repeat: Int = 0,
    /** where the songs come from, e.g. an album name or "Radio" */
    val source: String = "",
    /** playlist to fetch more songs from when the queue runs out (radio, long playlists) */
    val moreUrl: String? = null,
    val moreToken: String? = null,
    /** order before shuffling, to restore it */
    val original: List<Video>? = null,
    /** the current song is being watched as a video ("Xem video"); next songs are music again */
    val videoOnce: Boolean = false,
) {
    /** Whether the music player (not the video player) should be shown. */
    val showMusicPlayer: Boolean get() = music && !videoOnce && items.isNotEmpty()

    val current: Video? get() = items.getOrNull(index)
}

/** Single app-wide player shared by the UI, the media notification and picture-in-picture. */
object PlayerController {
    private const val TAG = "PlayerController"

    private lateinit var app: Context
    val appContext: Context get() = app
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

    // ---- Music queue
    private val _queue = MutableStateFlow(QueueState())
    val queue: StateFlow<QueueState> = _queue.asStateFlow()
    private var shuffleOn = false
    private var repeatMode = 0
    private var moreJob: Job? = null

    /** "Lặp lại": the current video starts over when it ends. Turned off for the next video. */
    private val _loopVideo = MutableStateFlow(false)
    val loopVideo: StateFlow<Boolean> = _loopVideo.asStateFlow()

    fun setLoopVideo(on: Boolean) {
        _loopVideo.value = on
        exo.repeatMode = if (on) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    /** Sleep timer: 0 = off, -1 = after the current song, else the time to pause at. */
    private val _sleepAt = MutableStateFlow(0L)
    val sleepAt: StateFlow<Long> = _sleepAt.asStateFlow()

    // ---- Vietnamese subtitles / voice-over
    private val _captionInfo = MutableStateFlow(CaptionInfo())
    val captionInfo: StateFlow<CaptionInfo> = _captionInfo.asStateFlow()

    /** The subtitle line to show right now (null between lines or when off). */
    private val _caption = MutableStateFlow<String?>(null)
    val caption: StateFlow<String?> = _caption.asStateFlow()

    private var lines: List<Caption> = emptyList()
    private var captionsFor: String? = null
    private var captionJob: Job? = null
    /** Index of the line on screen; [NO_LINE] forces a refresh (lineAt never returns it). */
    private var lineIndex = NO_LINE
    private const val NO_LINE = -2

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
        scope.launch {
            while (true) {
                delay(150)
                tickCaptions()
                val sleep = _sleepAt.value
                if (sleep > 0 && System.currentTimeMillis() >= sleep) {
                    _sleepAt.value = 0
                    exo.pause()
                }
            }
        }
    }

    // ------------------------------------------------------------------ public actions

    /**
     * Plays one video. Called from the screens this ends any music queue; the queue itself
     * passes [keepQueue].
     */
    fun play(video: Video, rememberCurrent: Boolean = true, keepQueue: Boolean = false) {
        if (!keepQueue) {
            moreJob?.cancel()
            if (_queue.value.items.isNotEmpty()) _queue.value = QueueState(shuffle = shuffleOn, repeat = repeatMode)
        }
        val current = _ui.value
        if (current.video != null && videoKey(current.video.url) == videoKey(video.url) &&
            current.details != null && current.error == null
        ) {
            if (exo.playbackState == Player.STATE_ENDED) exo.seekTo(0)
            exo.play()
            // Same song, now from the Music tab: switch the video stream to audio only.
            val q = _queue.value
            if (q.music && !q.videoOnce && current.quality?.audioOnly != true) {
                current.choices.firstOrNull { it.audioOnly }?.let { switchQuality(it) }
            }
            return
        }
        // Looping is for one video: a different video plays normally.
        if (_loopVideo.value) setLoopVideo(false)
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
        resetCaptions()
        val music = _queue.value.music
        if (!music) Library.recordWatch(video)
        var resume = if (music) 0L else Library.positionOf(video.url)
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
        switchQuality(choice)
    }

    /** Changes the stream of the current video without changing the user's quality choice. */
    private fun switchQuality(choice: QualityChoice) {
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
        val q = _queue.value
        if (q.items.isNotEmpty()) {
            val n = nextIndex(q)
            if (n != null) {
                goTo(n)
                return
            }
            if (q.moreUrl != null && q.moreToken != null) {
                loadMoreThenNext(q.moreUrl, q.moreToken)
                return
            }
            // Out of songs: carry on with what YouTube suggests (never a song already queued).
            val have = q.items.map { videoKey(it.url) }.toHashSet()
            val r = _ui.value.related.firstOrNull { videoKey(it.url) !in have } ?: return
            _queue.update { it.copy(items = it.items + r, index = it.items.size, videoOnce = false) }
            play(r, rememberCurrent = false, keepQueue = true)
            return
        }
        val n = upNext() ?: return
        play(n)
    }

    fun previous() {
        if (exo.currentPosition > 5_000) {
            exo.seekTo(0)
            return
        }
        val q = _queue.value
        if (q.items.isNotEmpty()) {
            when {
                q.index > 0 -> goTo(q.index - 1)
                q.repeat == 1 && q.items.size > 1 -> goTo(q.items.lastIndex)
                else -> exo.seekTo(0)
            }
            return
        }
        val prev = backStack.removeLastOrNull()
        if (prev != null) play(prev, rememberCurrent = false) else exo.seekTo(0)
    }

    fun stop() {
        savePosition()
        loadJob?.cancel()
        moreJob?.cancel()
        exo.stop()
        exo.clearMediaItems()
        backStack.clear()
        resetCaptions()
        _queue.value = QueueState(shuffle = shuffleOn, repeat = repeatMode)
        _sleepAt.value = 0
        if (_loopVideo.value) setLoopVideo(false)
        _ui.value = PlayerUi(speed = _ui.value.speed)
    }

    // ------------------------------------------------------------------ music queue

    /** Short messages for the user ("Đã thêm vào hàng chờ"…), shown as toasts by the activity. */
    private val _messages = kotlinx.coroutines.flow.MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: kotlinx.coroutines.flow.SharedFlow<String> = _messages

    private fun say(text: String) {
        _messages.tryEmit(text)
    }

    /**
     * Plays [items] starting at [index] (Music tab).
     *
     * @param shuffle true/false sets the shuffle mode, null keeps the current one
     * @param moreUrl/moreToken where to fetch more songs once the list runs out
     */
    fun playQueue(
        items: List<Video>,
        index: Int,
        source: String,
        music: Boolean = true,
        shuffle: Boolean? = null,
        moreUrl: String? = null,
        moreToken: String? = null,
    ) {
        if (items.isEmpty()) return
        if (_loopVideo.value) setLoopVideo(false)
        moreJob?.cancel()
        savePosition() // the video watched before switching to music keeps its place
        if (shuffle != null) shuffleOn = shuffle
        val start = if (index in items.indices) index else if (shuffleOn) items.indices.random() else 0
        val ordered = if (shuffleOn) {
            listOf(items[start]) + items.filterIndexed { i, _ -> i != start }.shuffled()
        } else items
        _queue.value = QueueState(
            items = ordered, index = if (shuffleOn) 0 else start, music = music,
            shuffle = shuffleOn, repeat = repeatMode, source = source,
            moreUrl = moreUrl, moreToken = moreToken,
            original = if (shuffleOn) items else null,
        )
        play(ordered[if (shuffleOn) 0 else start], rememberCurrent = false, keepQueue = true)
    }

    /** Plays the song at [index] of the queue (picked by the user). */
    fun jumpTo(index: Int) {
        moreJob?.cancel()
        goTo(index)
    }

    private fun goTo(index: Int) {
        val q = _queue.value
        val v = q.items.getOrNull(index) ?: return
        _queue.value = q.copy(index = index, videoOnce = false)
        play(v, rememberCurrent = false, keepQueue = true)
    }

    /** Puts [video] right after the current song. */
    fun playNext(video: Video) {
        val q = ensureQueue() ?: run {
            playQueue(listOf(video), 0, "Hàng chờ")
            return
        }
        val list = q.items.toMutableList()
        list.add((q.index + 1).coerceAtMost(list.size), video)
        _queue.value = q.copy(items = list)
        say("Sẽ phát tiếp theo: ${video.title}")
    }

    /** Adds [video] at the end of the queue. */
    fun addToQueue(video: Video) {
        val q = ensureQueue() ?: run {
            playQueue(listOf(video), 0, "Hàng chờ")
            return
        }
        _queue.value = q.copy(items = q.items + video)
        say("Đã thêm vào hàng chờ")
    }

    fun removeFromQueue(index: Int) {
        val q = _queue.value
        if (index == q.index || index !in q.items.indices) return
        val removed = q.items[index]
        val list = q.items.toMutableList().apply { removeAt(index) }
        _queue.value = q.copy(
            items = list, index = if (index < q.index) q.index - 1 else q.index,
            original = q.original?.filterNot { videoKey(it.url) == videoKey(removed.url) },
        )
    }

    /** Shuffles the songs after the current one once (or restores the original order). */
    fun toggleShuffle() {
        shuffleOn = !shuffleOn
        val q = _queue.value
        val cur = q.current
        if (q.items.isEmpty() || cur == null) {
            _queue.value = q.copy(shuffle = shuffleOn)
            return
        }
        _queue.value = if (shuffleOn) {
            val rest = q.items.filterIndexed { i, _ -> i != q.index }.shuffled()
            q.copy(items = listOf(cur) + rest, index = 0, shuffle = true, original = q.items)
        } else {
            val orig = q.original
            if (orig != null) {
                // Songs added while shuffled are kept at the end.
                val keys = orig.map { videoKey(it.url) }.toHashSet()
                val restored = orig + q.items.filter { videoKey(it.url) !in keys }
                val i = restored.indexOfFirst { videoKey(it.url) == videoKey(cur.url) }.coerceAtLeast(0)
                q.copy(items = restored, index = i, shuffle = false, original = null)
            } else q.copy(shuffle = false)
        }
        say(if (shuffleOn) "Đã bật trộn bài" else "Đã tắt trộn bài")
    }

    /** off → repeat all → repeat one → off */
    fun cycleRepeat() {
        repeatMode = (repeatMode + 1) % 3
        _queue.update { it.copy(repeat = repeatMode) }
        say(
            when (repeatMode) {
                1 -> "Lặp lại tất cả"
                2 -> "Lặp lại bài này"
                else -> "Đã tắt lặp lại"
            }
        )
    }

    /** Endless list of similar songs ("radio") starting with [seed] or the current song. */
    fun startRadio(seed: Video? = null) {
        val video = seed ?: _ui.value.video ?: return
        moreJob?.cancel()
        say("Đang tìm các bài tương tự…")
        moreJob = scope.launch {
            try {
                val page = vn.lucbao.music.MusicRepo.radio(video)
                val seedKey = videoKey(video.url)
                val songs = page.items.filter { it.playable }.map { it.toVideo() }
                    .distinctBy { videoKey(it.url) }
                    .filterNot { videoKey(it.url) == seedKey }
                if (songs.isEmpty()) {
                    say("Không tìm được bài tương tự")
                    return@launch
                }
                val current = _ui.value.video
                val source = "Tương tự · ${video.title.ifBlank { current?.title ?: "" }}"
                shuffleOn = false
                if (current != null && videoKey(current.url) == seedKey) {
                    // Keep playing this song; the similar songs follow it.
                    _queue.value = QueueState(
                        items = listOf(current) + songs, index = 0, music = true, repeat = repeatMode,
                        source = source, moreUrl = radioUrl(video), moreToken = page.next,
                        videoOnce = !_queue.value.showMusicPlayer && _ui.value.quality?.audioOnly == false,
                    )
                } else {
                    playQueue(listOf(video) + songs, 0, source, shuffle = false,
                        moreUrl = radioUrl(video), moreToken = page.next)
                }
                say("Đã thêm ${songs.size} bài tương tự")
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                Log.w(TAG, "Radio failed", t)
                say("Không tìm được bài tương tự")
            }
        }
    }

    /** Shows the current song as a video (only this song; the next ones are music again). */
    fun watchVideo() {
        _queue.update { it.copy(videoOnce = true) }
        if (_ui.value.quality?.audioOnly == true) {
            val best = Quality.pick(
                _ui.value.choices.filterNot { it.audioOnly },
                manualMaxP?.takeIf { it > 0 } ?: networkMaxP()
            )
            if (best != null) switchQuality(best)
        }
    }

    /** Pauses after [minutes] (0 = off, -1 = when the current song ends). */
    fun setSleepTimer(minutes: Int) {
        _sleepAt.value = when {
            minutes == 0 -> 0
            minutes < 0 -> -1
            else -> System.currentTimeMillis() + minutes * 60_000L
        }
        say(
            when {
                minutes == 0 -> "Đã tắt hẹn giờ"
                minutes < 0 -> "Sẽ dừng khi hết bài này"
                else -> "Sẽ dừng sau $minutes phút"
            }
        )
    }

    private fun radioUrl(video: Video): String? {
        val id = videoKey(video.url).takeIf { it.length == 11 } ?: return null
        return "https://www.youtube.com/watch?v=$id&list=RD$id"
    }

    /** The queue to edit; a single playing video becomes the first item of a new queue. */
    private fun ensureQueue(): QueueState? {
        val q = _queue.value
        if (q.items.isNotEmpty()) return q
        val current = _ui.value.video ?: return null
        shuffleOn = false
        val fresh = QueueState(
            items = listOf(current), index = 0, music = true, shuffle = false,
            repeat = repeatMode, source = "Hàng chờ",
            // A video being watched stays a video; the queued songs play as music.
            videoOnce = _ui.value.quality?.audioOnly != true,
        )
        _queue.value = fresh
        return fresh
    }

    private fun nextIndex(q: QueueState): Int? = when {
        q.items.isEmpty() -> null
        q.index + 1 < q.items.size -> q.index + 1
        q.repeat == 1 -> 0
        else -> null
    }

    private fun loadMoreThenNext(url: String, token: String) {
        moreJob?.cancel()
        moreJob = scope.launch {
            try {
                var next: String? = token
                var tries = 0
                // A page can bring only songs we already have: try a few pages.
                while (next != null && tries < 3) {
                    tries++
                    val page = vn.lucbao.music.MusicRepo.playlist(url, next)
                    next = page.next
                    val have = _queue.value.items.map { videoKey(it.url) }.toHashSet()
                    val more = page.items.filter { it.playable }.map { it.toVideo() }
                        .filter { videoKey(it.url) !in have }
                    _queue.update { it.copy(items = it.items + more, moreToken = next, moreUrl = if (next == null) null else it.moreUrl) }
                    if (more.isNotEmpty()) break
                }
                val q = _queue.value
                if (q.index + 1 < q.items.size) {
                    goTo(q.index + 1)
                } else {
                    _queue.update { it.copy(moreUrl = null, moreToken = null) }
                    next()
                }
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                Log.w(TAG, "More songs failed", t)
                _queue.update { it.copy(moreUrl = null, moreToken = null) }
                next()
            }
        }
    }

    // ------------------------------------------------------------------ Vietnamese

    /** 0 = off, 1 = subtitles, 2 = subtitles + voice-over, 3 = voice-over only. Kept for later videos. */
    fun setVietnamese(mode: Int) {
        Prefs.update { it.copy(vietnamese = mode) }
        if (speaks(mode)) VoiceOver.init(app) else VoiceOver.shutdown()
        lineIndex = NO_LINE
        if (mode == 0) {
            _caption.value = null
            return
        }
        val d = _ui.value.details ?: return
        if (captionsFor != d.url || _captionInfo.value.status == CaptionStatus.ERROR ||
            _captionInfo.value.status == CaptionStatus.UNSUPPORTED
        ) {
            loadCaptions(d.url)
        }
    }

    /** Whether [mode] reads the subtitles aloud. */
    fun speaks(mode: Int) = mode == 2 || mode == 3

    /** Whether [mode] shows the subtitles on screen. */
    fun showsText(mode: Int) = mode == 1 || mode == 2

    private fun resetCaptions() {
        captionJob?.cancel()
        VoiceOver.stop()
        lines = emptyList()
        captionsFor = null
        lineIndex = NO_LINE
        _caption.value = null
        _captionInfo.value = CaptionInfo()
    }

    private fun loadCaptions(url: String) {
        captionJob?.cancel()
        lines = emptyList()
        lineIndex = NO_LINE
        _caption.value = null
        captionsFor = url
        _captionInfo.value = CaptionInfo(CaptionStatus.LOADING)
        if (speaks(Prefs.current.vietnamese)) VoiceOver.init(app)
        captionJob = scope.launch {
            try {
                val engine = withContext(Dispatchers.IO) { EngineManager.get() }
                // Found by name so engines and apps of different ages keep working together.
                val method = runCatching {
                    engine.javaClass.getMethod("captionsJson", String::class.java, String::class.java)
                }.getOrNull()
                if (method == null) {
                    _captionInfo.value = CaptionInfo(CaptionStatus.UNSUPPORTED)
                    Updater.requestEngineCheck(app)
                    return@launch
                }
                val json = withContext(Dispatchers.IO) {
                    try {
                        method.invoke(engine, url, "vi") as String?
                    } catch (e: java.lang.reflect.InvocationTargetException) {
                        throw e.targetException ?: e
                    }
                }
                val parsed = json?.let { parseCaptions(it) }
                if (parsed == null || parsed.second.isEmpty()) {
                    _captionInfo.value = CaptionInfo(CaptionStatus.NONE)
                    return@launch
                }
                lines = mergeForSpeech(parsed.second)
                lineIndex = NO_LINE
                _captionInfo.value = parsed.first
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                Log.w(TAG, "Captions failed", t)
                _captionInfo.value = CaptionInfo(CaptionStatus.ERROR)
            }
        }
    }

    private fun parseCaptions(json: String): Pair<CaptionInfo, List<Caption>> {
        val o = JSONObject(json)
        val arr = o.optJSONArray("lines")
        val out = ArrayList<Caption>(arr?.length() ?: 0)
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val l = arr.optJSONArray(i) ?: continue
                val text = l.optString(2).trim()
                if (text.isNotEmpty()) out.add(Caption(l.optLong(0), l.optLong(1), text))
            }
        }
        val info = CaptionInfo(
            CaptionStatus.READY,
            translated = o.optBoolean("translated"),
            source = o.optString("source").ifBlank { null },
        )
        return info to out
    }

    /**
     * Auto subtitles come in short fragments; joining neighbours into phrases reads
     * much better aloud and is easier to follow on screen.
     */
    private fun mergeForSpeech(src: List<Caption>): List<Caption> {
        val out = ArrayList<Caption>(src.size)
        for (c in src) {
            val text = c.text.trim()
            if (text.isEmpty()) continue
            val last = out.lastOrNull()
            val joinable = last != null &&
                c.startMs - last.endMs < 300 &&
                c.endMs - last.startMs <= 5_500 &&
                last.text.length + text.length <= 90 &&
                !last.text.trimEnd().let { it.endsWith('.') || it.endsWith('?') || it.endsWith('!') }
            if (joinable && last != null) {
                out[out.lastIndex] = Caption(last.startMs, c.endMs, last.text + " " + text)
            } else {
                out.add(Caption(c.startMs, c.endMs, text))
            }
        }
        return out
    }

    /** Index of the line shown at [pos], or -1 (between lines). */
    private fun lineAt(pos: Long): Int {
        var lo = 0
        var hi = lines.size - 1
        var found = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (lines[mid].startMs <= pos) {
                found = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return if (found >= 0 && pos < lines[found].endMs) found else -1
    }

    private fun tickCaptions() {
        if (!::exo.isInitialized) return
        val mode = Prefs.current.vietnamese
        if (mode == 0 || lines.isEmpty()) return
        val pos = exo.currentPosition
        val i = lineAt(pos)
        if (i == lineIndex) return
        lineIndex = i
        val line = lines.getOrNull(i)
        _caption.value = line?.text
        // Only read a line from (near) its beginning, e.g. not after jumping into its middle.
        if (speaks(mode) && line != null && exo.isPlaying && pos - line.startMs < 1_200) {
            val speed = _ui.value.speed.coerceAtLeast(0.25f)
            VoiceOver.speak(line.text, ((line.endMs - pos) / speed).toLong())
        }
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
                val music = _queue.value.music && !_queue.value.videoOnce
                if (_queue.value.music) Library.recordSong(full) else Library.recordWatch(full)
                val choices = Quality.choices(details.videoOptions, details.audioOptions.isNotEmpty())
                val choice = forced?.let { f -> choices.firstOrNull { it.label == f.label } }
                    ?: (if (music) choices.firstOrNull { it.audioOnly } else null)
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
                if (Prefs.current.vietnamese > 0 && captionsFor != details.url) {
                    loadCaptions(details.url)
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
        // A song that can never play here (removed, blocked…) must not stop the whole queue.
        val q = _queue.value
        val unplayable = kind == ErrorKind.UNAVAILABLE || kind == ErrorKind.GEO_BLOCKED ||
            kind == ErrorKind.AGE_RESTRICTED || kind == ErrorKind.PRIVATE || kind == ErrorKind.PAID
        if (q.items.isNotEmpty() && unplayable && q.index + 1 < q.items.size) {
            say("Bỏ qua bài không phát được")
            scope.launch {
                delay(1_200)
                if (_queue.value.index == q.index) next()
            }
        }
    }

    private fun networkMaxP(): Int {
        val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val s = Prefs.current
        return if (cm.isActiveNetworkMetered) s.qualityMobile else s.qualityWifi
    }

    private fun preferredMaxP(): Int = manualMaxP ?: networkMaxP()

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
        if (!::exo.isInitialized || v.live || (_queue.value.music && !_queue.value.videoOnce)) return
        val pos = exo.currentPosition
        if (pos > 0) Library.updatePosition(v.url, pos)
    }

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _ui.update { it.copy(isPlaying = isPlaying) }
            if (!isPlaying) {
                savePosition()
                VoiceOver.stop()
            }
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                // Jumped: stop the current sentence; the next line is read from its start.
                VoiceOver.stop()
                lineIndex = NO_LINE
            }
        }

        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_ENDED) {
                savePosition()
                val q = _queue.value
                when {
                    _sleepAt.value == -1L -> _sleepAt.value = 0 // sleep timer: stop after this song
                    q.items.isNotEmpty() && q.repeat == 2 -> {
                        exo.seekTo(0)
                        exo.play()
                    }
                    q.items.isNotEmpty() || Prefs.current.autoplayNext -> next()
                }
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
