package com.ryzen.tvremote.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.circle
import androidx.graphics.shapes.star
import androidx.graphics.shapes.toPath
import com.ryzen.tvremote.ui.theme.Motion

// ---- Shapes ------------------------------------------------------------------------------------

/** The app's shape vocabulary. All are drawn inside a unit circle and scaled when painted. */
object Polygons {
    val Circle: RoundedPolygon = RoundedPolygon.circle(numVertices = 8)

    /** Soft eight-lobed cookie: the OK button and the "connected" badge. */
    val Cookie: RoundedPolygon = RoundedPolygon.star(
        numVerticesPerRadius = 8,
        radius = 1f,
        innerRadius = 0.84f,
        rounding = CornerRounding(0.24f),
        innerRounding = CornerRounding(0.24f)
    )

    val Clover: RoundedPolygon = RoundedPolygon.star(
        numVerticesPerRadius = 4,
        radius = 1f,
        innerRadius = 0.6f,
        rounding = CornerRounding(0.42f),
        innerRounding = CornerRounding(0.12f)
    )

    val Pentagon: RoundedPolygon = RoundedPolygon(
        numVertices = 5,
        radius = 1f,
        rounding = CornerRounding(0.34f)
    )

    val Burst: RoundedPolygon = RoundedPolygon.star(
        numVerticesPerRadius = 10,
        radius = 1f,
        innerRadius = 0.8f,
        rounding = CornerRounding(0.1f),
        innerRounding = CornerRounding(0.1f)
    )

    val Triangle: RoundedPolygon = RoundedPolygon(
        numVertices = 3,
        radius = 1f,
        rounding = CornerRounding(0.36f)
    )
}

private fun DrawScope.drawUnitPath(
    path: androidx.compose.ui.graphics.Path,
    color: Color,
    rotation: Float
) {
    val radius = size.minDimension / 2f
    withTransform({
        translate(center.x, center.y)
        rotate(rotation, pivot = Offset.Zero)
        scale(radius, radius, pivot = Offset.Zero)
    }) {
        drawPath(path, color)
    }
}

/**
 * A filled shape that morphs between [from] and [to]. [progress] and [rotation] are read while
 * drawing, so animating them doesn't recompose.
 */
@Composable
fun MorphBox(
    from: RoundedPolygon,
    to: RoundedPolygon,
    progress: () -> Float,
    color: Color,
    modifier: Modifier = Modifier,
    rotation: () -> Float = { 0f },
    content: @Composable BoxScope.() -> Unit = {}
) {
    val morph = remember(from, to) { Morph(from, to) }
    val scratch = remember { android.graphics.Path() }
    Box(
        modifier = modifier.drawBehind {
            scratch.rewind()
            morph.toPath(progress(), scratch)
            drawUnitPath(scratch.asComposePath(), color, rotation())
        },
        contentAlignment = Alignment.Center,
        content = content
    )
}

/** A single static shape with content centred on it. */
@Composable
fun ShapeBox(
    polygon: RoundedPolygon,
    color: Color,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit = {}
) = MorphBox(polygon, polygon, { 0f }, color, modifier, content = content)

/** Expressive loading indicator: one shape tumbling through the shape vocabulary. */
@Composable
fun LoadingMorph(
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp
) {
    val shapes = remember {
        listOf(Polygons.Cookie, Polygons.Pentagon, Polygons.Clover, Polygons.Triangle, Polygons.Burst)
    }
    val morphs = remember { shapes.indices.map { Morph(shapes[it], shapes[(it + 1) % shapes.size]) } }
    val scratch = remember { android.graphics.Path() }
    val transition = rememberInfiniteTransition(label = "loading")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = morphs.size.toFloat(),
        animationSpec = infiniteRepeatable(tween(morphs.size * 700, easing = LinearEasing)),
        label = "phase"
    )
    Box(
        modifier = modifier
            .size(size)
            .drawBehind {
                val index = phase.toInt().coerceIn(0, morphs.lastIndex)
                val local = FastOutSlowInEasing.transform((phase - index).coerceIn(0f, 1f))
                scratch.rewind()
                morphs[index].toPath(local, scratch)
                drawUnitPath(scratch.asComposePath(), color, rotation = (index + local) * 72f)
            }
    )
}

// ---- Press behaviour ---------------------------------------------------------------------------

private class FractionCorner(private val fraction: Float) : CornerSize {
    override fun toPx(shapeSize: Size, density: Density): Float =
        shapeSize.minDimension * fraction.coerceIn(0f, 0.5f)
}

/**
 * The expressive press: the container squashes slightly and its corners tighten from
 * [restCorner] to [pressedCorner] (fractions of the shorter side; 0.5 = fully round), on a
 * bouncy spring. Also clips to that shape.
 */
@Composable
fun Modifier.pressMorph(
    pressed: Boolean,
    restCorner: Float = 0.5f,
    pressedCorner: Float = 0.28f,
    pressedScale: Float = 0.95f
): Modifier {
    val amount = animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = Motion.fastSpatial(),
        label = "press"
    )
    return graphicsLayer {
        val p = amount.value
        val s = 1f - (1f - pressedScale) * p
        scaleX = s
        scaleY = s
        shape = RoundedCornerShape(FractionCorner(restCorner + (pressedCorner - restCorner) * p))
        clip = true
    }
}

/** Clickable container with the expressive press, a ripple and a haptic tick. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PressSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    restCorner: Float = 0.5f,
    pressedCorner: Float = 0.28f,
    enabled: Boolean = true,
    description: String? = null,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val haptic = rememberHaptic()

    Box(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.38f)
            .pressMorph(pressed, restCorner, pressedCorner)
            .background(color)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)
            .combinedClickable(
                interactionSource = interaction,
                indication = ripple(),
                enabled = enabled,
                role = Role.Button,
                onLongClickLabel = onLongClickLabel,
                onLongClick = onLongClick?.let {
                    {
                        haptic(HapticFeedbackType.LongPress)
                        it()
                    }
                },
                onClick = {
                    haptic(HapticFeedbackType.TextHandleMove)
                    onClick()
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) { content() }
    }
}

/** Round icon button; goes squircle while pressed. */
@Composable
fun IconSurface(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    iconSize: Dp = 24.dp,
    color: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    enabled: Boolean = true
) {
    PressSurface(
        onClick = onClick,
        modifier = modifier.size(size),
        color = color,
        contentColor = contentColor,
        enabled = enabled,
        description = description
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(iconSize))
    }
}

enum class ButtonTone { Primary, Tonal, Plain }

/** Text button in the same press language. 56dp tall by default. */
@Composable
fun ExpressiveButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tone: ButtonTone = ButtonTone.Primary,
    enabled: Boolean = true,
    height: Dp = 56.dp
) {
    val scheme = MaterialTheme.colorScheme
    val (container, content) = when (tone) {
        ButtonTone.Primary -> scheme.primary to scheme.onPrimary
        ButtonTone.Tonal -> scheme.secondaryContainer to scheme.onSecondaryContainer
        ButtonTone.Plain -> Color.Transparent to scheme.primary
    }
    PressSurface(
        onClick = onClick,
        modifier = modifier.height(height),
        color = container,
        contentColor = content,
        enabled = enabled
    ) {
        Row(
            modifier = Modifier.padding(horizontal = if (tone == ButtonTone.Plain) 16.dp else 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}
