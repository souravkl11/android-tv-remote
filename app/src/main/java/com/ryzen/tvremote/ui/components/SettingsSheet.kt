package com.ryzen.tvremote.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ryzen.tvremote.ui.ControlMode
import com.ryzen.tvremote.ui.theme.RemoteTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    controlMode: ControlMode,
    isAmoled: Boolean,
    hapticsEnabled: Boolean,
    volumeKeysControlTv: Boolean,
    versionName: String,
    onControlModeChange: (ControlMode) -> Unit,
    onAmoledChange: (Boolean) -> Unit,
    onHapticsChange: (Boolean) -> Unit,
    onVolumeKeysChange: (Boolean) -> Unit,
    onAddApp: () -> Unit,
    onSwitchTv: () -> Unit,
    onRePair: () -> Unit,
    onTutorial: () -> Unit,
    onCheckUpdate: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = RemoteTheme.colors

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.cardSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp)
        ) {
            Text("Settings", color = colors.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)

            Section("Controls")
            Text("Default control mode", color = colors.textPrimary, fontSize = 15.sp)
            Spacer(Modifier.height(8.dp))
            ModeSwitch(mode = controlMode, onModeChange = onControlModeChange)
            Spacer(Modifier.height(4.dp))
            ToggleRow("Haptic feedback", "Vibrate on button presses", hapticsEnabled, onHapticsChange)
            ToggleRow("Phone volume buttons", "Use them to change the TV volume", volumeKeysControlTv, onVolumeKeysChange)

            Section("Appearance")
            ToggleRow("True AMOLED black", "Pure black background to save battery on OLED", isAmoled, onAmoledChange)

            Section("Apps & TV")
            ActionRow("Add or manage apps", "Long-press an app on the remote to edit or reorder it", onAddApp)
            ActionRow("Switch TV / scan", null, onSwitchTv)
            ActionRow("Re-pair with TV", "Use this if the remote stops connecting", onRePair)

            Section("Help")
            ActionRow("How to use", null, onTutorial)
            ActionRow("Check for updates", "Version $versionName", onCheckUpdate)
        }
    }
}

@Composable
private fun Section(title: String) {
    Spacer(Modifier.height(20.dp))
    Text(
        text = title.uppercase(),
        color = RemoteTheme.colors.accent,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp
    )
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val colors = RemoteTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Switch) { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = colors.textPrimary, fontSize = 15.sp)
            Text(subtitle, color = colors.textSecondary, fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.background,
                checkedTrackColor = colors.accent
            )
        )
    }
}

@Composable
private fun ActionRow(title: String, subtitle: String?, onClick: () -> Unit) {
    val colors = RemoteTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick),
        verticalArrangement = Arrangement.Center
    ) {
        Text(title, color = colors.textPrimary, fontSize = 15.sp)
        if (subtitle != null) Text(subtitle, color = colors.textSecondary, fontSize = 12.sp)
        HorizontalDivider(modifier = Modifier.padding(top = 10.dp), color = colors.divider)
    }
}
