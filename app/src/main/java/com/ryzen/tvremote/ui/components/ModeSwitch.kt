package com.ryzen.tvremote.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Gamepad
import androidx.compose.material.icons.rounded.Swipe
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.ryzen.tvremote.ui.ControlMode
import com.ryzen.tvremote.ui.theme.Motion

data class SegmentOption<T>(val value: T, val label: String, val icon: ImageVector? = null)

/** Segmented control whose highlight slides between options on a spring. */
@Composable
fun <T> SegmentedPills(
    options: List<SegmentOption<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 48.dp,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainer
) {
    val scheme = MaterialTheme.colorScheme
    val haptic = rememberHaptic()
    val index = options.indexOfFirst { it.value == selected }.coerceAtLeast(0)

    BoxWithConstraints(
        modifier = modifier
            .height(height)
            .clip(CircleShape)
            .background(trackColor)
            .padding(4.dp)
    ) {
        val segment = maxWidth / options.size
        val x by animateDpAsState(segment * index, Motion.spatial(), label = "segmentX")

        Box(
            modifier = Modifier
                .offset { IntOffset(x.roundToPx(), 0) }
                .width(segment)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(scheme.secondaryContainer)
        )

        Row(modifier = Modifier.fillMaxSize().selectableGroup()) {
            options.forEach { option ->
                val isSelected = option.value == selected
                val tint by animateColorAsState(
                    targetValue = if (isSelected) scheme.onSecondaryContainer else scheme.onSurfaceVariant,
                    animationSpec = Motion.effects(),
                    label = "segmentTint"
                )
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .selectable(
                            selected = isSelected,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.RadioButton
                        ) {
                            if (!isSelected) {
                                haptic(HapticFeedbackType.TextHandleMove)
                                onSelect(option.value)
                            }
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
                ) {
                    if (option.icon != null) {
                        Icon(option.icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
                    }
                    Text(option.label, color = tint, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                }
            }
        }
    }
}

private val ModeOptions = listOf(
    SegmentOption(ControlMode.SWIPE, "Swipe", Icons.Rounded.Swipe),
    SegmentOption(ControlMode.DPAD, "D-pad", Icons.Rounded.Gamepad)
)

/** Swipe | D-pad. */
@Composable
fun ModeSwitch(
    mode: ControlMode,
    onModeChange: (ControlMode) -> Unit,
    modifier: Modifier = Modifier,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainer
) {
    SegmentedPills(
        options = ModeOptions,
        selected = mode,
        onSelect = onModeChange,
        modifier = modifier.width(232.dp),
        height = 44.dp,
        trackColor = trackColor
    )
}
