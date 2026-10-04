package io.github.jqssun.airplay.ui.tv

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.jqssun.airplay.R

@Composable
fun TvAudioScreen(title: String, artist: String, album: String, coverArt: Bitmap?) {
    val art = remember(coverArt) { coverArt?.asImageBitmap() }
    Box(Modifier.fillMaxSize().background(TvBackground)) {
        if (art != null) {
            // blur needs api 31; older tvs get a dimmed cover instead
            Image(
                art, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().alpha(0.35f).then(if (Build.VERSION.SDK_INT >= 31) Modifier.blur(48.dp) else Modifier),
            )
        }
        Row(
            modifier = Modifier.align(Alignment.Center).padding(64.dp),
            horizontalArrangement = Arrangement.spacedBy(48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (art != null) {
                Image(art, contentDescription = null, modifier = Modifier.size(320.dp).clip(RoundedCornerShape(16.dp)))
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    title.ifBlank { stringResource(R.string.tv_audio_fallback_title) },
                    style = MaterialTheme.typography.displaySmall,
                    color = Color.White,
                    modifier = Modifier.testTag("audio_title"),
                )
                val subtitle = listOf(artist, album).filter { it.isNotBlank() }.joinToString(" · ")
                if (subtitle.isNotEmpty()) {
                    Text(subtitle, style = MaterialTheme.typography.headlineSmall, color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.testTag("audio_artist"))
                }
            }
        }
    }
}
