package com.ryzen.tvremote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ryzen.tvremote.ui.theme.RemoteTheme
import com.ryzen.tvremote.update.AppUpdateInfo

@Composable
fun UpdateDialog(
    updateInfo: AppUpdateInfo,
    currentVersion: String,
    onDownload: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = RemoteTheme.colors.cardSurface,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SystemUpdate,
                    contentDescription = null,
                    tint = RemoteTheme.colors.accentGreen,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Update Available",
                    color = RemoteTheme.colors.textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "A new version (v${updateInfo.latestVersion}) is available. You are currently on v$currentVersion.",
                    color = RemoteTheme.colors.textSecondary,
                    fontSize = 14.sp
                )

                if (updateInfo.releaseNotes.isNotBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Release Notes:",
                        color = RemoteTheme.colors.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 140.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(RemoteTheme.colors.buttonSurface)
                            .padding(10.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = updateInfo.releaseNotes.trim(),
                            color = RemoteTheme.colors.textSecondary,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val targetUrl = updateInfo.apkDownloadUrl ?: updateInfo.releaseUrl
                    onDownload(targetUrl)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = RemoteTheme.colors.accentGreen,
                    contentColor = RemoteTheme.colors.background
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = "Download",
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Later",
                    color = RemoteTheme.colors.textSecondary
                )
            }
        }
    )
}
