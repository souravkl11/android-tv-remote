package com.ryzen.tvremote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ryzen.tvremote.protocol.ConnectionStatus
import com.ryzen.tvremote.ui.theme.RemoteTheme

@Composable
fun TopBar(
    status: ConnectionStatus,
    deviceName: String,
    onDevicePickerClick: () -> Unit,
    onPowerClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = RemoteTheme.colors
    val dotColor = when (status) {
        is ConnectionStatus.Connected -> colors.accent
        is ConnectionStatus.Connecting, is ConnectionStatus.NeedsPairing -> colors.warning
        is ConnectionStatus.Error -> colors.red
        is ConnectionStatus.Disconnected -> colors.textSecondary
    }
    val statusLabel = when (status) {
        is ConnectionStatus.Connected -> "Connected"
        is ConnectionStatus.Connecting -> "Connecting…"
        is ConnectionStatus.NeedsPairing -> "Pairing"
        is ConnectionStatus.Error -> "Offline"
        is ConnectionStatus.Disconnected -> "Not connected"
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Device chip: tap to pick a TV.
        Row(
            modifier = Modifier
                .weight(1f, fill = false)
                .heightIn(min = 48.dp)
                .clip(CircleShape)
                .background(colors.buttonSurface)
                .clickable(role = Role.Button, onClickLabel = "Choose TV", onClick = onDevicePickerClick)
                .padding(start = 16.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    text = deviceName,
                    color = colors.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = statusLabel,
                    color = colors.textSecondary,
                    fontSize = 11.sp,
                    maxLines = 1
                )
            }
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(Modifier.width(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundButton(
                icon = Icons.Default.PowerSettingsNew,
                description = "Power",
                onClick = onPowerClick,
                size = 48.dp,
                tint = colors.red
            )
            RoundButton(
                icon = Icons.Default.Settings,
                description = "Settings",
                onClick = onSettingsClick,
                size = 48.dp
            )
        }
    }
}
