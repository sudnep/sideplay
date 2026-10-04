package io.github.jqssun.airplay.ui.tv

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import io.github.jqssun.airplay.R
import io.github.jqssun.airplay.ui.requestFocusUntilLanded
import kotlinx.coroutines.delay

private const val CONNECTED_TOAST_MS = 3_000L

/** Transparent layer over the mirror surface: a short "connected" toast and Back → disconnect confirmation. */
@Composable
fun TvMirroringOverlay(onDisconnect: () -> Unit) {
    var toast by remember { mutableStateOf(true) }
    var confirm by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(CONNECTED_TOAST_MS); toast = false }
    BackHandler(enabled = !confirm) { confirm = true }

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(toast, Modifier.align(Alignment.TopCenter).padding(top = 32.dp), enter = fadeIn(), exit = fadeOut()) {
            Surface(shape = RoundedCornerShape(24.dp), color = Color.Black.copy(alpha = 0.7f)) {
                Text(
                    stringResource(R.string.tv_mirroring_connected),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp).testTag("connected_toast"),
                )
            }
        }
        if (confirm) {
            // default to the safe choice
            val cancelFocus = remember { FocusRequester() }
            LaunchedEffect(Unit) { cancelFocus.requestFocusUntilLanded() }
            AlertDialog(
                onDismissRequest = { confirm = false },
                title = { Text(stringResource(R.string.tv_disconnect_title)) },
                confirmButton = {
                    TextButton(
                        onClick = { confirm = false; onDisconnect() },
                        modifier = Modifier.tvFocus().testTag("confirm_disconnect"),
                    ) { Text(stringResource(R.string.tv_disconnect)) }
                },
                dismissButton = {
                    TextButton(
                        onClick = { confirm = false },
                        modifier = Modifier.focusRequester(cancelFocus).tvFocus().testTag("cancel_disconnect"),
                    ) { Text(stringResource(R.string.tv_cancel)) }
                },
            )
        }
    }
}
