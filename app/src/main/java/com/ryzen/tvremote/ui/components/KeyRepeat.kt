package com.ryzen.tvremote.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Lets the settings screen turn all haptics off in one place. */
val LocalHapticsEnabled = compositionLocalOf { true }

@Composable
fun rememberHaptic(): (HapticFeedbackType) -> Unit {
    val haptic = LocalHapticFeedback.current
    val enabled = LocalHapticsEnabled.current
    return remember(haptic, enabled) {
        { type -> if (enabled) haptic.performHapticFeedback(type) }
    }
}

/**
 * Calls [tick] repeatedly while the returned job is alive: after [initialDelayMs], then every
 * [intervalMs]. If [fireFirst] is true, [tick] also runs once immediately.
 */
fun CoroutineScope.launchKeyRepeat(
    initialDelayMs: Long = 320L,
    intervalMs: Long = 150L,
    fireFirst: Boolean = false,
    tick: () -> Unit
): Job = launch {
    if (fireFirst) tick()
    delay(initialDelayMs)
    while (isActive) {
        tick()
        delay(intervalMs)
    }
}
