package com.ryzen.tvremote.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ryzen.tvremote.protocol.KeyDirection
import com.ryzen.tvremote.protocol.RemoteKey
import com.ryzen.tvremote.ui.theme.RemoteTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Swipe mode: one big surface. Swipe to move (one step per ~76dp of travel, hold still to repeat slowly),
 * tap to press OK, press and hold to long-press OK.
 */
@Composable
fun SwipePad(
    onKey: (RemoteKey, KeyDirection) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = rememberHaptic()
    val scope = rememberCoroutineScope()
    val stepPx = with(LocalDensity.current) { 76.dp.toPx() }
    val glowPx = with(LocalDensity.current) { 130.dp.toPx() }

    var touch by remember { mutableStateOf<Offset?>(null) }
    var longPressed by remember { mutableStateOf(false) }
    var hasInteracted by remember { mutableStateOf(false) }
    var flashDir by remember { mutableStateOf<RemoteKey?>(null) }
    val flashAlpha = remember { Animatable(0f) }

    val accent = RemoteTheme.colors.accent
    val shape = RoundedCornerShape(32.dp)

    Box(
        modifier = modifier
            .clip(shape)
            .background(RemoteTheme.colors.trackpadSurface)
            .then(
                if (RemoteTheme.colors.trackpadBorder != Color.Transparent)
                    Modifier.border(1.5.dp, RemoteTheme.colors.trackpadBorder, shape)
                else Modifier
            )
            .semantics { contentDescription = "Swipe pad. Swipe to navigate, tap to select, hold to long press." }
            .drawBehind {
                touch?.let { p ->
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                accent.copy(alpha = if (longPressed) 0.40f else 0.24f),
                                Color.Transparent
                            ),
                            center = p,
                            radius = glowPx
                        ),
                        radius = glowPx,
                        center = p
                    )
                }
            }
            .pointerInput(stepPx) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    hasInteracted = true
                    touch = down.position
                    var anchor = down.position
                    var moved = false
                    var repeatJob: Job? = null
                    var lastDir: RemoteKey? = null
                    var longActive = false
                    val slop = viewConfiguration.touchSlop

                    val longJob = scope.launch {
                        delay(400L)
                        if (!moved) {
                            longActive = true
                            longPressed = true
                            haptic(HapticFeedbackType.LongPress)
                            onKey(RemoteKey.DPAD_CENTER, KeyDirection.START_LONG)
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
                            touch = change.position
                            val dx = change.position.x - anchor.x
                            val dy = change.position.y - anchor.y

                            if (!moved && !longActive && SwipeMath.exceeds(dx, dy, slop)) {
                                moved = true
                                longJob.cancel()
                            }
                            if (!moved) continue
                            change.consume()

                            // Finger drifting a different way than the held repeat: stop repeating.
                            if (repeatJob != null &&
                                SwipeMath.exceeds(dx, dy, slop * 2) &&
                                SwipeMath.direction(dx, dy) != lastDir
                            ) {
                                repeatJob.cancel()
                                repeatJob = null
                            }

                            if (SwipeMath.exceeds(dx, dy, stepPx)) {
                                val dir = SwipeMath.direction(dx, dy)
                                lastDir = dir
                                anchor = change.position
                                haptic(HapticFeedbackType.TextHandleMove)
                                onKey(dir, KeyDirection.SHORT)
                                flashDir = dir
                                scope.launch {
                                    flashAlpha.snapTo(1f)
                                    flashAlpha.animateTo(0f, tween(350))
                                }
                                // Holding still after a swipe keeps stepping in that direction.
                                repeatJob?.cancel()
                                repeatJob = scope.launchKeyRepeat(initialDelayMs = 550L, intervalMs = 300L) {
                                    haptic(HapticFeedbackType.TextHandleMove)
                                    onKey(dir, KeyDirection.SHORT)
                                }
                            }
                        }

                        longJob.cancel()
                        if (longActive) {
                            longActive = false
                            onKey(RemoteKey.DPAD_CENTER, KeyDirection.END_LONG)
                        } else if (!moved) {
                            haptic(HapticFeedbackType.LongPress)
                            onKey(RemoteKey.DPAD_CENTER, KeyDirection.SHORT)
                        }
                    } finally {
                        longJob.cancel()
                        repeatJob?.cancel()
                        if (longActive) {
                            // Gesture cancelled mid long-press: still release the TV key.
                            onKey(RemoteKey.DPAD_CENTER, KeyDirection.END_LONG)
                        }
                        longPressed = false
                        touch = null
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (!hasInteracted) {
            Text(
                text = "Swipe to navigate\nTap to select\nHold for more",
                color = RemoteTheme.colors.textSecondary.copy(alpha = 0.7f),
                fontSize = 15.sp,
                lineHeight = 24.sp,
                textAlign = TextAlign.Center
            )
        }

        flashDir?.let { dir ->
            val (icon, align) = when (dir) {
                RemoteKey.DPAD_UP -> Icons.Default.KeyboardArrowUp to Alignment.TopCenter
                RemoteKey.DPAD_DOWN -> Icons.Default.KeyboardArrowDown to Alignment.BottomCenter
                RemoteKey.DPAD_LEFT -> Icons.Default.KeyboardArrowLeft to Alignment.CenterStart
                else -> Icons.Default.KeyboardArrowRight to Alignment.CenterEnd
            }
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier
                    .align(align)
                    .padding(12.dp)
                    .size(56.dp)
                    .alpha(flashAlpha.value)
            )
        }
    }
}
