package io.github.jqssun.airplay.ui.tv

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

// white ring around a small inset: a border drawn on the component itself ends up under
// material surfaces, and a primary-coloured one vanishes on primary-filled buttons
fun Modifier.tvFocus(shape: Shape = RoundedCornerShape(50)): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    onFocusChanged { focused = it.hasFocus }
        .border(3.dp, if (focused) Color.White else Color.Transparent, shape)
        .padding(5.dp)
}
