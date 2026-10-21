package com.ryzen.tvremote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.ryzen.tvremote.protocol.RemoteKey
import com.ryzen.tvremote.ui.theme.RemoteTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
fun VolumePillRow(
    isMuted: Boolean,
    onKey: (RemoteKey) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    var isMutePressed by remember { mutableStateOf(false) }
    var isVolDownPressed by remember { mutableStateOf(false) }
    var isVolUpPressed by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp)
            .height(64.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(RemoteTheme.colors.buttonSurface),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Mute
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(if (isMutePressed) RemoteTheme.colors.buttonHighlight else Color.Transparent)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        isMutePressed = true
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onKey(RemoteKey.VOLUME_MUTE)

                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) {
                                    change.consume()
                                    break
                                }
                            }
                        } finally {
                            isMutePressed = false
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.VolumeOff,
                contentDescription = "Mute",
                tint = if (isMuted) RemoteTheme.colors.red else RemoteTheme.colors.textPrimary,
                modifier = Modifier.size(26.dp)
            )
        }

        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight(0.6f)
                .background(RemoteTheme.colors.divider)
        )

        // Volume Down
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(if (isVolDownPressed) RemoteTheme.colors.buttonHighlight else Color.Transparent)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        isVolDownPressed = true
                        var repeatJob: Job? = null

                        try {
                            repeatJob = coroutineScope.launch {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onKey(RemoteKey.VOLUME_DOWN)
                                delay(320L)

                                while (isActive) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onKey(RemoteKey.VOLUME_DOWN)
                                    delay(140L)
                                }
                            }

                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) {
                                    change.consume()
                                    break
                                }
                            }
                        } finally {
                            repeatJob?.cancel()
                            isVolDownPressed = false
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.VolumeDown,
                contentDescription = "Volume Down",
                tint = RemoteTheme.colors.textPrimary,
                modifier = Modifier.size(26.dp)
            )
        }

        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight(0.6f)
                .background(RemoteTheme.colors.divider)
        )

        // Volume Up
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(if (isVolUpPressed) RemoteTheme.colors.buttonHighlight else Color.Transparent)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        isVolUpPressed = true
                        var repeatJob: Job? = null

                        try {
                            repeatJob = coroutineScope.launch {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onKey(RemoteKey.VOLUME_UP)
                                delay(320L)

                                while (isActive) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onKey(RemoteKey.VOLUME_UP)
                                    delay(140L)
                                }
                            }

                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) {
                                    change.consume()
                                    break
                                }
                            }
                        } finally {
                            repeatJob?.cancel()
                            isVolUpPressed = false
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = "Volume Up",
                tint = RemoteTheme.colors.textPrimary,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}
