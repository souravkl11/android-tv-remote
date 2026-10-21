package com.ryzen.tvremote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.ryzen.tvremote.protocol.RemoteKey
import com.ryzen.tvremote.ui.theme.RemoteTheme

@Composable
fun ActionButtonsRow(
    onKey: (RemoteKey) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(56.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Back Button
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(RemoteTheme.colors.buttonSurface)
                .clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onKey(RemoteKey.BACK)
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = RemoteTheme.colors.textPrimary,
                modifier = Modifier.size(28.dp)
            )
        }

        // Home Button
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(RemoteTheme.colors.buttonSurface)
                .clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onKey(RemoteKey.HOME)
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Home,
                contentDescription = "Home",
                tint = RemoteTheme.colors.textPrimary,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}
