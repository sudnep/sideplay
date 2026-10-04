package io.github.jqssun.airplay.ui.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.jqssun.airplay.R
import io.github.jqssun.airplay.ui.requestFocusUntilLanded

val TvBackground = Color(0xFF101418)
private val TvMuted = Color.White.copy(alpha = 0.7f)

@Composable
fun TvIdleScreen(
    serverName: String,
    ipAddress: String?,
    connecting: Boolean,
    overlayAccess: OverlayAccess,
    onOpenSettings: () -> Unit,
    onAllowAutoOpen: () -> Unit,
    receiverError: Boolean = false,
    onRetry: () -> Unit = {},
) {
    val settingsFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { settingsFocus.requestFocusUntilLanded() }
    Box(Modifier.fillMaxSize().background(TvBackground).padding(horizontal = 64.dp, vertical = 48.dp)) {
        Column(
            modifier = Modifier.align(Alignment.CenterStart).fillMaxWidth(0.7f),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                text = when {
                    receiverError -> stringResource(R.string.tv_idle_error)
                    connecting -> stringResource(R.string.tv_idle_connecting, serverName)
                    else -> stringResource(R.string.tv_idle_ready, serverName)
                },
                style = MaterialTheme.typography.displaySmall,
                color = Color.White,
                modifier = Modifier.testTag("idle_title"),
            )
            Text(stringResource(R.string.tv_idle_steps_title), style = MaterialTheme.typography.titleLarge, color = TvMuted)
            Step(1, stringResource(R.string.tv_idle_step_1))
            Step(2, stringResource(R.string.tv_idle_step_2))
            Step(3, stringResource(R.string.tv_idle_step_3, serverName))
            if (receiverError) {
                Button(onClick = onRetry, modifier = Modifier.tvFocus().testTag("retry_receiver")) {
                    Text(stringResource(R.string.tv_retry))
                }
            }
            when (overlayAccess) {
                OverlayAccess.GRANTABLE -> Button(
                    onClick = onAllowAutoOpen,
                    modifier = Modifier.tvFocus().testTag("allow_auto_open"),
                ) { Text(stringResource(R.string.tv_idle_allow_auto_open)) }
                OverlayAccess.UNAVAILABLE -> Text(
                    stringResource(R.string.tv_idle_keep_open),
                    style = MaterialTheme.typography.bodyLarge,
                    color = TvMuted,
                    modifier = Modifier.testTag("keep_open_note"),
                )
                OverlayAccess.GRANTED -> Unit
            }
        }
        Column(Modifier.align(Alignment.BottomEnd), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (ipAddress != null) {
                Text(
                    stringResource(R.string.tv_idle_address, ipAddress),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TvMuted,
                    modifier = Modifier.testTag("idle_ip"),
                )
            }
            Button(
                onClick = onOpenSettings,
                modifier = Modifier.focusRequester(settingsFocus).tvFocus().testTag("open_settings"),
            ) { Text(stringResource(R.string.tv_settings)) }
        }
    }
}

@Composable
private fun Step(n: Int, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("$n", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        Text(text, style = MaterialTheme.typography.headlineSmall, color = Color.White)
    }
}

@Composable
fun TvPinScreen(code: String, onDismiss: () -> Unit) {
    BackHandler { onDismiss() }
    Box(Modifier.fillMaxSize().background(TvBackground), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Text(stringResource(R.string.tv_pin_title), style = MaterialTheme.typography.headlineMedium, color = TvMuted)
            Text(
                code,
                style = MaterialTheme.typography.displayLarge,
                color = Color.White,
                modifier = Modifier.testTag("pin_code"),
            )
        }
    }
}
