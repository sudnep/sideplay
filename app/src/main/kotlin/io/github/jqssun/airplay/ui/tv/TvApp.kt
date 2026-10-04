package io.github.jqssun.airplay.ui.tv

import android.view.Surface
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.jqssun.airplay.renderer.DecoderSelector
import io.github.jqssun.airplay.service.AirPlayService.ServerState
import io.github.jqssun.airplay.ui.VideoSurfaceView
import io.github.jqssun.airplay.ui.theme.AirPlayTheme
import io.github.jqssun.airplay.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class TvRoute { HOME, SETTINGS, TROUBLESHOOTING }

// tv screens always draw on a dark background, whatever the system theme
@Composable
fun TvTheme(content: @Composable () -> Unit) = AirPlayTheme(darkTheme = true, content = content)

/** Routing only; every screen is a slot so this can be tested without the service. */
@Composable
fun TvAppContent(
    screen: TvScreen,
    route: TvRoute,
    inPip: Boolean,
    mirrorSurface: @Composable () -> Unit,
    playbackSurface: @Composable () -> Unit,
    firstRun: @Composable () -> Unit,
    idle: @Composable (connecting: Boolean) -> Unit,
    pin: @Composable (code: String) -> Unit,
    mirroring: @Composable () -> Unit,
    video: @Composable () -> Unit,
    audio: @Composable () -> Unit,
    settings: @Composable () -> Unit,
    troubleshooting: @Composable () -> Unit,
) {
    // centred: portrait iphones and 4:3 ipads give a surface narrower than the tv
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        // picture-in-picture is a small window: show only the picture
        if (inPip) {
            if (screen is TvScreen.Video) playbackSurface() else mirrorSurface()
            return@Box
        }
        // kept composed (never moved) so frames have a surface the moment mirroring starts
        if (screen !is TvScreen.Video) mirrorSurface()
        when (screen) {
            TvScreen.FirstRun -> firstRun()
            is TvScreen.Idle -> when (route) {
                TvRoute.HOME -> idle(screen.connecting)
                TvRoute.SETTINGS -> settings()
                TvRoute.TROUBLESHOOTING -> troubleshooting()
            }
            is TvScreen.Pin -> pin(screen.code)
            TvScreen.Mirroring -> mirroring()
            TvScreen.Video -> video()
            TvScreen.Audio -> audio()
        }
    }
}

@Composable
fun TvApp(
    viewModel: MainViewModel,
    isInPip: Boolean,
    onSurfaceAvailable: (Surface) -> Unit,
    onSurfaceDestroyed: (Surface) -> Unit,
) {
    val context = LocalContext.current
    val firstRunDone by viewModel.firstRunDone.collectAsState()
    val connections by viewModel.connectionCount.collectAsState()
    val mirroring by viewModel.mirroringActive.collectAsState()
    val audioOnly by viewModel.audioOnly.collectAsState()
    val videoActive by viewModel.videoPlaybackActive.collectAsState()
    val videoPending by viewModel.videoSessionPending.collectAsState()
    val pinCode by viewModel.pinCode.collectAsState()
    val serverName by viewModel.serverName.collectAsState()
    val lanAddress by viewModel.lanAddress.collectAsState()
    val serverState by viewModel.serverState.collectAsState()
    val h265 by viewModel.h265Enabled.collectAsState()

    var route by rememberSaveable { mutableStateOf(TvRoute.HOME) }
    var overlayAccess by remember { mutableStateOf(OverlayPermission.access(context)) }
    // re-check after returning from the system permission screen
    LifecycleResumeEffect(Unit) {
        overlayAccess = OverlayPermission.access(context)
        onPauseOrDispose { }
    }

    val screen = resolveTvScreen(TvSession(firstRunDone, connections, mirroring, audioOnly, videoActive, videoPending, pinCode))
    // a session ends any settings visit, so the next idle starts at home
    LaunchedEffect(screen !is TvScreen.Idle) { if (screen !is TvScreen.Idle) route = TvRoute.HOME }
    // the service only clears a pin when a stream starts; a cancelled pairing must not leave it behind
    LaunchedEffect(connections) { if (connections == 0) viewModel.dismissPin() }

    val playbackSurface: @Composable () -> Unit = {
        val aspect by viewModel.videoPlaybackAspect.collectAsState()
        VideoSurfaceView(
            onSurfaceAvailable = { viewModel.onVideoPlaybackSurfaceAvailable(it) },
            onSurfaceDestroyed = { viewModel.onVideoPlaybackSurfaceDestroyed(it) },
            aspectRatio = aspect,
        )
    }

    TvTheme {
        TvAppContent(
            screen = screen,
            route = route,
            inPip = isInPip,
            playbackSurface = playbackSurface,
            mirrorSurface = {
                val aspect by viewModel.videoAspect.collectAsState()
                VideoSurfaceView(onSurfaceAvailable = onSurfaceAvailable, onSurfaceDestroyed = onSurfaceDestroyed, aspectRatio = aspect)
            },
            firstRun = { TvFirstRunScreen(serverName) { name, boot -> viewModel.completeFirstRun(name, boot) } },
            idle = { connecting ->
                TvIdleScreen(
                    serverName = serverName,
                    ipAddress = lanAddress,
                    connecting = connecting,
                    overlayAccess = overlayAccess,
                    onOpenSettings = { route = TvRoute.SETTINGS },
                    onAllowAutoOpen = { runCatching { context.startActivity(OverlayPermission.intent(context)) } },
                    receiverError = serverState == ServerState.ERROR,
                    onRetry = { viewModel.startServer() },
                )
            },
            pin = { code -> TvPinScreen(code, onDismiss = { viewModel.dismissPin() }) },
            mirroring = { TvMirroringOverlay(onDisconnect = { viewModel.disconnectAll() }) },
            video = {
                val title by viewModel.videoTitle.collectAsState()
                val playing by viewModel.videoPlaying.collectAsState()
                val buffering by viewModel.videoBuffering.collectAsState()
                val position by viewModel.videoPositionMs.collectAsState()
                val duration by viewModel.videoDurationMs.collectAsState()
                TvVideoScreen(
                    state = TvVideoState(title, playing, buffering, pending = !videoActive && videoPending, position, duration),
                    surface = playbackSurface,
                    onTogglePlay = { viewModel.toggleVideoPlayPause(showOverlay = false) },
                    onPlay = { viewModel.setVideoPlaying(true, showOverlay = false) },
                    onPause = { viewModel.setVideoPlaying(false, showOverlay = false) },
                    onSeekBy = { viewModel.seekVideoBy(it) },
                    onStop = { viewModel.stopVideoPlayback() },
                )
            },
            audio = {
                val track by viewModel.trackInfo.collectAsState()
                TvAudioScreen(track.title, track.artist, track.album, track.coverArt)
            },
            settings = {
                val requirePin by viewModel.requirePin.collectAsState()
                val boot by viewModel.bootAutoStart.collectAsState()
                TvSettingsScreen(
                    state = TvSettingsState(serverName, requirePin, boot, h265),
                    onRename = { viewModel.setServerName(it) },
                    onRequirePin = { viewModel.setRequirePin(it) },
                    onBootAutoStart = { viewModel.setBootAutoStart(it) },
                    onH265 = { viewModel.setH265Enabled(it) },
                    onOpenTroubleshooting = { route = TvRoute.TROUBLESHOOTING },
                    onBack = { route = TvRoute.HOME },
                )
            },
            troubleshooting = {
                val port by viewModel.serverPort.collectAsState()
                // the service's debug stream is off by default; ask the decoder selector directly
                val decoders by produceState("", h265) {
                    value = withContext(Dispatchers.IO) {
                        val selector = DecoderSelector(context)
                        val avc = selector.avc()
                        decoderSummary(avc?.name, selector.hevc(avc, 1920, 1080, 60)?.name, h265)
                    }
                }
                val logs by viewModel.logs.collectAsState()
                val version = remember {
                    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
                }
                TvTroubleshootingScreen(
                    state = TvTroubleshootingState(lanAddress, port, version, decoders, logs),
                    onBack = { route = TvRoute.SETTINGS },
                )
            },
        )
    }
}
