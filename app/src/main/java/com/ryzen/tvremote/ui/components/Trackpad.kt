package com.ryzen.tvremote.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ryzen.tvremote.protocol.RemoteKey
import com.ryzen.tvremote.ui.theme.RemoteTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

@Composable
fun Trackpad(
    onKey: (RemoteKey) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    val maxRadiusPx = with(density) { 95.dp.toPx() }
    val triggerThresholdPx = with(density) { 34.dp.toPx() }

    val ballOffsetX = remember { Animatable(0f) }
    val ballOffsetY = remember { Animatable(0f) }

    var isDragging by remember { mutableStateOf(false) }
    var isPressed by remember { mutableStateOf(false) }

    val ballScale by animateFloatAsState(
        targetValue = when {
            isDragging -> 1.08f
            isPressed -> 0.93f
            else -> 1.0f
        },
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 800f),
        label = "ballScale"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(44.dp))
            .background(RemoteTheme.colors.trackpadSurface)
            .then(
                if (RemoteTheme.colors.trackpadBorder != Color.Transparent) {
                    Modifier.border(1.5.dp, RemoteTheme.colors.trackpadBorder, RoundedCornerShape(44.dp))
                } else Modifier
            )
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    isPressed = true
                    var isDrag = false
                    var activeRepeatDirection: RemoteKey? = null
                    var repeatJob: Job? = null
                    var accumulatedX = 0f
                    var accumulatedY = 0f
                    val touchSlop = viewConfiguration.touchSlop

                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break

                            if (!change.pressed) {
                                change.consume()
                                if (!isDrag) {
                                    // Tap acts as DPAD Center / Select
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onKey(RemoteKey.DPAD_CENTER)
                                }
                                break
                            }

                            val positionChange = change.positionChange()
                            val deltaX = positionChange.x
                            val deltaY = positionChange.y
                            accumulatedX += deltaX
                            accumulatedY += deltaY

                            val distFromStart = sqrt(accumulatedX * accumulatedX + accumulatedY * accumulatedY)

                            if (!isDrag && distFromStart > touchSlop) {
                                isDrag = true
                                isDragging = true
                            }

                            if (isDrag) {
                                change.consume()

                                val targetX = ballOffsetX.value + deltaX
                                val targetY = ballOffsetY.value + deltaY
                                val dist = sqrt(targetX * targetX + targetY * targetY)

                                val (clampedX, clampedY) = if (dist > maxRadiusPx) {
                                    val ratio = maxRadiusPx / dist
                                    Pair(targetX * ratio, targetY * ratio)
                                } else {
                                    Pair(targetX, targetY)
                                }

                                coroutineScope.launch {
                                    ballOffsetX.snapTo(clampedX)
                                    ballOffsetY.snapTo(clampedY)
                                }

                                // Directional control with continuous repeat when held
                                if (dist >= triggerThresholdPx) {
                                    val targetDirection = if (abs(clampedX) > abs(clampedY)) {
                                        if (clampedX > 0) RemoteKey.DPAD_RIGHT else RemoteKey.DPAD_LEFT
                                    } else {
                                        if (clampedY > 0) RemoteKey.DPAD_DOWN else RemoteKey.DPAD_UP
                                    }

                                    if (activeRepeatDirection != targetDirection) {
                                        activeRepeatDirection = targetDirection
                                        repeatJob?.cancel()
                                        repeatJob = coroutineScope.launch {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onKey(targetDirection)
                                            delay(320L)

                                            while (isActive) {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                onKey(targetDirection)
                                                delay(175L)
                                            }
                                        }
                                    }
                                } else if (dist < triggerThresholdPx * 0.6f) {
                                    activeRepeatDirection = null
                                    repeatJob?.cancel()
                                    repeatJob = null
                                }
                            }
                        }
                    } finally {
                        repeatJob?.cancel()
                        repeatJob = null
                        activeRepeatDirection = null
                        isPressed = false
                        isDragging = false
                        // Smooth physical snap-back spring to center
                        coroutineScope.launch {
                            launch {
                                ballOffsetX.animateTo(
                                    0f,
                                    spring(dampingRatio = 0.65f, stiffness = 650f)
                                )
                            }
                            launch {
                                ballOffsetY.animateTo(
                                    0f,
                                    spring(dampingRatio = 0.65f, stiffness = 650f)
                                )
                            }
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // Faint Directional Guide Chevrons
        val guideTint = Color.White.copy(alpha = 0.12f)
        Icon(
            imageVector = Icons.Default.KeyboardArrowUp,
            contentDescription = null,
            tint = guideTint,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 18.dp)
                .size(28.dp)
        )
        Icon(
            imageVector = Icons.Default.KeyboardArrowDown,
            contentDescription = null,
            tint = guideTint,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 18.dp)
                .size(28.dp)
        )
        Icon(
            imageVector = Icons.Default.KeyboardArrowLeft,
            contentDescription = null,
            tint = guideTint,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 18.dp)
                .size(28.dp)
        )
        Icon(
            imageVector = Icons.Default.KeyboardArrowRight,
            contentDescription = null,
            tint = guideTint,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 18.dp)
                .size(28.dp)
        )

        // Center Thumbstick & D-pad Center Action
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        ballOffsetX.value.roundToInt(),
                        ballOffsetY.value.roundToInt()
                    )
                }
                .size(76.dp)
                .scale(ballScale)
                .shadow(elevation = if (isDragging) 10.dp else 4.dp, shape = CircleShape)
                .clip(CircleShape)
                .background(RemoteTheme.colors.trackpadDot),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "OK",
                color = Color(0xFF1E2025),
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }
    }
}
