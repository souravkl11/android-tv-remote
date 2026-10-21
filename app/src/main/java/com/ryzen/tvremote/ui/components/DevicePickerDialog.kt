package com.ryzen.tvremote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ryzen.tvremote.protocol.DiscoveredDevice
import com.ryzen.tvremote.ui.theme.RemoteTheme

@Composable
fun DevicePickerDialog(
    currentIp: String,
    discoveredDevices: List<DiscoveredDevice>,
    isScanning: Boolean,
    isAdbMode: Boolean,
    onScanClick: () -> Unit,
    onProtocolChange: (Boolean) -> Unit,
    onConnect: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var ipInput by remember { mutableStateOf(currentIp) }
    var useAdb by remember { mutableStateOf(isAdbMode) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = RemoteTheme.colors.cardSurface,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Select Android TV",
                    color = RemoteTheme.colors.textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                if (isScanning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = RemoteTheme.colors.accentGreen,
                        strokeWidth = 2.dp
                    )
                } else {
                    IconButton(
                        onClick = onScanClick,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Scan",
                            tint = RemoteTheme.colors.accentGreen,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Discovered on Wi-Fi:",
                    color = RemoteTheme.colors.textSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (discoveredDevices.isEmpty()) {
                    Text(
                        text = if (isScanning) "Searching for TVs on local network..." else "No TVs automatically found yet.",
                        color = RemoteTheme.colors.textSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                    ) {
                        items(discoveredDevices) { device ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(RemoteTheme.colors.buttonSurface)
                                    .clickable {
                                        ipInput = device.host
                                        onConnect(device.host, device.name)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Outlined.Tv,
                                        contentDescription = null,
                                        tint = RemoteTheme.colors.accentGreen,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = device.name,
                                            color = RemoteTheme.colors.textPrimary,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = device.host,
                                            color = RemoteTheme.colors.textSecondary,
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                if (ipInput == device.host) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = RemoteTheme.colors.accentGreen,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Manual TV IP Address:",
                    color = RemoteTheme.colors.textSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = ipInput,
                    onValueChange = { ipInput = it.trim() },
                    singleLine = true,
                    placeholder = { Text("192.168.1.100", color = RemoteTheme.colors.textSecondary) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = RemoteTheme.colors.accentGreen,
                        unfocusedBorderColor = RemoteTheme.colors.buttonSurface,
                        focusedTextColor = RemoteTheme.colors.textPrimary,
                        unfocusedTextColor = RemoteTheme.colors.textPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Protocol:",
                    color = RemoteTheme.colors.textSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            useAdb = false
                            onProtocolChange(false)
                        }
                ) {
                    RadioButton(
                        selected = !useAdb,
                        onClick = {
                            useAdb = false
                            onProtocolChange(false)
                        },
                        colors = RadioButtonDefaults.colors(selectedColor = RemoteTheme.colors.accentGreen)
                    )
                    Text("Android TV Remote v2 (TLS, Official)", color = RemoteTheme.colors.textPrimary, fontSize = 13.sp)
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            useAdb = true
                            onProtocolChange(true)
                        }
                ) {
                    RadioButton(
                        selected = useAdb,
                        onClick = {
                            useAdb = true
                            onProtocolChange(true)
                        },
                        colors = RadioButtonDefaults.colors(selectedColor = RemoteTheme.colors.accentGreen)
                    )
                    Text("ADB Wi-Fi (Port 5555)", color = RemoteTheme.colors.textPrimary, fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConnect(ipInput, "Living Room TV")
                },
                enabled = ipInput.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = RemoteTheme.colors.accentGreen,
                    contentColor = RemoteTheme.colors.background
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Connect", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = RemoteTheme.colors.textSecondary)
            }
        }
    )
}
