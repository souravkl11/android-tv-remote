package com.ryzen.tvremote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ryzen.tvremote.protocol.ConnectionStatus
import com.ryzen.tvremote.ui.theme.RemoteTheme

/** Explains why the remote isn't responding and offers the next step. Hidden when connected. */
@Composable
fun ConnectionBanner(
    status: ConnectionStatus,
    onRetry: () -> Unit,
    onChooseTv: () -> Unit,
    onRePair: () -> Unit,
    onEnterCode: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = RemoteTheme.colors
    val isError = status is ConnectionStatus.Error

    val title = when (status) {
        is ConnectionStatus.Connecting -> status.message
        is ConnectionStatus.NeedsPairing -> "Enter the code shown on your TV"
        is ConnectionStatus.Error -> status.message
        else -> "Not connected to a TV"
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (isError) colors.red.copy(alpha = 0.14f) else colors.buttonSurface)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (status is ConnectionStatus.Connecting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = colors.warning,
                    strokeWidth = 2.dp
                )
            }
            Text(
                text = title,
                color = if (isError) colors.red else colors.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
        }

        if (status !is ConnectionStatus.Connecting) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (status is ConnectionStatus.NeedsPairing) {
                    TextButton(onClick = onEnterCode) { Text("Enter code", color = colors.accent) }
                } else {
                    TextButton(onClick = onRetry) { Text("Retry", color = colors.accent) }
                    if (isError) {
                        TextButton(onClick = onRePair) { Text("Re-pair", color = colors.accent) }
                    }
                }
                TextButton(onClick = onChooseTv) { Text("Choose TV", color = colors.textSecondary) }
            }
        }
    }
}
