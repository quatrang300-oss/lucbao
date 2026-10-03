@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package vn.lucbao.ui

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vn.lucbao.R
import vn.lucbao.data.Prefs
import vn.lucbao.data.Video
import vn.lucbao.player.PlaybackService
import vn.lucbao.player.PlayerController
import vn.lucbao.ui.components.BottomNav
import vn.lucbao.ui.components.MiniPlayer
import vn.lucbao.ui.screens.HomeScreen
import vn.lucbao.ui.screens.LibraryScreen
import vn.lucbao.ui.screens.OnboardingScreen
import vn.lucbao.ui.screens.PlayerScreen
import vn.lucbao.ui.screens.SearchScreen
import vn.lucbao.ui.screens.SettingsScreen
import vn.lucbao.ui.screens.VideoSurface
import vn.lucbao.ui.theme.Luc
import vn.lucbao.ui.theme.LucTheme

class MainActivity : ComponentActivity() {
    companion object {
        const val ACTION_OPEN_PLAYER = "vn.lucbao.OPEN_PLAYER"
        private const val ACTION_PIP = "vn.lucbao.PIP_CONTROL"
        private const val PIP_TOGGLE = 1
        private const val PIP_NEXT = 2
    }

    private val vm: AppViewModel by viewModels()
    private var controllerFuture: ListenableFuture<MediaController>? = null

    private val pipReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.getIntExtra("code", 0)) {
                PIP_TOGGLE -> PlayerController.togglePlay()
                PIP_NEXT -> PlayerController.next()
            }
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onVideoSizeChanged(videoSize: VideoSize) = updatePip()
        override fun onIsPlayingChanged(isPlaying: Boolean) = updatePip()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        PlayerController.init(this)
        PlayerController.exo.addListener(playerListener)
        ContextCompat.registerReceiver(
            this, pipReceiver, IntentFilter(ACTION_PIP), ContextCompat.RECEIVER_NOT_EXPORTED
        )
        if (savedInstanceState == null) handleIntent(intent)

        setContent {
            Root(
                vm,
                actions = ScreenActions(
                    onPip = { enterPip() },
                    onToggleFullscreen = { toggleFullscreen() },
                    onFullscreenShown = { hideSystemBars(it) },
                )
            )
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(PlayerController.ui, vm.nav, Prefs.state) { _, _, _ -> }.collect { updatePip() }
            }
        }
        lifecycleScope.launch {
            vm.nav.collect { nav ->
                if (!nav.playerExpanded && orientationLocked) {
                    orientationLocked = false
                    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    if (nav.fullscreen) vm.nav.update { it.copy(fullscreen = false) }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Binding to the media service keeps playback alive after the app is left.
        controllerFuture = MediaController.Builder(
            this, SessionToken(this, ComponentName(this, PlaybackService::class.java))
        ).buildAsync()
    }

    override fun onStop() {
        super.onStop()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        if (!Prefs.current.backgroundPlay && !isInPictureInPictureMode && !isChangingConfigurations) {
            PlayerController.exo.pause()
        }
    }

    override fun onDestroy() {
        PlayerController.exo.removeListener(playerListener)
        runCatching { unregisterReceiver(pipReceiver) }
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        when (intent.action) {
            ACTION_OPEN_PLAYER -> if (PlayerController.ui.value.video != null) {
                vm.nav.update { it.copy(playerExpanded = true) }
            }
            Intent.ACTION_SEND -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
                val url = Regex("https?://\\S+").find(text)?.value ?: return
                openUrl(url)
            }
            Intent.ACTION_VIEW -> intent.dataString?.let { openUrl(it) }
        }
    }

    private fun openUrl(url: String) {
        PlayerController.play(Video(url = url, title = "", channel = "", thumbnail = null, duration = -1))
        vm.nav.update { it.copy(playerExpanded = true) }
    }

    // ------------------------------------------------------------------ picture in picture

    private fun canPip(): Boolean {
        val ui = PlayerController.ui.value
        return vm.nav.value.playerExpanded && ui.video != null && ui.quality?.audioOnly != true &&
            ui.error == null
    }

    private fun shouldAutoPip(): Boolean =
        Prefs.current.autoPip && canPip() && PlayerController.exo.isPlaying

    private fun pipParams(): PictureInPictureParams {
        val vs = PlayerController.exo.videoSize
        var ratio = if (vs.width > 0 && vs.height > 0) Rational(vs.width, vs.height) else Rational(16, 9)
        val f = ratio.toFloat()
        if (f > 2.39f) ratio = Rational(239, 100)
        if (f < 0.42f) ratio = Rational(100, 239)
        val playing = PlayerController.exo.isPlaying
        val actions = listOf(
            action(
                if (playing) R.drawable.ic_pip_pause else R.drawable.ic_pip_play,
                if (playing) "Tạm dừng" else "Phát", PIP_TOGGLE
            ),
            action(R.drawable.ic_pip_next, "Video tiếp", PIP_NEXT),
        )
        val b = PictureInPictureParams.Builder().setAspectRatio(ratio).setActions(actions)
        if (Build.VERSION.SDK_INT >= 31) {
            b.setAutoEnterEnabled(shouldAutoPip())
            b.setSeamlessResizeEnabled(true)
        }
        return b.build()
    }

    private fun action(icon: Int, title: String, code: Int): RemoteAction {
        val pi = PendingIntent.getBroadcast(
            this, code,
            Intent(ACTION_PIP).setPackage(packageName).putExtra("code", code),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return RemoteAction(Icon.createWithResource(this, icon), title, title, pi)
    }

    private fun updatePip() {
        runCatching { setPictureInPictureParams(pipParams()) }
    }

    fun enterPip() {
        if (canPip()) runCatching { enterPictureInPictureMode(pipParams()) }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT < 31 && shouldAutoPip()) enterPip()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        vm.nav.update { it.copy(pip = isInPictureInPictureMode) }
        if (!isInPictureInPictureMode && lifecycle.currentState == Lifecycle.State.CREATED) {
            // PiP window was closed: stop like YouTube does.
            PlayerController.exo.pause()
        }
    }

    // ------------------------------------------------------------------ fullscreen

    private var orientationLocked = false

    private fun toggleFullscreen() {
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val nowFull = vm.nav.value.fullscreen || landscape
        if (nowFull) {
            vm.nav.update { it.copy(fullscreen = false) }
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            val vs = PlayerController.exo.videoSize
            val portraitVideo = vs.height > vs.width && vs.width > 0
            vm.nav.update { it.copy(fullscreen = true) }
            requestedOrientation = if (portraitVideo) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        // Released when the player is collapsed.
        orientationLocked = true
    }

    private fun hideSystemBars(hide: Boolean) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (hide) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}

class ScreenActions(
    val onPip: () -> Unit,
    val onToggleFullscreen: () -> Unit,
    val onFullscreenShown: (Boolean) -> Unit,
)

@Composable
private fun Root(vm: AppViewModel, actions: ScreenActions) {
    val settings by Prefs.state.collectAsStateWithLifecycle()
    val dark = when (settings.theme) {
        1 -> true
        2 -> false
        else -> isSystemInDarkTheme()
    }
    LucTheme(dark) {
        val view = LocalView.current
        SideEffect {
            val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
        if (!settings.onboarded) {
            OnboardingScreen(onDone = { Prefs.update { it.copy(onboarded = true) } })
        } else {
            Main(vm, actions)
        }
    }
}

@Composable
private fun Main(vm: AppViewModel, actions: ScreenActions) {
    val c = Luc.colors
    val nav by vm.nav.collectAsStateWithLifecycle()
    val ui by PlayerController.ui.collectAsStateWithLifecycle()
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    if (nav.pip) {
        Box(Modifier.fillMaxSize().background(Color.Black)) { VideoSurface(Modifier.fillMaxSize()) }
        return
    }

    val play: (Video) -> Unit = { v ->
        PlayerController.play(v)
        vm.nav.update { it.copy(playerExpanded = true) }
    }
    val expanded = nav.playerExpanded && ui.video != null
    val fullscreen = expanded && (nav.fullscreen || landscape)

    LaunchedEffect(fullscreen) { actions.onFullscreenShown(fullscreen) }
    LaunchedEffect(ui.video) {
        if (ui.video == null && nav.playerExpanded) vm.nav.update { it.copy(playerExpanded = false, fullscreen = false) }
    }

    Box(Modifier.fillMaxSize().background(c.surface)) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                when (nav.tab) {
                    Tab.HOME -> HomeScreen(vm, onPlay = play)
                    Tab.SEARCH -> SearchScreen(vm, nav.focusSearch, onPlay = play, onBack = { vm.go(Tab.HOME) })
                    Tab.LIBRARY -> LibraryScreen(onPlay = play)
                    Tab.SETTINGS -> SettingsScreen()
                }
            }
            if (ui.video != null && !expanded) {
                MiniPlayer(ui, onExpand = { vm.nav.update { it.copy(playerExpanded = true) } })
            }
            BottomNav(nav.tab, onSelect = { vm.go(it) })
        }
        AnimatedVisibility(
            visible = expanded,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
        ) {
            PlayerScreen(
                ui = ui,
                fullscreen = fullscreen,
                onCollapse = { vm.nav.update { it.copy(playerExpanded = false) } },
                onToggleFullscreen = actions.onToggleFullscreen,
                onPip = actions.onPip,
                onPlay = play,
            )
        }
    }

    BackHandler(enabled = expanded) {
        if (nav.fullscreen) vm.nav.update { it.copy(fullscreen = false) }
        else vm.nav.update { it.copy(playerExpanded = false) }
    }
    BackHandler(enabled = !expanded && nav.tab != Tab.HOME) { vm.go(Tab.HOME) }
}
