package com.ryzen.tvremote.ui.components

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ryzen.tvremote.apps.AppPreset
import com.ryzen.tvremote.apps.AppPresets
import com.ryzen.tvremote.apps.AppShortcut
import com.ryzen.tvremote.ui.theme.RemoteTheme
import java.util.UUID

/**
 * Add an app (pick a preset or fill in a custom one), or edit an existing shortcut when
 * [editing] is non-null.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAppSheet(
    editing: AppShortcut?,
    existing: List<AppShortcut>,
    onAddPreset: (AppPreset) -> Unit,
    onSave: (AppShortcut) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = RemoteTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var name by remember { mutableStateOf(editing?.name ?: "") }
    var target by remember { mutableStateOf(editing?.launchTarget ?: "") }
    var color by remember { mutableStateOf(editing?.colorArgb ?: AppPresets.swatches.first()) }
    var showError by remember { mutableStateOf(false) }

    val canSave = name.isNotBlank() && target.isNotBlank()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.cardSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp)
        ) {
            Text(
                text = if (editing == null) "Add app" else "Edit app",
                color = colors.textPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            if (editing == null) {
                Spacer(Modifier.height(4.dp))
                Text("Tap an app to add it to your remote.", color = colors.textSecondary, fontSize = 13.sp)
                Spacer(Modifier.height(16.dp))

                AppPresets.all.chunked(4).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
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

                Spacer(Modifier.height(8.dp))
                Text("Custom app", color = colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
            } else {
                Spacer(Modifier.height(16.dp))
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(20); showError = false },
                singleLine = true,
                label = { Text("Name") },
                colors = fieldColors(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = target,
                onValueChange = { target = it.trim(); showError = false },
                singleLine = true,
                label = { Text("Package name or link") },
                supportingText = {
                    Text(
                        text = "e.g. org.xbmc.kodi or https://www.youtube.com",
                        color = if (showError && target.isBlank()) colors.red else colors.textSecondary
                    )
                },
                colors = fieldColors(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))
            Text("Colour", color = colors.textSecondary, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                AppPresets.swatches.forEach { swatch ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(32.dp)
                            .clip(CircleShape)
                            .background(Color(swatch))
                            .then(
                                if (color == swatch) Modifier.border(2.dp, colors.textPrimary, CircleShape) else Modifier
                            )
                            .clickable(role = Role.RadioButton) { color = swatch },
                        contentAlignment = Alignment.Center
                    ) {
                        if (color == swatch) {
                            Icon(Icons.Default.Check, contentDescription = "Selected", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                AppTileIcon(name = name.ifBlank { "?" }, color = Color(color), size = 48.dp, presetId = editing?.presetId)
                Button(
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
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.background),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Text(if (editing == null) "Add custom app" else "Save", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun PresetTile(preset: AppPreset, added: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val colors = RemoteTheme.colors
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClickLabel = "Add ${preset.name}", onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            AppTileIcon(preset.name, Color(preset.colorArgb), size = 56.dp, presetId = preset.id)
            if (added) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(17.dp))
                        .background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Check, contentDescription = "Already added", tint = colors.accent, modifier = Modifier.size(26.dp))
                }
            }
        }
        Text(
            text = preset.name,
            color = colors.textSecondary,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = RemoteTheme.colors.accent,
    unfocusedBorderColor = RemoteTheme.colors.divider,
    focusedTextColor = RemoteTheme.colors.textPrimary,
    unfocusedTextColor = RemoteTheme.colors.textPrimary,
    focusedLabelColor = RemoteTheme.colors.accent,
    unfocusedLabelColor = RemoteTheme.colors.textSecondary,
    cursorColor = RemoteTheme.colors.accent
)
