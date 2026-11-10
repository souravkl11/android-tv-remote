package com.ryzen.tvremote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Swipe
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ryzen.tvremote.ui.ControlMode
import com.ryzen.tvremote.ui.theme.RemoteTheme

/** Compact segmented control: Swipe | D-pad. */
@Composable
fun ModeSwitch(
    mode: ControlMode,
    onModeChange: (ControlMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(RemoteTheme.colors.buttonSurface)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Segment("Swipe", Icons.Default.Swipe, mode == ControlMode.SWIPE) { onModeChange(ControlMode.SWIPE) }
        Segment("D-pad", Icons.Default.Gamepad, mode == ControlMode.DPAD) { onModeChange(ControlMode.DPAD) }
    }
}

@Composable
private fun Segment(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    val colors = RemoteTheme.colors
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) colors.accent.copy(alpha = 0.22f) else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) colors.accent else colors.textSecondary,
            modifier = Modifier.size(18.dp)
        )
        Row(modifier = Modifier.width(6.dp)) {}
        Text(
            text = label,
            color = if (selected) colors.textPrimary else colors.textSecondary,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}
