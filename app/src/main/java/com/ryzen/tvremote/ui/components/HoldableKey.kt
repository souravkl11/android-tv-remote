package com.ryzen.tvremote.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ryzen.tvremote.protocol.KeyDirection
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val HOLD_MS = 450L
/** The TV decides it was a long press from how long the key stays down, so keep it down at least this long. */
private const val MIN_TV_HOLD_MS = 1000L

/**
 * Round key that sends a tap as a short press and a hold as a real long press (key down until
 * release). A ring fills while holding so it's clear a long press is coming.
 */
@Composable
fun HoldableKey(
    icon: ImageVector,
    description: String,
    longPressLabel: String,
    onKey: (KeyDirection) -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 60.dp,
    iconSize: Dp = 26.dp,
    color: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    enabled: Boolean = true
) {
    val haptic = rememberHaptic()
    val scope = rememberCoroutineScope()
    var pressed by remember { mutableStateOf(false) }
    val ring = remember { Animatable(0f) }
    val ringColor = contentColor

    fun longPress() {
        onKey(KeyDirection.START_LONG)
        scope.launch {
            delay(MIN_TV_HOLD_MS)
            onKey(KeyDirection.END_LONG)
        }
    }

    Box(
        modifier = modifier
            .size(size)
            .alpha(if (enabled) 1f else 0.38f)
            .pressMorph(pressed, pressedCorner = 0.3f)
            .background(color)
            .drawBehind {
                val p = ring.value
                if (p > 0f) {
                    val stroke = 3.dp.toPx()
                    val inset = stroke / 2f + 4.dp.toPx()
                    drawArc(
                        color = ringColor,
                        startAngle = -90f,
                        sweepAngle = 360f * p,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = Size(this.size.width - inset * 2, this.size.height - inset * 2),
                        style = Stroke(stroke, cap = StrokeCap.Round)
                    )
                }
            }
            .semantics {
                contentDescription = description
                role = Role.Button
                if (enabled) {
                    onClick { onKey(KeyDirection.SHORT); true }
                    onLongClick(label = longPressLabel) { longPress(); true }
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown()
                    pressed = true
                    var longStartedAt = 0L
                    val ringJob: Job = scope.launch {
                        ring.snapTo(0f)
                        ring.animateTo(1f, tween(HOLD_MS.toInt(), easing = LinearEasing))
                        longStartedAt = System.currentTimeMillis()
                        haptic(HapticFeedbackType.LongPress)
                        onKey(KeyDirection.START_LONG)
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
                        pressed = false
                        ringJob.cancel()
                        val startedAt = longStartedAt
                        scope.launch {
                            if (startedAt > 0L) {
                                val left = MIN_TV_HOLD_MS - (System.currentTimeMillis() - startedAt)
                                if (left > 0) delay(left)
                                onKey(KeyDirection.END_LONG)
                            } else {
                                haptic(HapticFeedbackType.TextHandleMove)
                                onKey(KeyDirection.SHORT)
                            }
                            ring.animateTo(0f, tween(180))
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(iconSize))
    }
}
