package io.github.jqssun.airplay.ui.tv

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.jqssun.airplay.R
import io.github.jqssun.airplay.ui.requestFocusUntilLanded
import kotlinx.coroutines.delay

private const val CONTROLS_HIDE_MS = 4_000L

data class TvVideoState(
    val title: String,
    val playing: Boolean,
    val buffering: Boolean,
    val pending: Boolean,
    val positionMs: Long,
    val durationMs: Long,
)

@Composable
fun TvVideoScreen(
    state: TvVideoState,
    surface: @Composable () -> Unit,
    onTogglePlay: () -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onStop: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    var controlsVisible by remember { mutableStateOf(true) }
    var keyTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { focus.requestFocusUntilLanded() }
    // any key or pause shows controls; they hide again while playing
    LaunchedEffect(keyTick, state.playing) {
        controlsVisible = true
        if (state.playing) {
            delay(CONTROLS_HIDE_MS)
            controlsVisible = false
        }
    }
    BackHandler { onStop() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val action = videoKeyAction(e.nativeKeyEvent.keyCode) ?: return@onPreviewKeyEvent false
                when (action) {
                    VideoKeyAction.TOGGLE_PLAY -> onTogglePlay()
                    VideoKeyAction.PLAY -> onPlay()
                    VideoKeyAction.PAUSE -> onPause()
                    VideoKeyAction.SEEK_BACK -> onSeekBy(-SEEK_STEP_MS)
                    VideoKeyAction.SEEK_FORWARD -> onSeekBy(SEEK_STEP_MS)
                    VideoKeyAction.SHOW_CONTROLS -> Unit
                }
                keyTick++
                true
            }
            .testTag("video_root"),
        contentAlignment = Alignment.Center,
    ) {
        surface()
        if (state.pending || state.buffering) {
            val loading = stringResource(R.string.tv_video_loading)
            CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.testTag("video_loading").semantics { contentDescription = loading },
            )
        }
        AnimatedVisibility(
            visible = controlsVisible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))))
                    .padding(horizontal = 64.dp, vertical = 40.dp)
                    .testTag("video_controls"),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.title.isNotBlank()) {
                    Text(state.title, style = MaterialTheme.typography.headlineSmall, color = Color.White)
                }
                val fraction = if (state.durationMs > 0) (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f
                LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                Text(
                    "${formatPlaybackTime(state.positionMs)} / ${formatPlaybackTime(state.durationMs)}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.testTag("video_time"),
                )
            }
        }
    }
}
