package com.ryzen.tvremote.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ryzen.tvremote.protocol.KeyDirection
import com.ryzen.tvremote.protocol.RemoteKey
import com.ryzen.tvremote.ui.theme.RemoteTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * D-pad mode: a classic ring with four arrow segments and a centre OK button.
 * Tap an arrow to step, hold it to repeat. Tap OK to select, hold OK to long-press.
 */
@Composable
fun DPad(
    onKey: (RemoteKey, KeyDirection) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = rememberHaptic()
    val scope = rememberCoroutineScope()
    val okRadiusPx = with(LocalDensity.current) { 46.dp.toPx() }

    var pressed by remember { mutableStateOf<RemoteKey?>(null) }
    var okLong by remember { mutableStateOf(false) }

    val colors = RemoteTheme.colors
    val okScale by animateFloatAsState(
        targetValue = when {
            okLong -> 0.88f
            pressed == RemoteKey.DPAD_CENTER -> 0.94f
            else -> 1f
        },
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 800f),
        label = "okScale"
    )

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(colors.trackpadSurface)
            .then(
                if (colors.trackpadBorder != Color.Transparent)
                    Modifier.border(1.5.dp, colors.trackpadBorder, CircleShape)
                else Modifier
            )
            .semantics { contentDescription = "Directional pad" }
            .drawBehind {
                val zone = pressed
                val startAngle = when (zone) {
                    RemoteKey.DPAD_RIGHT -> -45f
                    RemoteKey.DPAD_DOWN -> 45f
                    RemoteKey.DPAD_LEFT -> 135f
                    RemoteKey.DPAD_UP -> 225f
                    else -> null
                }
                if (startAngle != null) {
                    val outer = size.minDimension / 2f
                    val mid = (outer + okRadiusPx) / 2f
                    val c = Offset(size.width / 2f, size.height / 2f)
                    drawArc(
                        color = colors.accent.copy(alpha = 0.28f),
                        startAngle = startAngle,
                        sweepAngle = 90f,
                        useCenter = false,
                        topLeft = Offset(c.x - mid, c.y - mid),
                        size = Size(mid * 2, mid * 2),
                        style = Stroke(width = outer - okRadiusPx)
                    )
                }
            }
            .pointerInput(okRadiusPx) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val dx = down.position.x - size.width / 2f
                    val dy = down.position.y - size.height / 2f
                    val zone = SwipeMath.dpadZone(dx, dy, okRadiusPx)
                    pressed = zone

                    var repeatJob: Job? = null
                    var longJob: Job? = null
                    var longActive = false

                    if (zone == RemoteKey.DPAD_CENTER) {
                        longJob = scope.launch {
                            delay(400L)
                            longActive = true
                            okLong = true
                            haptic(HapticFeedbackType.LongPress)
                            onKey(RemoteKey.DPAD_CENTER, KeyDirection.START_LONG)
                        }
                    } else {
                        repeatJob = scope.launchKeyRepeat(fireFirst = true) {
                            haptic(HapticFeedbackType.TextHandleMove)
                            onKey(zone, KeyDirection.SHORT)
                        }
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
                        longJob?.cancel()
                        if (zone == RemoteKey.DPAD_CENTER) {
                            if (longActive) {
                                longActive = false
                                onKey(RemoteKey.DPAD_CENTER, KeyDirection.END_LONG)
                            } else {
                                haptic(HapticFeedbackType.LongPress)
                                onKey(RemoteKey.DPAD_CENTER, KeyDirection.SHORT)
                            }
                        }
                    } finally {
                        longJob?.cancel()
                        repeatJob?.cancel()
                        if (longActive) onKey(RemoteKey.DPAD_CENTER, KeyDirection.END_LONG)
                        okLong = false
                        pressed = null
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Chevron(Icons.Default.KeyboardArrowUp, "Up", pressed == RemoteKey.DPAD_UP, Modifier.align(Alignment.TopCenter))
        Chevron(Icons.Default.KeyboardArrowDown, "Down", pressed == RemoteKey.DPAD_DOWN, Modifier.align(Alignment.BottomCenter))
        Chevron(Icons.Default.KeyboardArrowLeft, "Left", pressed == RemoteKey.DPAD_LEFT, Modifier.align(Alignment.CenterStart))
        Chevron(Icons.Default.KeyboardArrowRight, "Right", pressed == RemoteKey.DPAD_RIGHT, Modifier.align(Alignment.CenterEnd))

        Box(
            modifier = Modifier
                .size(92.dp)
                .scale(okScale)
                .clip(CircleShape)
                .background(colors.trackpadDot),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "OK",
                color = Color(0xFF1E2025),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
private fun Chevron(icon: ImageVector, description: String, active: Boolean, modifier: Modifier) {
    Icon(
        imageVector = icon,
        contentDescription = description,
        tint = if (active) RemoteTheme.colors.accent else RemoteTheme.colors.textSecondary.copy(alpha = 0.6f),
        modifier = modifier
            .padding(14.dp)
            .size(40.dp)
            .scale(if (active) 1.2f else 1f)
    )
}
