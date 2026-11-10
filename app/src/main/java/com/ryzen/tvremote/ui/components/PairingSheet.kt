package com.ryzen.tvremote.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ryzen.tvremote.ui.theme.Motion

private const val CodeLength = 6

/** Pairing code entry: six cells that fill as you type, and it submits itself on the sixth. */
@Composable
fun PairingSheet(
    deviceName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var code by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    RemoteSheet(
        title = "Enter the code",
        subtitle = "$deviceName is showing a 6-character code. Type it here to pair.",
        onDismiss = onDismiss
    ) {
        BasicTextField(
            value = code,
            onValueChange = { input ->
                // The TV's code is hexadecimal (0-9, A-F).
                val filtered = input.filter { it in "0123456789abcdefABCDEF" }.uppercase().take(CodeLength)
                val completed = filtered.length == CodeLength && code.length < CodeLength
                code = filtered
                if (completed) onConfirm(filtered)
            },
            singleLine = true,
            cursorBrush = SolidColor(Color.Transparent),
            textStyle = TextStyle(color = Color.Transparent),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { if (code.length == CodeLength) onConfirm(code) }),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .semantics { contentDescription = "Pairing code" },
            decorationBox = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    repeat(CodeLength) { i ->
                        CodeCell(
                            char = code.getOrNull(i),
                            active = i == code.length,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        )

        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ExpressiveButton("Cancel", onDismiss, tone = ButtonTone.Tonal, modifier = Modifier.weight(1f))
            ExpressiveButton(
                text = "Pair",
                onClick = { onConfirm(code) },
                enabled = code.length == CodeLength,
                modifier = Modifier.weight(1.4f)
            )
        }
    }
}

@Composable
private fun CodeCell(char: Char?, active: Boolean, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val filled = char != null
    val pop = animateFloatAsState(if (filled) 1f else 0f, Motion.fastSpatial(), label = "pop")
    val container by animateColorAsState(
        targetValue = if (filled) scheme.primaryContainer else scheme.surfaceContainerHighest,
        animationSpec = Motion.effects(),
        label = "cell"
    )
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = modifier
            .aspectRatio(0.8f)
            .clip(shape)
            .background(container)
            .then(if (active) Modifier.border(2.dp, scheme.primary, shape) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = char?.toString() ?: "",
            color = scheme.onPrimaryContainer,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.graphicsLayer {
                val s = 0.5f + 0.5f * pop.value
                scaleX = s
                scaleY = s
            }
        )
    }
}
