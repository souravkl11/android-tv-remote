package com.ryzen.tvremote.ui.components

import com.ryzen.tvremote.protocol.RemoteKey
import kotlin.math.abs
import kotlin.math.sqrt

/** Pure geometry helpers shared by the Swipe pad and the D-pad (no Android dependencies). */
object SwipeMath {

    /** Dominant-axis direction for a movement vector (screen coordinates, y grows downward). */
    fun direction(dx: Float, dy: Float): RemoteKey =
        if (abs(dx) > abs(dy)) {
            if (dx > 0) RemoteKey.DPAD_RIGHT else RemoteKey.DPAD_LEFT
        } else {
            if (dy > 0) RemoteKey.DPAD_DOWN else RemoteKey.DPAD_UP
        }

    fun distance(dx: Float, dy: Float): Float = sqrt(dx * dx + dy * dy)

    fun exceeds(dx: Float, dy: Float, threshold: Float): Boolean = distance(dx, dy) >= threshold

    /**
     * Which D-pad zone a touch at offset ([dx], [dy]) from the centre falls in.
     * Returns DPAD_CENTER inside [centerRadius], otherwise the dominant direction.
     */
    fun dpadZone(dx: Float, dy: Float, centerRadius: Float): RemoteKey =
        if (distance(dx, dy) <= centerRadius) RemoteKey.DPAD_CENTER else direction(dx, dy)
}
