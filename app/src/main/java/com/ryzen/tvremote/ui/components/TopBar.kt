package com.ryzen.tvremote.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.TvOff
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ryzen.tvremote.protocol.ConnectionStatus
import com.ryzen.tvremote.protocol.KeyDirection
import com.ryzen.tvremote.ui.theme.Motion
import com.ryzen.tvremote.ui.theme.RemoteTheme

@Composable
fun TopBar(
    status: ConnectionStatus,
    deviceName: String,
    hasTv: Boolean,
    onDevicePickerClick: () -> Unit,
    onPowerKey: (KeyDirection) -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val connected = status is ConnectionStatus.Connected

    val statusLabel = when (status) {
        is ConnectionStatus.Connected -> "Connected"
        is ConnectionStatus.Connecting -> "Connecting…"
        is ConnectionStatus.NeedsPairing -> "Waiting for code"
        is ConnectionStatus.Error -> "Can't connect"
        is ConnectionStatus.Disconnected -> if (hasTv) "Not connected" else "Tap to choose"
    }
    val statusColor by animateColorAsState(
        targetValue = when (status) {
            is ConnectionStatus.Connected -> RemoteTheme.status.success
            is ConnectionStatus.Connecting, is ConnectionStatus.NeedsPairing -> RemoteTheme.status.warning
            is ConnectionStatus.Error -> scheme.error
            is ConnectionStatus.Disconnected -> scheme.onSurfaceVariant
        },
        animationSpec = Motion.effects(),
        label = "statusColor"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PressSurface(
            onClick = onDevicePickerClick,
            modifier = Modifier
                .weight(1f)
                .height(60.dp),
            description = "Choose TV. ${if (hasTv) deviceName else "No TV"}, $statusLabel"
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 9.dp, end = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusBadge(status)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (hasTv) deviceName else "No TV yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = scheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = statusColor,
                        maxLines = 1
                    )
                }
                Icon(
                    imageVector = Icons.Rounded.UnfoldMore,
                    contentDescription = null,
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Tap toggles power; hold for the TV's power menu.
        HoldableKey(
            icon = Icons.Rounded.PowerSettingsNew,
            description = "Power",
            longPressLabel = "Power menu",
            onKey = onPowerKey,
            size = 60.dp,
            iconSize = 26.dp,
            color = scheme.errorContainer,
            contentColor = scheme.onErrorContainer,
            enabled = connected
        )
        IconSurface(
            icon = Icons.Rounded.Settings,
            description = "Settings",
            onClick = onSettingsClick,
            size = 60.dp,
            iconSize = 24.dp
        )
    }
}

/** Circle that blooms into a cookie when the TV connects; a tumbling loader while it tries. */
@Composable
private fun StatusBadge(status: ConnectionStatus) {
    val scheme = MaterialTheme.colorScheme
    val connected = status is ConnectionStatus.Connected
    val busy = status is ConnectionStatus.Connecting || status is ConnectionStatus.NeedsPairing
    val isError = status is ConnectionStatus.Error

    val bloom = animateFloatAsState(if (connected) 1f else 0f, Motion.spatial(), label = "bloom")
    val container by animateColorAsState(
        targetValue = when {
            connected -> RemoteTheme.status.success
            isError -> scheme.errorContainer
            else -> scheme.surfaceContainerHighest
        },
        animationSpec = Motion.effects(),
        label = "badgeColor"
    )
    val tint = when {
        connected -> RemoteTheme.status.onSuccess
        isError -> scheme.onErrorContainer
        else -> scheme.onSurfaceVariant
    }

    MorphBox(
        from = Polygons.Circle,
        to = Polygons.Cookie,
        progress = { bloom.value },
        rotation = { bloom.value * 45f },
        color = container,
        modifier = Modifier.size(42.dp)
    ) {
        if (busy) {
            LoadingMorph(color = RemoteTheme.status.warning, size = 22.dp)
        } else {
            Icon(
                imageVector = if (isError) Icons.Rounded.TvOff else Icons.Rounded.Tv,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
