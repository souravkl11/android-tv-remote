package com.ryzen.tvremote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ryzen.tvremote.protocol.DiscoveredDevice
import com.ryzen.tvremote.ui.theme.RemoteTheme

@OptIn(ExperimentalMaterial3Api::class)
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
    val colors = RemoteTheme.colors
    var ipInput by remember { mutableStateOf(currentIp) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.cardSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Choose your TV", color = colors.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                if (isScanning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp).padding(2.dp),
                        color = colors.accent,
                        strokeWidth = 2.dp
                    )
                } else {
                    IconButton(onClick = onScanClick) {
                        Icon(Icons.Default.Refresh, contentDescription = "Scan again", tint = colors.accent)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (isScanning) "Looking for TVs on your Wi-Fi…" else "TVs found on your Wi-Fi",
                color = colors.textSecondary,
                fontSize = 13.sp
            )
            Spacer(Modifier.height(12.dp))

            if (discoveredDevices.isEmpty()) {
                Text(
                    text = if (isScanning) "Searching…" else "No TVs found. Make sure the TV is on and on the same Wi-Fi, or enter its IP below.",
                    color = colors.textSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                discoveredDevices.forEach { device ->
                    val isConnected = device.host == connectedIp
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(colors.buttonSurface)
                            .clickable(role = Role.Button) { onConnect(device.host, device.name) }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.Tv, contentDescription = null, tint = colors.accent, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(device.name, color = colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            Text(device.host, color = colors.textSecondary, fontSize = 12.sp)
                        }
                        if (isConnected) {
                            Icon(Icons.Default.Check, contentDescription = "Connected", tint = colors.accent, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Text("Or enter an IP address", color = colors.textSecondary, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = ipInput,
                onValueChange = { ipInput = it.trim() },
                singleLine = true,
                placeholder = { Text("192.168.1.100", color = colors.textSecondary) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.accent,
                    unfocusedBorderColor = colors.divider,
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary,
                    cursorColor = colors.accent
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { if (ipInput.isNotEmpty()) onConnect(ipInput, null) }),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { onConnect(ipInput, null) },
                enabled = ipInput.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.background),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text("Connect", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
