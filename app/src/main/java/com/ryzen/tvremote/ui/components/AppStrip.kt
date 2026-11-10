package com.ryzen.tvremote.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ryzen.tvremote.apps.AppShortcut
import com.ryzen.tvremote.apps.BrandIcons

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
    size: Dp = 60.dp,
    presetId: String? = null
) {
    val pathData = presetId?.let { BrandIcons.paths[it] }
    val glyph = remember(pathData) {
        pathData?.let { PathParser().parsePathString(it).toPath() }
    }
    // Netflix's mark is red on black, so it's inverted compared to the other brand tiles.
    val redOnBlack = presetId == "netflix"
    val onBrand = when {
        redOnBlack -> Color(0xFFE50914)
        color.luminance() > 0.6f -> Color(0xFF1E2025)
        else -> Color.White
    }
    val fill = if (redOnBlack) {
        Brush.verticalGradient(listOf(Color(0xFF1C1C1C), Color.Black))
    } else {
        Brush.verticalGradient(listOf(lerp(color, Color.White, 0.22f), color))
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(fill),
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
                fontSize = (size.value * 0.4f).sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * The app dock: one tray of round brand icons. Tap launches the app on the TV; press and hold
 * opens Edit / Move / Remove. Five fit across a phone; more scroll sideways.
 */
@Composable
fun AppStrip(
    shortcuts: List<AppShortcut>,
    onLaunch: (AppShortcut) -> Unit,
    onAdd: () -> Unit,
    onEdit: (AppShortcut) -> Unit,
    onMove: (AppShortcut, Int) -> Unit,
    onRemove: (AppShortcut) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(scheme.surfaceContainerLow)
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LazyRow(
            modifier = Modifier
                .weight(1f)
                .clip(CircleShape),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(shortcuts, key = { it.id }) { shortcut ->
                val index = shortcuts.indexOf(shortcut)
                AppButton(
                    shortcut = shortcut,
                    enabled = enabled,
                    canMoveLeft = index > 0,
                    canMoveRight = index < shortcuts.lastIndex,
                    onLaunch = { onLaunch(shortcut) },
                    onEdit = { onEdit(shortcut) },
                    onMove = { delta -> onMove(shortcut, delta) },
                    onRemove = { onRemove(shortcut) },
                    modifier = Modifier.animateItem()
                )
            }
            if (shortcuts.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = "Add your favourite apps",
                        color = scheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 14.dp)
                    )
                }
            }
        }
        // Pinned at the end of the tray so it never scrolls out of reach.
        IconSurface(
            icon = Icons.Rounded.Add,
            description = "Add app",
            onClick = onAdd,
            size = TileSize,
            color = scheme.surfaceContainerHighest,
            contentColor = scheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 10.dp)
        )
    }
}

private val TileSize = 52.dp

@Composable
private fun AppButton(
    shortcut: AppShortcut,
    enabled: Boolean,
    canMoveLeft: Boolean,
    canMoveRight: Boolean,
    onLaunch: () -> Unit,
    onEdit: () -> Unit,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    var menu by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        PressSurface(
            onClick = onLaunch,
            onLongClick = { menu = true },
            onLongClickLabel = "Edit ${shortcut.name}",
            description = "Open ${shortcut.name} on TV",
            color = Color.Transparent,
            pressedCorner = 0.32f,
            enabled = enabled,
            modifier = Modifier.size(TileSize)
        ) {
            AppTileIcon(shortcut.name, Color(shortcut.colorArgb), size = TileSize, presetId = shortcut.presetId)
        }

        DropdownMenu(
            expanded = menu,
            onDismissRequest = { menu = false },
            shape = MaterialTheme.shapes.large,
            containerColor = scheme.surfaceContainerHighest
        ) {
            Text(
                text = shortcut.name,
                color = scheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            MenuItem("Edit") { menu = false; onEdit() }
            if (canMoveLeft) MenuItem("Move left") { menu = false; onMove(-1) }
            if (canMoveRight) MenuItem("Move right") { menu = false; onMove(1) }
            MenuItem("Remove", color = scheme.error) { menu = false; onRemove() }
        }
    }
}

@Composable
private fun MenuItem(
    text: String,
    color: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = { Text(text, color = color, style = MaterialTheme.typography.bodyLarge) },
        onClick = onClick
    )
}
