package com.ryzen.tvremote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.ryzen.tvremote.update.AppUpdateInfo

@Composable
fun UpdateDialog(
    updateInfo: AppUpdateInfo,
    currentVersion: String,
    onDownload: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    RemoteSheet(
        title = "Update available",
        subtitle = "Version ${updateInfo.latestVersion} is out. You have $currentVersion.",
        onDismiss = onDismiss
    ) {
        if (updateInfo.releaseNotes.isNotBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 180.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(scheme.surfaceContainerHigh)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                Text(
                    text = updateInfo.releaseNotes.trim(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(20.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ExpressiveButton("Later", onDismiss, tone = ButtonTone.Tonal, modifier = Modifier.weight(1f))
            ExpressiveButton(
                text = "Download",
                icon = Icons.Rounded.Download,
                onClick = { onDownload(updateInfo.apkDownloadUrl ?: updateInfo.releaseUrl) },
                modifier = Modifier.weight(1.4f)
            )
        }
    }
}
