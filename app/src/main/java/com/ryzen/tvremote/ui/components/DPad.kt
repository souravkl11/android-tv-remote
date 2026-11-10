package com.ryzen.tvremote.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.KeyboardArrowUp
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
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
import com.ryzen.tvremote.ui.theme.Motion
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val OkSize = 112.dp

/**
 * D-pad mode: a ring with four arrow segments and a cookie-shaped OK button in the middle.
 * Tap an arrow to step, hold it to repeat. Tap OK to select, hold OK to long-press.
 */
@Composable
fun DPad(
    onKey: (RemoteKey, KeyDirection) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = rememberHaptic()
    val scope = rememberCoroutineScope()
    val okRadiusPx = with(LocalDensity.current) { (OkSize / 2).toPx() }

    var pressed by remember { mutableStateOf<RemoteKey?>(null) }
    var okLong by remember { mutableStateOf(false) }

    val scheme = MaterialTheme.colorScheme
    val okDown = pressed == RemoteKey.DPAD_CENTER

    // OK: cookie relaxes into a circle, shrinks and twists while held.
    val okMorph = animateFloatAsState(if (okDown) 1f else 0f, Motion.fastSpatial(), label = "okMorph")
    val okScale = animateFloatAsState(
        targetValue = when {
            okLong -> 0.84f
            okDown -> 0.92f
            else -> 1f
        },
        animationSpec = Motion.fastSpatial(),
        label = "okScale"
    )
    val okColor by animateColorAsState(
        targetValue = if (okLong) scheme.tertiary else scheme.primary,
        animationSpec = Motion.effects(),
        label = "okColor"
    )
    val segmentGlow = animateFloatAsState(
        targetValue = if (pressed != null && !okDown) 1f else 0f,
        animationSpec = Motion.effects(),
        label = "segmentGlow"
    )
    var lastSegment by remember { mutableStateOf(RemoteKey.DPAD_UP) }
    if (pressed != null && !okDown) lastSegment = pressed!!

    val highlight = scheme.primary

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(scheme.surfaceContainerLow)
            .semantics { contentDescription = "Directional pad" }
            .drawBehind {
                val outer = size.minDimension / 2f
                val c = Offset(size.width / 2f, size.height / 2f)
                val gap = 10.dp.toPx()
                val inner = okRadiusPx + gap

                // Well around the OK button.
                drawCircle(scheme.surfaceContainerHigh, radius = inner, center = c)

                val glow = segmentGlow.value
                if (glow > 0.01f) {
                    val startAngle = when (lastSegment) {
                        RemoteKey.DPAD_RIGHT -> -45f
                        RemoteKey.DPAD_DOWN -> 45f
                        RemoteKey.DPAD_LEFT -> 135f
                        else -> 225f
                    }
                    val mid = (outer + inner) / 2f
                    drawArc(
                        color = highlight.copy(alpha = 0.20f * glow),
                        startAngle = startAngle + 3f,
                        sweepAngle = 84f,
                        useCenter = false,
                        topLeft = Offset(c.x - mid, c.y - mid),
                        size = Size(mid * 2, mid * 2),
                        style = Stroke(width = outer - inner - gap)
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
        Chevron(Icons.Rounded.KeyboardArrowUp, "Up", pressed == RemoteKey.DPAD_UP, Modifier.align(Alignment.TopCenter))
        Chevron(Icons.Rounded.KeyboardArrowDown, "Down", pressed == RemoteKey.DPAD_DOWN, Modifier.align(Alignment.BottomCenter))
        Chevron(Icons.Rounded.KeyboardArrowLeft, "Left", pressed == RemoteKey.DPAD_LEFT, Modifier.align(Alignment.CenterStart))
        Chevron(Icons.Rounded.KeyboardArrowRight, "Right", pressed == RemoteKey.DPAD_RIGHT, Modifier.align(Alignment.CenterEnd))

        MorphBox(
            from = Polygons.Cookie,
            to = Polygons.Circle,
            progress = { okMorph.value },
            rotation = { okMorph.value * 22.5f },
            color = okColor,
            modifier = Modifier
                .size(OkSize)
                .graphicsLayer {
                    scaleX = okScale.value
                    scaleY = okScale.value
                }
        ) {
            Text(
                text = "OK",
                color = scheme.onPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
private fun Chevron(icon: ImageVector, description: String, active: Boolean, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val amount = animateFloatAsState(if (active) 1f else 0f, Motion.fastSpatial(), label = "chevron")
    val tint by animateColorAsState(
        targetValue = if (active) scheme.primary else scheme.onSurfaceVariant,
        animationSpec = Motion.effects(),
        label = "chevronTint"
    )
    Icon(
        imageVector = icon,
        contentDescription = description,
        tint = tint,
        modifier = modifier
            .padding(16.dp)
            .size(40.dp)
            .graphicsLayer {
                val s = 1f + 0.25f * amount.value
                scaleX = s
                scaleY = s
            }
    )
}
