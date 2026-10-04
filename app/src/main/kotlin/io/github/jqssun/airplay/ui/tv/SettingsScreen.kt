package io.github.jqssun.airplay.ui.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.jqssun.airplay.R
import io.github.jqssun.airplay.ServerName
import io.github.jqssun.airplay.ui.dpadFocus
import io.github.jqssun.airplay.ui.requestFocusUntilLanded
import kotlinx.coroutines.launch

data class TvSettingsState(
    val serverName: String,
    val requirePin: Boolean,
    val bootAutoStart: Boolean,
    val h265Enabled: Boolean,
)

data class TvTroubleshootingState(
    val ipAddress: String?,
    val port: Int,
    val versionName: String,
    val decoder: String,
    val logs: List<String>,
)

@Composable
fun TvSettingsScreen(
    state: TvSettingsState,
    onRename: (String) -> Unit,
    onRequirePin: (Boolean) -> Unit,
    onBootAutoStart: (Boolean) -> Unit,
    onH265: (Boolean) -> Unit,
    onOpenTroubleshooting: () -> Unit,
    onBack: () -> Unit,
) {
    var renaming by remember { mutableStateOf(false) }
    val firstRow = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstRow.requestFocusUntilLanded() }
    BackHandler(enabled = !renaming) { onBack() }

    Column(
        modifier = Modifier.fillMaxSize().background(TvBackground).padding(horizontal = 96.dp, vertical = 48.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.tv_settings), style = MaterialTheme.typography.displaySmall, color = Color.White)
        SettingRow(stringResource(R.string.tv_setting_name), "setting_name", Modifier.focusRequester(firstRow), onClick = { renaming = true }) {
            Text(state.serverName, color = Color.White.copy(alpha = 0.7f))
        }
        SwitchRow(stringResource(R.string.tv_setting_pin), "setting_pin", state.requirePin, onRequirePin)
        SwitchRow(stringResource(R.string.tv_setting_boot), "setting_boot", state.bootAutoStart, onBootAutoStart)
        SwitchRow(stringResource(R.string.tv_setting_h265), "setting_h265", state.h265Enabled, onH265,
            description = stringResource(R.string.tv_setting_h265_desc))
        SettingRow(stringResource(R.string.tv_troubleshooting), "setting_troubleshooting", onClick = onOpenTroubleshooting)
    }

    if (renaming) RenameDialog(state.serverName, onDismiss = { renaming = false }, onSave = { renaming = false; onRename(it) })
}

@Composable
private fun SettingRow(
    label: String,
    tag: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .dpadFocus(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleLarge, color = Color.White)
            if (description != null) Text(description, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.6f))
        }
        trailing()
    }
}

@Composable
private fun SwitchRow(label: String, tag: String, checked: Boolean, onChange: (Boolean) -> Unit, description: String? = null) =
    SettingRow(label, tag, description = description, onClick = { onChange(!checked) }) {
        // the row handles clicks; the switch is display only so focus stays on the row
        Switch(checked = checked, onCheckedChange = null)
    }

@Composable
private fun RenameDialog(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tv_setting_name)) },
        text = {
            OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true, modifier = Modifier.testTag("rename_field"))
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(ServerName.choose(text, null, null)) },
                enabled = text.isNotBlank(),
                modifier = Modifier.dpadFocus().testTag("rename_save"),
            ) { Text(stringResource(R.string.tv_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.dpadFocus()) { Text(stringResource(R.string.tv_cancel)) }
        },
    )
}

@Composable
fun TvTroubleshootingScreen(state: TvTroubleshootingState, onBack: () -> Unit) {
    val listFocus = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { listFocus.requestFocusUntilLanded() }
    BackHandler { onBack() }
    val muted = Color.White.copy(alpha = 0.7f)
    // nothing in the list is focusable, so the d-pad scrolls the list itself
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().background(TvBackground).padding(horizontal = 96.dp, vertical = 48.dp)
            .focusRequester(listFocus).focusable()
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.DirectionDown -> { scope.launch { listState.animateScrollBy(SCROLL_STEP_PX) }; true }
                    Key.DirectionUp -> { scope.launch { listState.animateScrollBy(-SCROLL_STEP_PX) }; true }
                    else -> false
                }
            }
            .testTag("ts_list"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { Text(stringResource(R.string.tv_troubleshooting), style = MaterialTheme.typography.displaySmall, color = Color.White) }
        item { Section(stringResource(R.string.tv_ts_network)) }
        item {
            Text(
                if (state.ipAddress != null) stringResource(R.string.tv_ts_address, state.ipAddress, state.port)
                else stringResource(R.string.tv_ts_address_unknown),
                color = muted, modifier = Modifier.testTag("ts_address"),
            )
        }
        item { Section(stringResource(R.string.tv_ts_decoder)) }
        item {
            Text(state.decoder.ifBlank { stringResource(R.string.tv_ts_decoder_none) }, color = muted, modifier = Modifier.testTag("ts_decoder"))
        }
        item { Section(stringResource(R.string.tv_ts_about)) }
        item { Text(stringResource(R.string.tv_ts_version, state.versionName), color = muted) }
        item { Text(stringResource(R.string.tv_ts_license), color = muted) }
        item { Text(stringResource(R.string.tv_ts_credits), color = muted) }
        item { Text(stringResource(R.string.tv_ts_disclaimer), color = muted, modifier = Modifier.testTag("ts_disclaimer")) }
        item { Section(stringResource(R.string.tv_ts_logs)) }
        items(state.logs.takeLast(TROUBLESHOOTING_LOG_LINES)) { line ->
            Text(line, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f))
        }
    }
}

@Composable
private fun Section(title: String) =
    Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 16.dp))

private const val TROUBLESHOOTING_LOG_LINES = 100
private const val SCROLL_STEP_PX = 300f
