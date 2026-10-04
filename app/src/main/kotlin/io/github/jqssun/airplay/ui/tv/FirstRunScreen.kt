package io.github.jqssun.airplay.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.jqssun.airplay.R
import io.github.jqssun.airplay.ServerName
import io.github.jqssun.airplay.ui.dpadFocus
import io.github.jqssun.airplay.ui.requestFocusUntilLanded

@Composable
fun TvFirstRunScreen(suggestedName: String, onDone: (name: String, startAtBoot: Boolean) -> Unit) {
    var name by remember { mutableStateOf(suggestedName) }
    var boot by remember { mutableStateOf(true) }
    // focus Done so one click accepts the defaults
    val doneFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { doneFocus.requestFocusUntilLanded() }

    Column(
        modifier = Modifier.fillMaxSize().background(TvBackground).padding(horizontal = 160.dp, vertical = 64.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
    ) {
        Text(stringResource(R.string.tv_firstrun_title), style = MaterialTheme.typography.displaySmall, color = Color.White)
        Text(stringResource(R.string.tv_firstrun_name), style = MaterialTheme.typography.titleLarge, color = Color.White.copy(alpha = 0.7f))
        OutlinedTextField(
            value = name, onValueChange = { name = it }, singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("firstrun_name"),
        )
        Row(
            modifier = Modifier.fillMaxWidth().dpadFocus(RoundedCornerShape(12.dp)).clickable { boot = !boot }
                .padding(horizontal = 16.dp, vertical = 12.dp).testTag("firstrun_boot"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(stringResource(R.string.tv_setting_boot), style = MaterialTheme.typography.titleLarge, color = Color.White)
            Switch(checked = boot, onCheckedChange = null)
        }
        Button(
            onClick = { onDone(ServerName.choose(name, null, null), boot) },
            enabled = name.isNotBlank(),
            modifier = Modifier.focusRequester(doneFocus).dpadFocus().testTag("firstrun_done"),
        ) { Text(stringResource(R.string.tv_firstrun_done)) }
    }
}
