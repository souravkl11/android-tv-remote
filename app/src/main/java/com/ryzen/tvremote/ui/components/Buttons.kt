package com.ryzen.tvremote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ryzen.tvremote.ui.theme.RemoteTheme

/** Round icon button with an optional caption underneath. Touch target is never below 48dp. */
@Composable
fun RoundButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    label: String? = null,
    tint: Color = RemoteTheme.colors.textPrimary,
    background: Color = RemoteTheme.colors.buttonSurface,
    enabled: Boolean = true
) {
    val haptic = rememberHaptic()
    Column(
        modifier = modifier.alpha(if (enabled) 1f else 0.4f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(maxOf(size, 48.dp))
                .clip(CircleShape)
                .background(background)
                .clickable(enabled = enabled, role = Role.Button, onClickLabel = description) {
                    haptic(HapticFeedbackType.TextHandleMove)
                    onClick()
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = description,
                tint = tint,
                modifier = Modifier.size(size * 0.43f)
            )
        }
        if (label != null) {
            Text(
                text = label,
                color = RemoteTheme.colors.textSecondary,
                fontSize = 12.sp
            )
        }
    }
}
