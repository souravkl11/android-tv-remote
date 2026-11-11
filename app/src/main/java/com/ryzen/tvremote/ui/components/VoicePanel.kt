package com.ryzen.tvremote.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.ryzen.tvremote.ui.VoiceUi
import com.ryzen.tvremote.ui.theme.Motion

/**
 * Shown in place of the control deck while voice search runs. A scalloped shape breathes with
 * your voice; the TV shows what it heard and the results.
 */
@Composable
fun VoicePanel(
    state: VoiceUi,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val listening = state is VoiceUi.Listening
    val level = (state as? VoiceUi.Listening)?.level ?: 0f
    val swell by animateFloatAsState(if (listening) level else 0f, Motion.fastSpatial(), label = "voiceLevel")
    val spin = rememberInfiniteTransition(label = "voiceSpin")
    val rotation by spin.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Restart),
        label = "voiceRotation"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(36.dp))
            .background(scheme.surfaceContainer)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(modifier = Modifier.size(104.dp), contentAlignment = Alignment.Center) {
            if (listening) {
                // Outer halo grows with loudness.
                MorphBox(
                    from = Polygons.Cookie,
                    to = Polygons.Burst,
                    progress = { swell },
                    rotation = { rotation },
                    color = scheme.primary.copy(alpha = 0.22f),
                    modifier = Modifier
                        .size(104.dp)
                        .graphicsLayer {
                            val s = 0.78f + 0.22f * swell
                            scaleX = s
                            scaleY = s
                        }
                )
                ShapeBox(
                    polygon = Polygons.Cookie,
                    color = scheme.primary,
                    modifier = Modifier
                        .size(72.dp)
                        .graphicsLayer { rotationZ = -rotation * 0.5f }
                )
                Icon(Icons.Rounded.Mic, contentDescription = null, tint = scheme.onPrimary, modifier = Modifier.size(32.dp))
            } else if (state is VoiceUi.WaitingForTv) {
                ShapeBox(polygon = Polygons.Cookie, color = scheme.secondaryContainer, modifier = Modifier.size(72.dp))
                Icon(Icons.Rounded.Mic, contentDescription = null, tint = scheme.onSecondaryContainer, modifier = Modifier.size(32.dp))
            } else {
                LoadingMorph(color = scheme.primary, size = 64.dp)
            }
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val (title, body) = when (state) {
                is VoiceUi.Listening -> "Listening…" to "Speak now. The results show on your TV."
                VoiceUi.WaitingForTv -> "Press a mic on the TV" to "No voice search on this TV, but apps like YouTube can hear you here."
                else -> "Starting voice search" to "Waiting for your TV"
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = scheme.onSurface
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant
            )
        }

        IconSurface(
            icon = Icons.Rounded.Stop,
            description = "Stop listening",
            onClick = onStop,
            size = 56.dp,
            color = scheme.surfaceContainerHighest,
            contentColor = scheme.onSurface
        )
    }
}
