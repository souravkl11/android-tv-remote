package com.ryzen.tvremote.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Swipe
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ryzen.tvremote.protocol.KeyDirection
import com.ryzen.tvremote.protocol.RemoteKey
import com.ryzen.tvremote.ui.theme.Motion
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sqrt

/**
 * Swipe mode: one big surface. Swipe to move (one step per ~76dp of travel, hold still to repeat
 * slowly), tap to press OK, press and hold to long-press OK.
 *
 * The surface is a field of dots that swell and light up around the finger, so the pad feels
 * alive and always shows exactly where the touch is.
 */
@Composable
fun SwipePad(
    onKey: (RemoteKey, KeyDirection) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = rememberHaptic()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val stepPx = with(density) { 76.dp.toPx() }

    var touchPos by remember { mutableStateOf(Offset.Zero) }
    var touching by remember { mutableStateOf(false) }
    var longPressed by remember { mutableStateOf(false) }
    var hasInteracted by rememberSaveable { mutableStateOf(false) }

    var flashDir by remember { mutableStateOf<RemoteKey?>(null) }
    val flash = remember { Animatable(0f) }
    var tapPos by remember { mutableStateOf(Offset.Zero) }
    val tapPulse = remember { Animatable(1f) }

    val presence = animateFloatAsState(
        targetValue = if (touching) 1f else 0f,
        animationSpec = if (touching) Motion.fastSpatial() else Motion.slowEffects(),
        label = "presence"
    )
    val hold = animateFloatAsState(if (longPressed) 1f else 0f, Motion.spatial(), label = "hold")

    val scheme = MaterialTheme.colorScheme
    val dotRest = scheme.onSurface.copy(alpha = 0.10f)
    val dotHot = scheme.primary
    val shape = RoundedCornerShape(40.dp)

    Box(
        modifier = modifier
            .clip(shape)
            .background(scheme.surfaceContainerLow)
            .semantics { contentDescription = "Swipe pad. Swipe to navigate, tap to select, hold to long press." }
            .drawBehind {
                val gap = 26.dp.toPx()
                val baseRadius = 1.6.dp.toPx()
                val reach = (110.dp.toPx()) * (1f + 0.5f * hold.value)
                val strength = presence.value.coerceIn(0f, 1.3f)
                val push = 7.dp.toPx()

                val cols = (size.width / gap).toInt()
                val rows = (size.height / gap).toInt()
                val startX = (size.width - (cols - 1) * gap) / 2f
                val startY = (size.height - (rows - 1) * gap) / 2f

                for (row in 0 until rows) {
                    val y = startY + row * gap
                    for (col in 0 until cols) {
                        val x = startX + col * gap
                        if (strength <= 0.01f) {
                            drawCircle(dotRest, baseRadius, Offset(x, y))
                            continue
                        }
                        val dx = x - touchPos.x
                        val dy = y - touchPos.y
                        val dist = sqrt(dx * dx + dy * dy)
                        val near = (1f - dist / reach).coerceIn(0f, 1f)
                        val k = near * near * strength
                        if (k <= 0.002f) {
                            drawCircle(dotRest, baseRadius, Offset(x, y))
                        } else {
                            // Dots near the finger grow, brighten and lean away from it.
                            val away = if (dist > 0.5f) push * k / dist else 0f
                            drawCircle(
                                color = lerp(dotRest, dotHot, k.coerceAtMost(1f)),
                                radius = baseRadius * (1f + 2.4f * k),
                                center = Offset(x + dx * away, y + dy * away)
                            )
                        }
                    }
                }

                // Ring that expands from a tap: "that was an OK press".
                val pulse = tapPulse.value
                if (pulse < 1f) {
                    drawCircle(
                        color = dotHot.copy(alpha = (1f - pulse) * 0.7f),
                        radius = 18.dp.toPx() + 70.dp.toPx() * pulse,
                        center = tapPos,
                        style = Stroke(width = 3.dp.toPx() * (1f - pulse) + 1f)
                    )
                }
            }
            .pointerInput(stepPx) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    hasInteracted = true
                    touchPos = down.position
                    touching = true
                    var anchor = down.position
                    var moved = false
                    var repeatJob: Job? = null
                    var lastDir: RemoteKey? = null
                    var longActive = false
                    val slop = viewConfiguration.touchSlop

                    fun step(dir: RemoteKey) {
                        haptic(HapticFeedbackType.TextHandleMove)
                        onKey(dir, KeyDirection.SHORT)
                        flashDir = dir
                        scope.launch {
                            flash.snapTo(1f)
                            flash.animateTo(0f, tween(420))
                        }
                    }

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
                            touchPos = change.position
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
                                step(dir)
                                // Holding still after a swipe keeps stepping in that direction.
                                repeatJob?.cancel()
                                repeatJob = scope.launchKeyRepeat(initialDelayMs = 550L, intervalMs = 300L) {
                                    step(dir)
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
                            tapPos = touchPos
                            scope.launch {
                                tapPulse.snapTo(0f)
                                tapPulse.animateTo(1f, tween(480))
                            }
                        }
                    } finally {
                        longJob.cancel()
                        repeatJob?.cancel()
                        if (longActive) {
                            // Gesture cancelled mid long-press: still release the TV key.
                            onKey(RemoteKey.DPAD_CENTER, KeyDirection.END_LONG)
                        }
                        longPressed = false
                        touching = false
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        EdgeChevron(Icons.Rounded.KeyboardArrowUp, RemoteKey.DPAD_UP, flashDir, { flash.value }, Modifier.align(Alignment.TopCenter))
        EdgeChevron(Icons.Rounded.KeyboardArrowDown, RemoteKey.DPAD_DOWN, flashDir, { flash.value }, Modifier.align(Alignment.BottomCenter))
        EdgeChevron(Icons.Rounded.KeyboardArrowLeft, RemoteKey.DPAD_LEFT, flashDir, { flash.value }, Modifier.align(Alignment.CenterStart))
        EdgeChevron(Icons.Rounded.KeyboardArrowRight, RemoteKey.DPAD_RIGHT, flashDir, { flash.value }, Modifier.align(Alignment.CenterEnd))

        AnimatedVisibility(visible = !hasInteracted, enter = fadeIn(), exit = fadeOut()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Hint(Icons.Rounded.Swipe, "Swipe to move")
                Hint(Icons.Rounded.TouchApp, "Tap to select · hold for more")
            }
        }
    }
}

@Composable
private fun Hint(icon: ImageVector, text: String) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(scheme.surfaceContainerHigh)
            .padding(start = 12.dp, end = 16.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(18.dp))
        Text(text, color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
    }
}

/** Faint arrow on each edge; the one matching the last step lights up and kicks outward. */
@Composable
private fun EdgeChevron(
    icon: ImageVector,
    key: RemoteKey,
    flashDir: RemoteKey?,
    flash: () -> Float,
    modifier: Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val active = flashDir == key
    val kick = with(LocalDensity.current) { 8.dp.toPx() }
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = if (active) lerp(scheme.onSurfaceVariant.copy(alpha = 0.32f), scheme.primary, flash().coerceIn(0f, 1f))
        else scheme.onSurfaceVariant.copy(alpha = 0.32f),
        modifier = modifier
            .padding(10.dp)
            .size(32.dp)
            .graphicsLayer {
                if (active) {
                    val f = flash()
                    val s = 1f + 0.5f * f
                    scaleX = s
                    scaleY = s
                    when (key) {
                        RemoteKey.DPAD_UP -> translationY = -kick * f
                        RemoteKey.DPAD_DOWN -> translationY = kick * f
                        RemoteKey.DPAD_LEFT -> translationX = -kick * f
                        else -> translationX = kick * f
                    }
                }
            }
    )
}
