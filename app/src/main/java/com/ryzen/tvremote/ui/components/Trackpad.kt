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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ryzen.tvremote.protocol.KeyDirection
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
    onKey: (RemoteKey, KeyDirection) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    val maxRadiusPx = with(density) { 95.dp.toPx() }
    val triggerThresholdPx = with(density) { 34.dp.toPx() }
    val centerThresholdPx = with(density) { 48.dp.toPx() }

    val ballOffsetX = remember { Animatable(0f) }
    val ballOffsetY = remember { Animatable(0f) }

    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    var isDragging by remember { mutableStateOf(false) }
    var isPressed by remember { mutableStateOf(false) }
    var isLongPressed by remember { mutableStateOf(false) }
    var pressedArrow by remember { mutableStateOf<RemoteKey?>(null) }
    var activeRepeatDirection by remember { mutableStateOf<RemoteKey?>(null) }

    val ballScale by animateFloatAsState(
        targetValue = when {
            isDragging -> 1.08f
            isLongPressed -> 0.88f
            isPressed -> 0.94f
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
            .onSizeChanged { boxSize = it }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    isPressed = true
                    var isDrag = false
                    var repeatJob: Job? = null
                    var longPressJob: Job? = null
                    var isLongPressActive = false

                    val centerX = if (boxSize.width > 0) boxSize.width / 2f else 0f
                    val centerY = if (boxSize.height > 0) boxSize.height / 2f else 0f
                    val touchStartX = down.position.x
                    val touchStartY = down.position.y
                    val initialDxFromCenter = touchStartX - centerX
                    val initialDyFromCenter = touchStartY - centerY
                    val initialDistFromCenter = sqrt(initialDxFromCenter * initialDxFromCenter + initialDyFromCenter * initialDyFromCenter)

                    val isTouchOnCenter = initialDistFromCenter <= centerThresholdPx

                    if (isTouchOnCenter) {
                        // Start long-press timer on OK button
                        longPressJob = coroutineScope.launch {
                            delay(400L)
                            if (!isDrag && isActive) {
                                isLongPressActive = true
                                isLongPressed = true
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onKey(RemoteKey.DPAD_CENTER, KeyDirection.START_LONG)
                            }
                        }
                    } else {
                        // Highlight tapped arrow direction
                        val initialTarget = if (abs(initialDxFromCenter) > abs(initialDyFromCenter)) {
                            if (initialDxFromCenter > 0) RemoteKey.DPAD_RIGHT else RemoteKey.DPAD_LEFT
                        } else {
                            if (initialDyFromCenter > 0) RemoteKey.DPAD_DOWN else RemoteKey.DPAD_UP
                        }
                        pressedArrow = initialTarget
                    }

                    var accumulatedX = 0f
                    var accumulatedY = 0f
                    val touchSlop = viewConfiguration.touchSlop

                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break

                            if (!change.pressed) {
                                change.consume()
                                longPressJob?.cancel()
                                longPressJob = null

                                if (isLongPressActive) {
                                    onKey(RemoteKey.DPAD_CENTER, KeyDirection.END_LONG)
                                    isLongPressActive = false
                                    isLongPressed = false
                                } else if (!isDrag) {
                                    if (isTouchOnCenter) {
                                        // Tap on center OK
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onKey(RemoteKey.DPAD_CENTER, KeyDirection.SHORT)
                                    } else {
                                        // Tap on directional arrow
                                        val tapDirection = if (abs(initialDxFromCenter) > abs(initialDyFromCenter)) {
                                            if (initialDxFromCenter > 0) RemoteKey.DPAD_RIGHT else RemoteKey.DPAD_LEFT
                                        } else {
                                            if (initialDyFromCenter > 0) RemoteKey.DPAD_DOWN else RemoteKey.DPAD_UP
                                        }
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onKey(tapDirection, KeyDirection.SHORT)
                                    }
                                }
                                pressedArrow = null
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
                                pressedArrow = null
                                longPressJob?.cancel()
                                longPressJob = null
                                if (isLongPressActive) {
                                    onKey(RemoteKey.DPAD_CENTER, KeyDirection.END_LONG)
                                    isLongPressActive = false
                                    isLongPressed = false
                                }
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
                                            onKey(targetDirection, KeyDirection.SHORT)
                                            delay(320L)

                                            while (isActive) {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                onKey(targetDirection, KeyDirection.SHORT)
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
                        longPressJob?.cancel()
                        longPressJob = null
                        if (isLongPressActive) {
                            onKey(RemoteKey.DPAD_CENTER, KeyDirection.END_LONG)
                            isLongPressActive = false
                            isLongPressed = false
                        }
                        pressedArrow = null
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
        // Directional Guide Chevrons with interactive touch highlight
        val baseGuideTint = RemoteTheme.colors.textSecondary.copy(alpha = 0.28f)
        val activeGuideTint = RemoteTheme.colors.accentGreen

        // Up Arrow
        val isUpActive = pressedArrow == RemoteKey.DPAD_UP || activeRepeatDirection == RemoteKey.DPAD_UP
        Icon(
            imageVector = Icons.Default.KeyboardArrowUp,
            contentDescription = "Up",
            tint = if (isUpActive) activeGuideTint else baseGuideTint,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 18.dp)
                .size(32.dp)
                .scale(if (isUpActive) 1.2f else 1.0f)
        )

        // Down Arrow
        val isDownActive = pressedArrow == RemoteKey.DPAD_DOWN || activeRepeatDirection == RemoteKey.DPAD_DOWN
        Icon(
            imageVector = Icons.Default.KeyboardArrowDown,
            contentDescription = "Down",
            tint = if (isDownActive) activeGuideTint else baseGuideTint,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 18.dp)
                .size(32.dp)
                .scale(if (isDownActive) 1.2f else 1.0f)
        )

        // Left Arrow
        val isLeftActive = pressedArrow == RemoteKey.DPAD_LEFT || activeRepeatDirection == RemoteKey.DPAD_LEFT
        Icon(
            imageVector = Icons.Default.KeyboardArrowLeft,
            contentDescription = "Left",
            tint = if (isLeftActive) activeGuideTint else baseGuideTint,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 18.dp)
                .size(32.dp)
                .scale(if (isLeftActive) 1.2f else 1.0f)
        )

        // Right Arrow
        val isRightActive = pressedArrow == RemoteKey.DPAD_RIGHT || activeRepeatDirection == RemoteKey.DPAD_RIGHT
        Icon(
            imageVector = Icons.Default.KeyboardArrowRight,
            contentDescription = "Right",
            tint = if (isRightActive) activeGuideTint else baseGuideTint,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 18.dp)
                .size(32.dp)
                .scale(if (isRightActive) 1.2f else 1.0f)
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
