package com.ryzen.tvremote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ryzen.tvremote.protocol.RemoteKey
import com.ryzen.tvremote.ui.theme.RemoteTheme

/** Volume rocker: down | mute | up, with a thin level indicator along the bottom edge. */
@Composable
fun VolumePillRow(
    isMuted: Boolean,
    level: Int,
    max: Int,
    onKey: (RemoteKey) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = RemoteTheme.colors
    val fraction = if (max > 0) (level.toFloat() / max).coerceIn(0f, 1f) else 0f
    val indicatorColor = if (isMuted) colors.red else colors.accent

    Row(
        modifier = modifier
            .height(64.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(colors.buttonSurface)
            .drawBehind {
                if (fraction > 0f) {
                    val h = 3.dp.toPx()
                    drawRect(
                        color = indicatorColor.copy(alpha = 0.85f),
                        topLeft = Offset(0f, size.height - h),
                        size = Size(size.width * fraction, h)
                    )
                }
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        VolumeSegment(
            icon = Icons.AutoMirrored.Filled.VolumeDown,
            description = "Volume down",
            repeat = true,
            tint = colors.textPrimary,
            onTick = { onKey(RemoteKey.VOLUME_DOWN) },
            modifier = Modifier.weight(1f)
        )
        Divider()
        VolumeSegment(
            icon = Icons.AutoMirrored.Filled.VolumeOff,
            description = if (isMuted) "Unmute" else "Mute",
            repeat = false,
            tint = if (isMuted) colors.red else colors.textPrimary,
            onTick = { onKey(RemoteKey.VOLUME_MUTE) },
            modifier = Modifier.weight(1f)
        )
        Divider()
        VolumeSegment(
            icon = Icons.AutoMirrored.Filled.VolumeUp,
            description = "Volume up",
            repeat = true,
            tint = colors.textPrimary,
            onTick = { onKey(RemoteKey.VOLUME_UP) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun Divider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(28.dp)
            .background(RemoteTheme.colors.divider)
    )
}

@Composable
private fun VolumeSegment(
    icon: ImageVector,
    description: String,
    repeat: Boolean,
    tint: Color,
    onTick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = rememberHaptic()
    val scope = rememberCoroutineScope()
    var isPressed by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .background(if (isPressed) RemoteTheme.colors.buttonHighlight else Color.Transparent)
            .semantics { contentDescription = description }
            .pointerInput(repeat) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    isPressed = true
                    val job = if (repeat) {
                        scope.launchKeyRepeat(intervalMs = 140L, fireFirst = true) {
                            haptic(HapticFeedbackType.TextHandleMove)
                            onTick()
                        }
                    } else {
                        haptic(HapticFeedbackType.TextHandleMove)
                        onTick()
                        null
                    }
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
                        job?.cancel()
                        isPressed = false
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(26.dp)
        )
    }
}
