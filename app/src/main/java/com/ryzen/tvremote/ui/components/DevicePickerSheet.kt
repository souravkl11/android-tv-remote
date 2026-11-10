package com.ryzen.tvremote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.WifiFind
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ryzen.tvremote.protocol.DiscoveredDevice
import com.ryzen.tvremote.ui.theme.RemoteTheme

@Composable
fun DevicePickerSheet(
    currentIp: String,
    connectedIp: String?,
    discoveredDevices: List<DiscoveredDevice>,
    isScanning: Boolean,
    onScanClick: () -> Unit,
    onConnect: (ip: String, name: String?) -> Unit,
    onDismiss: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    var ipInput by remember { mutableStateOf(currentIp) }

    RemoteSheet(
        title = "Choose your TV",
        subtitle = if (isScanning) "Looking on your Wi-Fi…" else "TVs on your Wi-Fi",
        onDismiss = onDismiss,
        action = {
            if (isScanning) {
                Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                    LoadingMorph(color = scheme.primary, size = 32.dp)
                }
            } else {
                IconSurface(Icons.Rounded.Refresh, "Scan again", onScanClick, color = scheme.surfaceContainerHighest)
            }
        }
    ) {
        if (discoveredDevices.isEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(scheme.surfaceContainerHigh)
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Icon(Icons.Rounded.WifiFind, null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(28.dp))
                Text(
                    text = if (isScanning) "Searching for TVs…"
                    else "No TVs found. Check that the TV is on and on the same Wi-Fi as this phone, or enter its IP address below.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant
                )
            }
        } else {
            Group {
                discoveredDevices.forEachIndexed { i, device ->
                    val isConnected = device.host == connectedIp
                    GroupRow(
                        index = i,
                        count = discoveredDevices.size,
                        title = device.name,
                        subtitle = if (isConnected) "Connected · ${device.host}" else device.host,
                        icon = Icons.Rounded.Tv,
                        iconBackground = if (isConnected) RemoteTheme.status.success else scheme.secondaryContainer,
                        iconTint = if (isConnected) RemoteTheme.status.onSuccess else scheme.onSecondaryContainer,
                        onClick = { onConnect(device.host, device.name) },
                        trailing = {
                            if (isConnected) {
                                Icon(
                                    Icons.Rounded.CheckCircle,
                                    contentDescription = "Connected",
                                    tint = RemoteTheme.status.success,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    )
                }
            }
        }

        SectionLabel("Connect by IP address")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = ipInput,
                onValueChange = { ipInput = it.trim() },
                singleLine = true,
                placeholder = { Text("192.168.1.100") },
                shape = RoundedCornerShape(50),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { if (ipInput.isNotEmpty()) onConnect(ipInput, null) }),
                modifier = Modifier.weight(1f)
            )
            IconSurface(
                icon = Icons.AutoMirrored.Rounded.ArrowForward,
                description = "Connect",
                onClick = { onConnect(ipInput, null) },
                enabled = ipInput.isNotEmpty(),
                size = 56.dp,
                color = scheme.primary,
                contentColor = scheme.onPrimary
            )
        }
    }
}
