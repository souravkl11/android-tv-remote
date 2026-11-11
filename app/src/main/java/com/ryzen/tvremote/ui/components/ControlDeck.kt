package com.ryzen.tvremote.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Input
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ryzen.tvremote.protocol.RemoteKey
import com.ryzen.tvremote.ui.theme.Motion

private val KeyHeight = 64.dp

/**
 * Everything under the pad, sized for the thumb:
 *   Back · Home · Voice · Play/Pause
 *   Volume rocker · Keyboard · Input
 */
@Composable
fun ControlDeck(
    isMuted: Boolean,
    volumeLevel: Int,
    volumeMax: Int,
    enabled: Boolean,
    onKey: (RemoteKey) -> Unit,
    onKeyboardClick: () -> Unit,
    onVoiceClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NavKey(Icons.AutoMirrored.Rounded.ArrowBack, "Back", enabled, Modifier.weight(1f)) { onKey(RemoteKey.BACK) }
            NavKey(Icons.Rounded.Home, "Home", enabled, Modifier.weight(1f)) { onKey(RemoteKey.HOME) }
            IconSurface(
                icon = Icons.Rounded.Mic,
                description = "Voice search",
                onClick = onVoiceClick,
                size = KeyHeight,
                iconSize = 28.dp,
                color = scheme.primary,
                contentColor = scheme.onPrimary,
                enabled = enabled
            )
            PressSurface(
                onClick = { onKey(RemoteKey.MEDIA_PLAY_PAUSE) },
                modifier = Modifier
                    .width(KeyHeight + 12.dp)
                    .height(KeyHeight),
                color = scheme.primaryContainer,
                contentColor = scheme.onPrimaryContainer,
                enabled = enabled,
                description = "Play or pause"
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.size(24.dp))
                    Icon(Icons.Rounded.Pause, null, modifier = Modifier.size(24.dp).offset(x = (-5).dp))
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VolumeRocker(
                isMuted = isMuted,
                level = volumeLevel,
                max = volumeMax,
                enabled = enabled,
                onKey = onKey,
                modifier = Modifier.weight(1f)
            )
            IconSurface(
                icon = Icons.Rounded.Keyboard,
                description = "Type on TV",
                onClick = onKeyboardClick,
                size = KeyHeight,
                iconSize = 26.dp,
                enabled = enabled
            )
            IconSurface(
                icon = Icons.AutoMirrored.Rounded.Input,
                description = "Input source",
                onClick = { onKey(RemoteKey.TV_INPUT) },
                size = KeyHeight,
                iconSize = 26.dp,
                enabled = enabled
            )
        }
    }
}

@Composable
private fun NavKey(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    PressSurface(
        onClick = onClick,
        modifier = modifier.height(KeyHeight),
        enabled = enabled,
        description = label
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Text(label, style = MaterialTheme.typography.titleMedium, maxLines = 1)
        }
    }
}

/**
 * Volume rocker. The pill itself is the level meter: it fills from the left as the TV reports its
 * volume. Hold − or + to keep going; tap the middle to mute.
 */
@Composable
private fun VolumeRocker(
    isMuted: Boolean,
    level: Int,
    max: Int,
    enabled: Boolean,
    onKey: (RemoteKey) -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val target = if (max > 0) (level.toFloat() / max).coerceIn(0f, 1f) else 0f
    val fraction = animateFloatAsState(target, Motion.spatial(), label = "volume")
    val fill by animateColorAsState(
        targetValue = if (isMuted) scheme.error else scheme.primary,
        animationSpec = Motion.effects(),
        label = "volumeFill"
    )
    var anyPressed by remember { mutableStateOf(false) }
    val track = scheme.onSurface.copy(alpha = 0.10f)
    val showMeter = enabled && max > 0

    Row(
        modifier = modifier
            .height(KeyHeight)
            .alpha(if (enabled) 1f else 0.38f)
            .pressMorph(anyPressed, pressedCorner = 0.34f, pressedScale = 0.98f)
            .background(scheme.surfaceContainerHigh)
            .drawBehind {
                // Slim level meter along the bottom edge of the pill.
                if (showMeter) {
                    val inset = 28.dp.toPx()
                    val h = 4.dp.toPx()
                    val y = size.height - 9.dp.toPx() - h
                    val w = size.width - inset * 2
                    val r = CornerRadius(h / 2f)
                    drawRoundRect(track, Offset(inset, y), Size(w, h), r)
                    val f = fraction.value.coerceIn(0f, 1f)
                    if (f > 0f) drawRoundRect(fill, Offset(inset, y), Size((w * f).coerceAtLeast(h), h), r)
                }
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        RockerZone(
            description = "Volume down",
            repeat = true,
            enabled = enabled,
            onPressedChange = { anyPressed = it },
            onTick = { onKey(RemoteKey.VOLUME_DOWN) },
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Rounded.Remove, null, tint = scheme.onSurface, modifier = Modifier.size(26.dp))
        }
        RockerZone(
            description = if (isMuted) "Unmute" else "Mute",
            repeat = false,
            enabled = enabled,
            onPressedChange = { anyPressed = it },
            onTick = { onKey(RemoteKey.VOLUME_MUTE) },
            modifier = Modifier.weight(1.2f)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(
                    imageVector = when {
                        isMuted -> Icons.AutoMirrored.Rounded.VolumeOff
                        target < 0.5f -> Icons.AutoMirrored.Rounded.VolumeDown
                        else -> Icons.AutoMirrored.Rounded.VolumeUp
                    },
                    contentDescription = null,
                    tint = if (isMuted) scheme.error else scheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
                if (enabled && max > 0) {
                    Text(
                        text = if (isMuted) "Muted" else level.toString(),
                        color = if (isMuted) scheme.error else scheme.onSurface,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
        RockerZone(
            description = "Volume up",
            repeat = true,
            enabled = enabled,
            onPressedChange = { anyPressed = it },
            onTick = { onKey(RemoteKey.VOLUME_UP) },
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Rounded.Add, null, tint = scheme.onSurface, modifier = Modifier.size(26.dp))
        }
    }
}

@Composable
private fun RockerZone(
    description: String,
    repeat: Boolean,
    enabled: Boolean,
    onPressedChange: (Boolean) -> Unit,
    onTick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val haptic = rememberHaptic()
    val scope = rememberCoroutineScope()
    var isPressed by remember { mutableStateOf(false) }
    val overlay by animateColorAsState(
        targetValue = if (isPressed) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f) else Color.Transparent,
        animationSpec = Motion.effects(),
        label = "zoneOverlay"
    )

    Box(
        modifier = modifier
            .fillMaxHeight()
            .background(overlay)
            .semantics {
                contentDescription = description
                role = Role.Button
            }
            .pointerInput(repeat, enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    isPressed = true
                    onPressedChange(true)
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
                        onPressedChange(false)
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}
