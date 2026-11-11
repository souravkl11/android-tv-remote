package com.ryzen.tvremote.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Gamepad
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.ryzen.tvremote.ui.ControlMode
import com.ryzen.tvremote.ui.ThemeMode
import com.ryzen.tvremote.ui.theme.supportsDynamicColor

private val ThemeOptions = listOf(
    SegmentOption(ThemeMode.SYSTEM, "System"),
    SegmentOption(ThemeMode.LIGHT, "Light"),
    SegmentOption(ThemeMode.DARK, "Dark")
)

@Composable
fun SettingsSheet(
    controlMode: ControlMode,
    themeMode: ThemeMode,
    isDark: Boolean,
    pureBlack: Boolean,
    dynamicColor: Boolean,
    hapticsEnabled: Boolean,
    volumeKeysControlTv: Boolean,
    autoKeyboard: Boolean,
    versionName: String,
    onControlModeChange: (ControlMode) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onPureBlackChange: (Boolean) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onHapticsChange: (Boolean) -> Unit,
    onVolumeKeysChange: (Boolean) -> Unit,
    onAutoKeyboardChange: (Boolean) -> Unit,
    onAddApp: () -> Unit,
    onSwitchTv: () -> Unit,
    onRePair: () -> Unit,
    onTutorial: () -> Unit,
    onCheckUpdate: () -> Unit,
    onDismiss: () -> Unit
) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest

    RemoteSheet(title = "Settings", onDismiss = onDismiss) {
        Group {
            GroupRow(
                index = 0, count = 4,
                title = "Control style",
                subtitle = "How you move around the TV",
                icon = Icons.Rounded.Gamepad,
                below = {
                    ModeSwitch(controlMode, onControlModeChange, Modifier.fillMaxWidth(), trackColor = track)
                }
            )
            ToggleRow(1, 4, Icons.Rounded.Vibration, "Haptic feedback", "A light tick on every press", hapticsEnabled, onHapticsChange)
            ToggleRow(2, 4, Icons.AutoMirrored.Rounded.VolumeUp, "Phone volume buttons", "Change the TV volume, not the phone's", volumeKeysControlTv, onVolumeKeysChange)
            ToggleRow(3, 4, Icons.Rounded.Keyboard, "Keyboard pops up", "Opens when the TV asks for text, like search or a code", autoKeyboard, onAutoKeyboardChange)
        }

        SectionLabel("Look")
        val lookCount = 2 + if (supportsDynamicColor) 1 else 0
        Group {
            GroupRow(
                index = 0, count = lookCount,
                title = "Theme",
                icon = Icons.Rounded.DarkMode,
                below = {
                    SegmentedPills(ThemeOptions, themeMode, onThemeModeChange, Modifier.fillMaxWidth(), height = 44.dp, trackColor = track)
                }
            )
            ToggleRow(
                1, lookCount, Icons.Rounded.Contrast, "Pure black",
                if (isDark) "True black background for OLED screens" else "Only applies to the dark theme",
                pureBlack, onPureBlackChange, enabled = isDark
            )
            if (supportsDynamicColor) {
                ToggleRow(2, lookCount, Icons.Rounded.Palette, "Wallpaper colours", "Match the colours of your phone", dynamicColor, onDynamicColorChange)
            }
        }

        SectionLabel("TV and apps")
        Group {
            NavRow(0, 3, Icons.Rounded.Apps, "Add an app shortcut", "Press and hold a shortcut to edit, move or remove it", onAddApp)
            NavRow(1, 3, Icons.Rounded.Tv, "Switch TV", "Scan your Wi-Fi or connect by IP address", onSwitchTv)
            NavRow(2, 3, Icons.Rounded.Link, "Pair again", "Fixes a remote that stopped connecting", onRePair)
        }

        SectionLabel("About")
        Group {
            NavRow(0, 2, Icons.AutoMirrored.Rounded.HelpOutline, "Quick tour", "Gestures and tips", onTutorial)
            NavRow(1, 2, Icons.Rounded.SystemUpdate, "Check for updates", "Version $versionName", onCheckUpdate)
        }
    }
}

@Composable
private fun ToggleRow(
    index: Int,
    count: Int,
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    GroupRow(
        index = index, count = count,
        title = title, subtitle = subtitle, icon = icon,
        role = Role.Switch,
        onClick = if (enabled) ({ onChange(!checked) }) else null,
        trailing = {
            Switch(
                checked = checked && enabled,
                onCheckedChange = null,
                enabled = enabled,
                thumbContent = if (checked && enabled) {
                    { Icon(Icons.Rounded.Check, null, modifier = Modifier.size(SwitchDefaults.IconSize)) }
                } else null
            )
        }
    )
}

@Composable
private fun NavRow(index: Int, count: Int, icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    GroupRow(
        index = index, count = count,
        title = title, subtitle = subtitle, icon = icon,
        onClick = onClick,
        trailing = {
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        }
    )
}
