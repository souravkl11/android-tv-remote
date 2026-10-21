package com.ryzen.tvremote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
    onInputClick: () -> Unit,
    onKeyboardClick: () -> Unit,
    onPowerClick: () -> Unit,
    onRePairClick: () -> Unit,
    onProtocolSwitchClick: () -> Unit,
    onTutorialClick: () -> Unit,
    onCheckUpdateClick: () -> Unit,
    isAdbMode: Boolean,
    isAmoledMode: Boolean,
    onToggleAmoled: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: Dropdown chevron + Connection status + TV Name
        Row(
            modifier = Modifier
                .weight(1f, fill = false)
                .clip(RoundedCornerShape(8.dp))
                .clickable { onDevicePickerClick() }
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Select TV",
                tint = RemoteTheme.colors.textSecondary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))

            Column {
                val statusText = when (status) {
                    is ConnectionStatus.Connected -> "Connected"
                    is ConnectionStatus.Connecting -> "Connecting..."
                    is ConnectionStatus.NeedsPairing -> "Pairing required"
                    is ConnectionStatus.Disconnected -> "Disconnected"
                    is ConnectionStatus.Error -> "Offline"
                }

                val statusColor = when (status) {
                    is ConnectionStatus.Connected -> RemoteTheme.colors.textPrimary
                    is ConnectionStatus.Connecting -> RemoteTheme.colors.accentGreen
                    is ConnectionStatus.NeedsPairing -> RemoteTheme.colors.accentGreen
                    is ConnectionStatus.Disconnected -> RemoteTheme.colors.textSecondary
                    is ConnectionStatus.Error -> RemoteTheme.colors.red
                }

                Text(
                    text = statusText,
                    color = statusColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Tv,
                        contentDescription = null,
                        tint = if (status is ConnectionStatus.Connected) RemoteTheme.colors.accentGreen else RemoteTheme.colors.textSecondary,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = deviceName,
                        color = if (status is ConnectionStatus.Connected) RemoteTheme.colors.accentGreen else RemoteTheme.colors.textSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Right: Action buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // TV Input Source
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(RemoteTheme.colors.buttonSurface)
                    .border(1.5.dp, RemoteTheme.colors.inputPillBorder, CircleShape)
                    .clickable { onInputClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Tv,
                    contentDescription = "Input Source",
                    tint = RemoteTheme.colors.accentGreen,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Keyboard input
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(RemoteTheme.colors.buttonSurface)
                    .clickable { onKeyboardClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Keyboard,
                    contentDescription = "Keyboard",
                    tint = RemoteTheme.colors.textPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Power
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(RemoteTheme.colors.buttonSurface)
                    .clickable { onPowerClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PowerSettingsNew,
                    contentDescription = "Power",
                    tint = RemoteTheme.colors.textPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Overflow Menu (fixed size and full touch target)
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(RemoteTheme.colors.buttonSurface)
                    .clickable { menuExpanded = true },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Menu",
                    tint = RemoteTheme.colors.textPrimary,
                    modifier = Modifier.size(22.dp)
                )

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    modifier = Modifier.background(RemoteTheme.colors.cardSurface)
                ) {
                    DropdownMenuItem(
                        text = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("True AMOLED Mode", color = RemoteTheme.colors.textPrimary)
                                Spacer(modifier = Modifier.width(12.dp))
                                Switch(
                                    checked = isAmoledMode,
                                    onCheckedChange = { onToggleAmoled() },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = RemoteTheme.colors.accentGreen,
                                        checkedTrackColor = RemoteTheme.colors.buttonHighlight
                                    )
                                )
                            }
                        },
                        onClick = {
                            onToggleAmoled()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("How to Use (Guide)", color = RemoteTheme.colors.textPrimary) },
                        onClick = {
                            menuExpanded = false
                            onTutorialClick()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Check for Updates", color = RemoteTheme.colors.textPrimary) },
                        onClick = {
                            menuExpanded = false
                            onCheckUpdateClick()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Switch TV / Scan Devices", color = RemoteTheme.colors.textPrimary) },
                        onClick = {
                            menuExpanded = false
                            onDevicePickerClick()
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (isAdbMode) "Protocol: Switch to v2 (TLS)" else "Protocol: Switch to ADB (Port 5555)",
                                color = RemoteTheme.colors.textPrimary
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onProtocolSwitchClick()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Re-Pair with TV", color = RemoteTheme.colors.textPrimary) },
                        onClick = {
                            menuExpanded = false
                            onRePairClick()
                        }
                    )
                }
            }
        }
    }
}
