package com.ryzen.tvremote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ryzen.tvremote.protocol.RemoteKey
import com.ryzen.tvremote.ui.theme.RemoteTheme
import kotlinx.coroutines.delay

@Composable
fun KeyboardDialog(
    onSendText: (String) -> Unit,
    onKey: (RemoteKey) -> Unit,
    onDismiss: () -> Unit
) {
    var textInput by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(150)
        focusRequester.requestFocus()
    }

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
                    text = "Type on TV",
                    color = RemoteTheme.colors.textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                // Live Sync badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(RemoteTheme.colors.accentGreen)
                    )
                    Text(
                        text = "Live Sync",
                        color = RemoteTheme.colors.accentGreen,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Characters and backspaces sync to your TV in real-time as you type.",
                    color = RemoteTheme.colors.textSecondary,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = textInput,
                    onValueChange = { newText ->
                        val oldText = textInput

                        // Find common prefix length
                        var commonPrefixLen = 0
                        while (commonPrefixLen < oldText.length &&
                            commonPrefixLen < newText.length &&
                            oldText[commonPrefixLen] == newText[commonPrefixLen]
                        ) {
                            commonPrefixLen++
                        }

                        // Send backspaces for removed characters
                        val charsToRemove = oldText.length - commonPrefixLen
                        repeat(charsToRemove) {
                            onKey(RemoteKey.BACKSPACE)
                        }

                        // Send newly added characters
                        val charsToAdd = newText.substring(commonPrefixLen)
                        if (charsToAdd.isNotEmpty()) {
                            onSendText(charsToAdd)
                        }

                        textInput = newText
                    },
                    singleLine = false,
                    maxLines = 3,
                    placeholder = { Text("Start typing...", color = RemoteTheme.colors.textSecondary) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = RemoteTheme.colors.accentGreen,
                        unfocusedBorderColor = RemoteTheme.colors.buttonSurface,
                        focusedTextColor = RemoteTheme.colors.textPrimary,
                        unfocusedTextColor = RemoteTheme.colors.textPrimary
                    ),
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Search
                    ),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            onKey(RemoteKey.ENTER)
                            onDismiss()
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            if (textInput.isNotEmpty()) {
                                textInput = textInput.dropLast(1)
                            }
                            onKey(RemoteKey.BACKSPACE)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RemoteTheme.colors.buttonSurface,
                            contentColor = RemoteTheme.colors.textPrimary
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("⌫ Del", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }

                    Button(
                        onClick = {
                            val count = textInput.length
                            repeat(count) {
                                onKey(RemoteKey.BACKSPACE)
                            }
                            textInput = ""
                        },
                        enabled = textInput.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RemoteTheme.colors.buttonSurface,
                            contentColor = RemoteTheme.colors.textSecondary
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Clear", fontSize = 14.sp)
                    }

                    Button(
                        onClick = {
                            onKey(RemoteKey.ENTER)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RemoteTheme.colors.accentGreen,
                            contentColor = RemoteTheme.colors.background
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Search ⏎", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = RemoteTheme.colors.buttonSurface,
                    contentColor = RemoteTheme.colors.textPrimary
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Done")
            }
        }
    )
}
