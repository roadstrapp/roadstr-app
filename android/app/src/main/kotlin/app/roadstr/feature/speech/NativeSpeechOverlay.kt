package app.roadstr.feature.speech

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.roadstr.R

/**
 * The listening screen: a card at the bottom with a mic that swells with the voice, a way to finish
 * early (useful in a noisy car, where silence never comes) and a way to cancel. Hidden when idle.
 */
@Composable
fun NativeSpeechOverlay(
    snapshot: NativeSpeechSnapshot,
    onDone: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (snapshot.phase == NativeSpeechPhase.Idle) return
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Surface(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (snapshot.phase == NativeSpeechPhase.Listening) {
                    ListeningIndicator(snapshot.level)
                } else {
                    CircularProgressIndicator(modifier = Modifier.size(48.dp))
                }
                Text(
                    stringResource(
                        if (snapshot.phase == NativeSpeechPhase.Listening) {
                            R.string.native_speech_listening
                        } else {
                            R.string.native_speech_transcribing
                        },
                    ),
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    style = MaterialTheme.typography.titleMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = onCancel) { Text(stringResource(R.string.native_speech_stop)) }
                    if (snapshot.phase == NativeSpeechPhase.Listening) {
                        Button(onClick = onDone) { Text(stringResource(R.string.native_speech_done)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ListeningIndicator(level: Float) {
    val scale by animateFloatAsState(1f + level.coerceIn(0f, 1f) * 0.45f, label = "mic-level")
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(72.dp)) {
        Surface(
            modifier = Modifier.size(56.dp).scale(scale),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
            content = {},
        )
        Surface(
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.Mic,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(26.dp),
                )
            }
        }
    }
}
