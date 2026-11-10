package com.ryzen.tvremote.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Gamepad
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Swipe
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun TutorialSheet(onDismiss: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    RemoteSheet(
        title = "Quick tour",
        subtitle = "Four things worth knowing.",
        onDismiss = onDismiss
    ) {
        Group {
            GroupRow(
                0, 4,
                title = "Swipe to move",
                subtitle = "Swipe anywhere on the pad. Keep your finger down to keep going. Tap to select, hold for more options.",
                icon = Icons.Rounded.Swipe,
                iconBackground = scheme.primaryContainer,
                iconTint = scheme.onPrimaryContainer
            )
            GroupRow(
                1, 4,
                title = "Prefer buttons?",
                subtitle = "Switch to D-pad above the pad. Hold an arrow to keep moving.",
                icon = Icons.Rounded.Gamepad,
                iconBackground = scheme.tertiaryContainer,
                iconTint = scheme.onTertiaryContainer
            )
            GroupRow(
                2, 4,
                title = "Jump straight to an app",
                subtitle = "Tap a shortcut to open it on the TV. Press and hold one to edit, move or remove it.",
                icon = Icons.Rounded.Apps,
                iconBackground = scheme.primaryContainer,
                iconTint = scheme.onPrimaryContainer
            )
            GroupRow(
                3, 4,
                title = "Type with your phone",
                subtitle = "The keyboard button sends searches and passwords to the TV as you type.",
                icon = Icons.Rounded.Keyboard,
                iconBackground = scheme.tertiaryContainer,
                iconTint = scheme.onTertiaryContainer
            )
        }
        Spacer(Modifier.height(20.dp))
        ExpressiveButton("Got it", onDismiss, modifier = Modifier.fillMaxWidth())
    }
}
