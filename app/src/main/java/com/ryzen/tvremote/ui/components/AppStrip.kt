package com.ryzen.tvremote.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ryzen.tvremote.apps.BrandIcons
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.core.animateFloatAsState
import com.ryzen.tvremote.apps.AppShortcut
import com.ryzen.tvremote.ui.theme.RemoteTheme

/** First letters used inside a tile: "YouTube" -> "Y", "Prime Video" -> "PV". */
fun appInitials(name: String): String {
    val words = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        words.isEmpty() -> "?"
        words.size == 1 -> words[0].take(1).uppercase()
        else -> words.take(2).joinToString("") { it.take(1).uppercase() }
    }
}

@Composable
fun AppTileIcon(
    name: String,
    color: Color,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 60.dp,
    presetId: String? = null
) {
    val pathData = presetId?.let { BrandIcons.paths[it] }
    val glyph = remember(pathData) {
        pathData?.let { PathParser().parsePathString(it).toPath() }
    }
    val onBrand = if (color.luminance() > 0.6f) Color(0xFF1E2025) else Color.White

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.verticalGradient(listOf(lerp(color, Color.White, 0.22f), color))),
        contentAlignment = Alignment.Center
    ) {
        if (glyph != null) {
            Canvas(modifier = Modifier.size(size * 0.56f)) {
                val scale = this.size.minDimension / 24f
                withTransform({ scale(scale, scale, pivot = Offset.Zero) }) {
                    drawPath(glyph, onBrand)
                }
            }
        } else {
            Text(
                text = appInitials(name),
                color = onBrand,
                fontSize = (size.value * 0.42f).sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/** Horizontal row of app shortcut tiles. Tap launches; long-press opens Edit / Move / Remove. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppStrip(
    shortcuts: List<AppShortcut>,
    onLaunch: (AppShortcut) -> Unit,
    onAdd: () -> Unit,
    onEdit: (AppShortcut) -> Unit,
    onMove: (AppShortcut, Int) -> Unit,
    onRemove: (AppShortcut) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(shortcuts, key = { it.id }) { shortcut ->
            val index = shortcuts.indexOf(shortcut)
            AppTile(
                shortcut = shortcut,
                canMoveLeft = index > 0,
                canMoveRight = index < shortcuts.lastIndex,
                onLaunch = { onLaunch(shortcut) },
                onEdit = { onEdit(shortcut) },
                onMove = { delta -> onMove(shortcut, delta) },
                onRemove = { onRemove(shortcut) }
            )
        }
        item(key = "add") { AddTile(onAdd) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppTile(
    shortcut: AppShortcut,
    canMoveLeft: Boolean,
    canMoveRight: Boolean,
    onLaunch: () -> Unit,
    onEdit: () -> Unit,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit
) {
    val haptic = rememberHaptic()
    var menu by remember { mutableStateOf(false) }
    val colors = RemoteTheme.colors
    val brand = Color(shortcut.colorArgb)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, label = "chipScale")
    val shape = RoundedCornerShape(24.dp)

    Box {
        Row(
            modifier = Modifier
                .scale(scale)
                .height(48.dp)
                .clip(shape)
                .background(brand.copy(alpha = 0.16f))
                .border(1.dp, brand.copy(alpha = 0.32f), shape)
                .combinedClickable(
                    interactionSource = interaction,
                    indication = null,
                    role = Role.Button,
                    onClickLabel = "Open ${shortcut.name} on TV",
                    onLongClickLabel = "Edit ${shortcut.name}",
                    onClick = {
                        haptic(HapticFeedbackType.TextHandleMove)
                        onLaunch()
                    },
                    onLongClick = {
                        haptic(HapticFeedbackType.LongPress)
                        menu = true
                    }
                )
                .padding(start = 8.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AppTileIcon(shortcut.name, brand, size = 32.dp, presetId = shortcut.presetId)
            Text(
                text = shortcut.name,
                color = colors.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        DropdownMenu(
            expanded = menu,
            onDismissRequest = { menu = false },
            containerColor = colors.cardSurface
        ) {
            MenuItem("Edit") { menu = false; onEdit() }
            if (canMoveLeft) MenuItem("Move left") { menu = false; onMove(-1) }
            if (canMoveRight) MenuItem("Move right") { menu = false; onMove(1) }
            MenuItem("Remove", color = colors.red) { menu = false; onRemove() }
        }
    }
}

@Composable
private fun MenuItem(text: String, color: Color = RemoteTheme.colors.textPrimary, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(text, color = color) }, onClick = onClick)
}

@Composable
private fun AddTile(onClick: () -> Unit) {
    val colors = RemoteTheme.colors
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(colors.buttonSurface)
            .combinedClickable(role = Role.Button, onClickLabel = "Add app", onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = "Add app",
            tint = colors.textPrimary,
            modifier = Modifier.size(22.dp)
        )
    }
}
