package com.ryzen.tvremote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.ryzen.tvremote.protocol.RemoteKey

/**
 * Inline "type on TV" panel docked above the keyboard. Text is mirrored to the TV as you type:
 * new characters are sent, removed ones become backspaces.
 */
@Composable
fun TypePanel(
    onSendText: (String) -> Unit,
    onKey: (RemoteKey) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val haptic = rememberHaptic()
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    var text by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }

    fun close() {
        keyboard?.hide()
        onClose()
    }

    fun submit() {
        onKey(RemoteKey.ENTER)
        close()
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(scheme.surfaceContainer)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextField(
                value = text,
                onValueChange = { newText ->
                    // Send only what changed: backspaces for removed chars, then the new tail.
                    var common = 0
                    while (common < text.length && common < newText.length && text[common] == newText[common]) common++
                    repeat(text.length - common) { onKey(RemoteKey.BACKSPACE) }
                    val added = newText.substring(common)
                    if (added.isNotEmpty()) onSendText(added)
                    text = newText
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.titleMedium,
                placeholder = {
                    Text("Type here, it appears on the TV", style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                },
                shape = RoundedCornerShape(50),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = scheme.surfaceContainerHighest,
                    unfocusedContainerColor = scheme.surfaceContainerHighest,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { submit() }),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
            )
            IconSurface(
                icon = Icons.Rounded.Close,
                description = "Close typing",
                onClick = { close() },
                size = 56.dp,
                color = scheme.surfaceContainerHighest,
                contentColor = scheme.onSurfaceVariant
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HoldKey(
                description = "Delete a character on the TV",
                modifier = Modifier.weight(1f),
                onTick = {
                    haptic(HapticFeedbackType.TextHandleMove)
                    if (text.isNotEmpty()) text = text.dropLast(1)
                    onKey(RemoteKey.BACKSPACE)
                }
            )
            ExpressiveButton(
                text = "Clear",
                tone = ButtonTone.Tonal,
                enabled = text.isNotEmpty(),
                height = 52.dp,
                modifier = Modifier.weight(1f),
                onClick = {
                    repeat(text.length) { onKey(RemoteKey.BACKSPACE) }
                    text = ""
                }
            )
            ExpressiveButton(
                text = "Search",
                icon = Icons.Rounded.Search,
                height = 52.dp,
                modifier = Modifier.weight(1.3f),
                onClick = { submit() }
            )
        }
    }
}

/** Delete key: fires immediately and keeps repeating while held. */
@Composable
private fun HoldKey(
    description: String,
    modifier: Modifier = Modifier,
    onTick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var pressed by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .height(52.dp)
            .pressMorph(pressed)
            .background(scheme.surfaceContainerHighest)
            .semantics {
                contentDescription = description
                role = Role.Button
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    val job = scope.launchKeyRepeat(initialDelayMs = 400L, intervalMs = 70L, fireFirst = true, tick = onTick)
                    try {
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                change.consume()
                                break
                            }
                        }
                    } finally {
                        job.cancel()
                        pressed = false
                    }
                }
            },
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.AutoMirrored.Rounded.Backspace, null, tint = scheme.onSurface, modifier = Modifier.size(20.dp))
        Text("Delete", color = scheme.onSurface, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}
