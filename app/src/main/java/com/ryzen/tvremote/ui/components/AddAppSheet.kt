package com.ryzen.tvremote.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ryzen.tvremote.apps.AppPreset
import com.ryzen.tvremote.apps.AppPresets
import com.ryzen.tvremote.apps.AppShortcut
import com.ryzen.tvremote.ui.theme.Motion
import java.util.UUID

/**
 * Add an app (pick a preset or fill in a custom one), or edit an existing shortcut when
 * [editing] is non-null.
 */
@Composable
fun AddAppSheet(
    editing: AppShortcut?,
    existing: List<AppShortcut>,
    onAddPreset: (AppPreset) -> Unit,
    onSave: (AppShortcut) -> Unit,
    onDismiss: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme

    var name by remember { mutableStateOf(editing?.name ?: "") }
    var target by remember { mutableStateOf(editing?.launchTarget ?: "") }
    var color by remember { mutableStateOf(editing?.colorArgb ?: AppPresets.swatches.first()) }
    var showError by remember { mutableStateOf(false) }

    val canSave = name.isNotBlank() && target.isNotBlank()

    RemoteSheet(
        title = if (editing == null) "Add an app" else "Edit shortcut",
        subtitle = if (editing == null) "Tap one to put it on your remote." else null,
        onDismiss = onDismiss
    ) {
        if (editing == null) {
            AppPresets.all.chunked(4).forEach { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { preset ->
                        val added = existing.any { it.presetId == preset.id }
                        PresetTile(
                            preset = preset,
                            added = added,
                            onClick = { if (!added) onAddPreset(preset) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            SectionLabel("Something else")
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(scheme.surfaceContainerHigh)
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppTileIcon(name = name.ifBlank { "?" }, color = Color(color), size = 56.dp, presetId = editing?.presetId)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(20); showError = false },
                    singleLine = true,
                    label = { Text("Name") },
                    isError = showError && name.isBlank(),
                    shape = RoundedCornerShape(18.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = target,
                onValueChange = { target = it.trim(); showError = false },
                singleLine = true,
                label = { Text("Package name or link") },
                isError = showError && target.isBlank(),
                supportingText = { Text("For example org.xbmc.kodi or https://www.youtube.com") },
                shape = RoundedCornerShape(18.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                AppPresets.swatches.forEach { swatch ->
                    Swatch(Color(swatch), selected = color == swatch) { color = swatch }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        ExpressiveButton(
            text = if (editing == null) "Add shortcut" else "Save",
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                if (!canSave) {
                    showError = true
                } else {
                    onSave(
                        AppShortcut(
                            id = editing?.id ?: UUID.randomUUID().toString(),
                            name = name.trim(),
                            launchTarget = target.trim(),
                            colorArgb = color,
                            presetId = editing?.presetId
                        )
                    )
                }
            }
        )
    }
}

@Composable
private fun Swatch(color: Color, selected: Boolean, onClick: () -> Unit) {
    val scale = animateFloatAsState(if (selected) 1f else 0.78f, Motion.fastSpatial(), label = "swatch")
    Box(
        modifier = Modifier
            .size(30.dp)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .clip(CircleShape)
            .background(color)
            .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
            .clickable(role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = Color.White, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun PresetTile(preset: AppPreset, added: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(scheme.surfaceContainerHigh)
            .clickable(enabled = !added, role = Role.Button, onClickLabel = "Add ${preset.name}", onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            AppTileIcon(preset.name, Color(preset.colorArgb), size = 48.dp, presetId = preset.id)
            if (added) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Check, contentDescription = "Already added", tint = Color.White, modifier = Modifier.size(24.dp))
                }
            }
        }
        Text(
            text = preset.name,
            color = if (added) scheme.onSurfaceVariant else scheme.onSurface,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}
