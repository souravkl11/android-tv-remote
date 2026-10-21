package com.ryzen.tvremote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ryzen.tvremote.ui.theme.RemoteTheme

@Composable
fun PairingPinDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = RemoteTheme.colors.cardSurface,
        title = {
            Text(
                text = "Pair with Android TV",
                color = RemoteTheme.colors.textPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Enter the 6-character pairing code shown on your TV screen:",
                    color = RemoteTheme.colors.textSecondary,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = pin,
                    onValueChange = { input ->
                        val filtered = input.filter { it.isLetterOrDigit() }.uppercase()
                        if (filtered.length <= 6) {
                            pin = filtered
                            errorText = null
                        }
                    },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = RemoteTheme.colors.textPrimary,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center,
                        letterSpacing = 6.sp
                    ),
                    placeholder = {
                        Text(
                            text = "••••••",
                            color = RemoteTheme.colors.textSecondary,
                            fontSize = 26.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = RemoteTheme.colors.accentGreen,
                        unfocusedBorderColor = RemoteTheme.colors.buttonSurface,
                        cursorColor = RemoteTheme.colors.accentGreen
                    ),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (pin.length == 6) onConfirm(pin)
                        }
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorText != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorText ?: "",
                        color = RemoteTheme.colors.red,
                        fontSize = 12.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (pin.length == 6) {
                        onConfirm(pin)
                    } else {
                        errorText = "Please enter all 6 characters"
                    }
                },
                enabled = pin.length == 6,
                colors = ButtonDefaults.buttonColors(
                    containerColor = RemoteTheme.colors.accentGreen,
                    contentColor = RemoteTheme.colors.background
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Pair", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = RemoteTheme.colors.textSecondary)
            }
        }
    )
}
