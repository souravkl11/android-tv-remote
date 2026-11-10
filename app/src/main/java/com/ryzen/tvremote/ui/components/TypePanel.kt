package com.ryzen.tvremote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ryzen.tvremote.protocol.RemoteKey
import com.ryzen.tvremote.ui.theme.RemoteTheme

/**
 * Inline "type on TV" panel shown in place of the pad while typing. Text is mirrored to the TV
 * as you type: new characters are sent, removed ones become backspaces.
 */
@Composable
fun TypePanel(
    onSendText: (String) -> Unit,
    onKey: (RemoteKey) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = RemoteTheme.colors
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
            .clip(RoundedCornerShape(28.dp))
            .background(colors.cardSurface)
            .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 12.dp)
    ) {
        Column {
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
                    textStyle = TextStyle(fontSize = 18.sp),
                    placeholder = { Text("Type here, syncs live to the TV", color = colors.textSecondary, fontSize = 16.sp) },
                    shape = RoundedCornerShape(20.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = colors.buttonSurface,
                        unfocusedContainerColor = colors.buttonSurface,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary,
                        cursorColor = colors.accent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { submit() }),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester)
                )
                RoundButton(
                    icon = Icons.Default.Close,
                    description = "Close typing",
                    onClick = { close() },
                    size = 48.dp,
                    background = Color.Transparent,
                    tint = colors.textSecondary
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HoldPill(
                    icon = Icons.AutoMirrored.Filled.Backspace,
                    label = "Delete",
                    description = "Delete a character on the TV",
                    modifier = Modifier.weight(1f),
                    onTick = {
                        haptic(HapticFeedbackType.TextHandleMove)
                        if (text.isNotEmpty()) text = text.dropLast(1)
                        onKey(RemoteKey.BACKSPACE)
                    }
                )
                Pill(
                    label = "Clear",
                    description = "Clear the text on the TV",
                    enabled = text.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                    onClick = {
                        repeat(text.length) { onKey(RemoteKey.BACKSPACE) }
                        text = ""
                    }
                )
                Pill(
                    icon = Icons.Default.Search,
                    label = "Search",
                    description = "Press enter on the TV",
                    primary = true,
                    modifier = Modifier.weight(1.3f),
                    onClick = { submit() }
                )
            }
        }
    }
}

@Composable
private fun Pill(
    label: String,
    description: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    primary: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val colors = RemoteTheme.colors
    Row(
        modifier = modifier
            .height(48.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(CircleShape)
            .background(if (primary) colors.accent else colors.buttonSurface)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = description, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        PillContent(icon, label, if (primary) colors.background else colors.textPrimary)
    }
}

/** Like [Pill], but fires immediately and keeps repeating while held (for Delete). */
@Composable
private fun HoldPill(
    icon: ImageVector,
    label: String,
    description: String,
    modifier: Modifier = Modifier,
    onTick: () -> Unit
) {
    val colors = RemoteTheme.colors
    val scope = rememberCoroutineScope()
    var pressed by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .height(48.dp)
            .clip(CircleShape)
            .background(if (pressed) colors.buttonHighlight else colors.buttonSurface)
            .semantics { contentDescription = description }
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
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        PillContent(icon, label, colors.textPrimary)
    }
}

@Composable
private fun PillContent(icon: ImageVector?, label: String, tint: Color) {
    if (icon != null) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(6.dp))
    }
    Text(text = label, color = tint, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
}
