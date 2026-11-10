package com.ryzen.tvremote.ui.theme

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring

/**
 * Material 3 Expressive motion: springs instead of fixed durations. "Spatial" springs move and
 * resize things and are allowed to overshoot; "effects" springs are for colour and opacity and
 * never bounce. The values are the expressive scheme's tokens.
 */
object Motion {
    fun <T> spatial(): SpringSpec<T> = spring(dampingRatio = 0.8f, stiffness = 380f)
    fun <T> fastSpatial(): SpringSpec<T> = spring(dampingRatio = 0.6f, stiffness = 800f)
    fun <T> slowSpatial(): SpringSpec<T> = spring(dampingRatio = 0.8f, stiffness = 200f)
    fun <T> effects(): SpringSpec<T> = spring(dampingRatio = 1f, stiffness = 1600f)
    fun <T> slowEffects(): SpringSpec<T> = spring(dampingRatio = 1f, stiffness = 800f)
}
