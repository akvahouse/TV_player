package ua.tvplayer.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.geometry.Size
import ua.tvplayer.ui.Bg
import ua.tvplayer.ui.BackdropSideShade
import ua.tvplayer.ui.BackdropBottomShade
import ua.tvplayer.ui.BACKDROP_ALPHA
import kotlin.math.roundToInt
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text

/*
 * "Liquid glass" look. Android 11 has no backdrop blur and video surfaces can't be blurred anyway,
 * so the glass is drawn: a dark translucent body, a light sheen from the top-left, and a rim that
 * runs from bright white through faint orange and blue, like light split by a glass edge.
 */

private val GlassBody = Brush.verticalGradient(listOf(Color(0x5C3A3A40), Color(0x7A16161A)))
private val GlassBodyFocused = Brush.verticalGradient(listOf(Color(0xB89C9CA6), Color(0xA64E4E56)))
private val GlassSheen = Brush.linearGradient(
    0f to Color.White.copy(alpha = 0.22f),
    0.35f to Color.White.copy(alpha = 0.04f),
    1f to Color.Transparent,
)
private val GlassRim = Brush.linearGradient(
    0f to Color.White.copy(alpha = 0.55f),
    0.3f to Color.White.copy(alpha = 0.10f),
    0.62f to Color(0x55FF8A3D),
    0.78f to Color(0x4D3DA5FF),
    1f to Color.White.copy(alpha = 0.35f),
)
private val GlassRimFocused = Brush.linearGradient(
    0f to Color.White,
    0.4f to Color.White.copy(alpha = 0.55f),
    0.7f to Color(0xAAFFB27A),
    1f to Color.White.copy(alpha = 0.9f),
)

/**
 * [seeThrough]: show the frosted page backdrop inside the glass when one is provided. Turn it off for
 * glass that floats over other content (menus over posters), where the backdrop would be wrong.
 */
fun Modifier.glass(
    shape: Shape,
    focused: Boolean = false,
    glow: Boolean = focused,
    seeThrough: Boolean = true,
): Modifier = composed {
    val backdrop = if (seeThrough) LocalGlassBackdrop.current else null
    this
        .then(if (glow) Modifier.halo() else Modifier)
        .clip(shape)
        .then(if (backdrop != null) Modifier.frosted(backdrop, shape) else Modifier)
        .background(
            when {
                backdrop == null -> if (focused) GlassBodyFocused else GlassBody
                else -> if (focused) GlassTintFocused else GlassTint
            }
        )
        .background(GlassSheen)
        .border(if (focused) 1.5.dp else 1.dp, if (focused) GlassRimFocused else GlassRim, shape)
}

// Over the frosted backdrop the glass only needs a light tint; the blur already gives it body.
private val GlassTint = Brush.verticalGradient(listOf(Color(0x1FFFFFFF), Color(0x14000000)))
private val GlassTintFocused = Brush.verticalGradient(listOf(Color(0x66FFFFFF), Color(0x40FFFFFF)))

/** Saturation lift for what shows through glass, as tvOS glass makes colours behind it richer. */
private val Vivid = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(1.5f) })

/** How far the backdrop bends at the very rim of the glass. */
private val LENS_SHIFT = 14.dp
/** How deep from the rim the bending reaches. */
private val LENS_DEPTH = 22.dp
private const val LENS_BANDS = 7

/**
 * Draws the blurred backdrop at the exact spot of the screen this element covers, then bends it near
 * the rim like a lens: the backdrop is redrawn in nested bands towards the edge, each magnified a
 * little more about the centre. The blur hides the steps between bands. Must come after the clip to [shape].
 */
@Composable
private fun Modifier.frosted(backdrop: GlassBackdrop, shape: Shape): Modifier {
    // Where the whole screen sits in this element's own coordinates (handles scaling on focus too).
    var screen by remember { mutableStateOf(Rect.Zero) }
    return this
        .onGloballyPositioned { c ->
            val root = c.findRootCoordinates()
            val topLeft = c.localPositionOf(root, Offset.Zero)
            val bottomRight = c.localPositionOf(root, Offset(root.size.width.toFloat(), root.size.height.toFloat()))
            screen = Rect(topLeft, bottomRight)
        }
        .drawBehind {
            if (screen.isEmpty) return@drawBehind
            fun DrawScope.backdropLayer() = translate(screen.left, screen.top) {
                val area = screen.size
                drawRect(Bg, size = area)
                val dst = IntSize(area.width.roundToInt(), area.height.roundToInt())
                backdrop.previous?.let {
                    drawImage(it, dstSize = dst, alpha = BACKDROP_ALPHA * (1 - backdrop.mix), colorFilter = Vivid)
                }
                backdrop.current?.let {
                    drawImage(it, dstSize = dst, alpha = BACKDROP_ALPHA * backdrop.mix, colorFilter = Vivid)
                }
                // The page shading, lighter: glass lets more light through than the bare page shows.
                drawRect(BackdropSideShade, size = area, alpha = 0.6f)
                drawRect(BackdropBottomShade, size = area, alpha = 0.55f)
            }

            val depth = minOf(LENS_DEPTH.toPx(), size.minDimension / 2)
            val shift = LENS_SHIFT.toPx()
            for (band in 0..LENS_BANDS) {
                val inset = depth * band / LENS_BANDS
                val rim = 1f - band.toFloat() / LENS_BANDS // 1 at the edge, 0 in the flat middle
                val d = shift * rim * rim
                val bandSize = Size(size.width - 2 * inset, size.height - 2 * inset)
                if (bandSize.minDimension <= 0f) break
                val clip = Path().apply {
                    addOutline(shape.createOutline(bandSize, layoutDirection, this@drawBehind))
                    translate(Offset(inset, inset))
                }
                clipPath(clip) {
                    scale(1f + d / (size.width / 2), 1f + d / (size.height / 2), pivot = center) {
                        backdropLayer()
                    }
                }
            }
        }
}

/** Soft light spilling around a focused control, standing in for the glow glass gets on tvOS. */
private fun Modifier.halo(): Modifier = drawBehind {
    // An oval stretched to the control's shape, so wide controls don't light up the whole screen.
    val radius = size.height * 1.1f
    scale(scaleX = size.width / size.height, scaleY = 1f, pivot = center) {
        // Eased falloff: bright near the control, a long faint tail, nothing left at the edge.
        drawCircle(
            Brush.radialGradient(
                0f to Color.White.copy(alpha = 0.24f),
                0.35f to Color.White.copy(alpha = 0.14f),
                0.6f to Color.White.copy(alpha = 0.05f),
                0.82f to Color.White.copy(alpha = 0.012f),
                1f to Color.Transparent,
                center = center,
                radius = radius,
            ),
            radius = radius,
        )
    }
}

/** Focus grows a glass control slightly, like tvOS. */
private fun Modifier.scaled(scale: Float): Modifier = graphicsLayer {
    scaleX = scale
    scaleY = scale
}

@Composable
private fun animatedScale(focused: Boolean, to: Float = 1.08f): Float =
    animateFloatAsState(if (focused) to else 1f, label = "focusScale").value

/** Rounded glass button with a text label ("Опис", "Серії"). */
@Composable
fun GlassPill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, active: Boolean = false) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(50)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .scaled(animatedScale(focused))
            .glass(shape, focused = focused || active, glow = focused)
            .clickable(interaction, indication = null, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 12.dp),
    ) {
        Text(text, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
    }
}

/** Big round glass button: play / pause. */
@Composable
fun GlassCircleButton(icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 68.dp) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .scaled(animatedScale(focused, 1.1f))
            .size(size)
            .glass(CircleShape, focused)
            .clickable(interaction, indication = null, onClick = onClick),
    ) {
        GlyphIcon(icon, Modifier.size(size * 0.46f))
    }
}

/** A glass capsule holding several icon buttons, like the right-hand groups in the YouTube TV bar. */
@Composable
fun GlassGroup(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.glass(RoundedCornerShape(50)).padding(4.dp),
        content = content,
    )
}

@Composable
fun GlassIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    active: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .scaled(animatedScale(focused, 1.12f))
            .size(44.dp)
            .clip(CircleShape)
            .background(
                when {
                    focused -> Color.White.copy(alpha = 0.32f)
                    active -> Color.White.copy(alpha = 0.14f)
                    else -> Color.Transparent
                }
            )
            .clickable(interaction, indication = null, enabled = enabled, onClick = onClick),
    ) {
        GlyphIcon(icon, Modifier.size(22.dp), alpha = if (enabled) 1f else 0.35f)
    }
}

@Composable
fun GlyphIcon(icon: ImageVector, modifier: Modifier = Modifier, alpha: Float = 1f, color: Color = Color.White) {
    Image(icon, contentDescription = null, colorFilter = ColorFilter.tint(color.copy(alpha = color.alpha * alpha)), modifier = modifier)
}

/** Dark glass sphere for the seek bar thumb, as in the reference player. */
fun Modifier.glassKnob(): Modifier = this
    .clip(CircleShape)
    .background(
        Brush.radialGradient(
            0f to Color(0xFF9A9AA0),
            0.55f to Color(0xFF2E2E33),
            1f to Color(0xFF0E0E10),
        )
    )
    .background(Brush.linearGradient(0f to Color.White.copy(alpha = 0.55f), 0.4f to Color.Transparent))
    .border(1.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = 0.9f), Color.White.copy(alpha = 0.15f))), CircleShape)

/** Material-style glyphs, drawn as paths so no icon library is needed. */
object Glyphs {
    val Play = glyph("M8,5v14l11,-7z")
    val Pause = glyph("M6,19h4V5H6v14zM14,5v14h4V5h-4z")
    val SkipNext = glyph("M6,18l8.5,-6L6,6v12zM16,6v12h2V6h-2z")
    val SkipPrevious = glyph("M6,6h2v12H6zM9.5,12l8.5,6V6z")
    val FastForward = glyph("M4,18l8.5,-6L4,6v12zM13,6v12l8.5,-6L13,6z")
    val Rewind = glyph("M11,18V6l-8.5,6 8.5,6zM11.5,12l8.5,6V6l-8.5,6z")
    val Subtitles = glyph(
        "M19,4H5c-1.11,0 -2,0.9 -2,2v12c0,1.1 0.89,2 2,2h14c1.1,0 2,-0.9 2,-2V6c0,-1.1 -0.9,-2 -2,-2z" +
            "M11,11H9.5v-0.5h-2v3h2V13H11v1c0,0.55 -0.45,1 -1,1H7c-0.55,0 -1,-0.45 -1,-1v-4c0,-0.55 0.45,-1 1,-1h3c0.55,0 1,0.45 1,1v1z" +
            "M18,11h-1.5v-0.5h-2v3h2V13H18v1c0,0.55 -0.45,1 -1,1h-3c-0.55,0 -1,-0.45 -1,-1v-4c0,-0.55 0.45,-1 1,-1h3c0.55,0 1,0.45 1,1v1z"
    )
    val Settings = glyph(
        "M19.14,12.94c0.04,-0.3 0.06,-0.61 0.06,-0.94c0,-0.32 -0.02,-0.64 -0.07,-0.94l2.03,-1.58c0.18,-0.14 0.23,-0.41 0.12,-0.61" +
            "l-1.92,-3.32c-0.12,-0.22 -0.37,-0.29 -0.59,-0.22l-2.39,0.96c-0.5,-0.38 -1.03,-0.7 -1.62,-0.94L14.4,2.81" +
            "c-0.04,-0.24 -0.24,-0.41 -0.48,-0.41h-3.84c-0.24,0 -0.43,0.17 -0.47,0.41L9.25,5.35C8.66,5.59 8.12,5.92 7.63,6.29" +
            "L5.24,5.33c-0.22,-0.08 -0.47,0 -0.59,0.22L2.74,8.87C2.62,9.08 2.66,9.34 2.86,9.48l2.03,1.58C4.84,11.36 4.8,11.69 4.8,12" +
            "s0.02,0.64 0.07,0.94l-2.03,1.58c-0.18,0.14 -0.23,0.41 -0.12,0.61l1.92,3.32c0.12,0.22 0.37,0.29 0.59,0.22l2.39,-0.96" +
            "c0.5,0.38 1.03,0.7 1.62,0.94l0.36,2.54c0.05,0.24 0.24,0.41 0.48,0.41h3.84c0.24,0 0.44,-0.17 0.47,-0.41l0.36,-2.54" +
            "c0.59,-0.24 1.13,-0.56 1.62,-0.94l2.39,0.96c0.22,0.08 0.47,0 0.59,-0.22l1.92,-3.32c0.12,-0.22 0.07,-0.47 -0.12,-0.61" +
            "L19.14,12.94zM12,15.6c-1.98,0 -3.6,-1.62 -3.6,-3.6s1.62,-3.6 3.6,-3.6s3.6,1.62 3.6,3.6S13.98,15.6 12,15.6z"
    )

    val Search = glyph(
        "M15.5,14h-0.79l-0.28,-0.27C15.41,12.59 16,11.11 16,9.5 16,5.91 13.09,3 9.5,3S3,5.91 3,9.5 5.91,16 9.5,16" +
            "c1.61,0 3.09,-0.59 4.23,-1.57l0.27,0.28v0.79l5,4.99L20.49,19l-4.99,-5zM9.5,14C7.01,14 5,11.99 5,9.5S7.01,5 9.5,5 14,7.01 14,9.5 11.99,14 9.5,14z"
    )
    val Home = glyph("M10,20v-6h4v6h5v-8h3L12,3 2,12h3v8z")
    val Mood = glyph(
        "M11.99,2C6.47,2 2,6.48 2,12s4.47,10 9.99,10C17.52,22 22,17.52 22,12S17.52,2 11.99,2zM12,20c-4.42,0 -8,-3.58 -8,-8" +
            "s3.58,-8 8,-8 8,3.58 8,8 -3.58,8 -8,8zM15.5,11c0.83,0 1.5,-0.67 1.5,-1.5S16.33,8 15.5,8 14,8.67 14,9.5s0.67,1.5 1.5,1.5z" +
            "M8.5,11c0.83,0 1.5,-0.67 1.5,-1.5S9.33,8 8.5,8 7,8.67 7,9.5 7.67,11 8.5,11zM12,17.5c2.33,0 4.31,-1.46 5.11,-3.5H6.89" +
            "c0.8,2.04 2.78,3.5 5.11,3.5z"
    )
    val Star = glyph("M12,17.27L18.18,21l-1.64,-7.03L22,9.24l-7.19,-0.61L12,2 9.19,8.63 2,9.24l5.46,4.73L5.82,21z")
    val Sparkle = glyph(
        "M19,9l1.25,-2.75L23,5l-2.75,-1.25L19,1l-1.25,2.75L15,5l2.75,1.25L19,9zM11.5,9.5L9,4 6.5,9.5 1,12l5.5,2.5L9,20" +
            "l2.5,-5.5L17,12l-5.5,-2.5zM19,15l-1.25,2.75L15,19l2.75,1.25L19,23l1.25,-2.75L23,19l-2.75,-1.25L19,15z"
    )
    val Tv = glyph(
        "M21,3H3c-1.1,0 -2,0.9 -2,2v12c0,1.1 0.9,2 2,2h5v2h8v-2h5c1.1,0 1.99,-0.9 1.99,-2L23,5c0,-1.1 -0.9,-2 -2,-2z" +
            "M21,17H3V5h18v12z"
    )
    val Movie = glyph(
        "M18,4l2,4h-3l-2,-4h-2l2,4h-3l-2,-4H8l2,4H7L5,4H4c-1.1,0 -1.99,0.9 -1.99,2L2,18c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V4h-4z"
    )
    val History = glyph(
        "M13,3c-4.97,0 -9,4.03 -9,9H1l3.89,3.89 0.07,0.14L9,12H6c0,-3.87 3.13,-7 7,-7s7,3.13 7,7 -3.13,7 -7,7" +
            "c-1.93,0 -3.68,-0.79 -4.94,-2.06l-1.42,1.42C8.27,19.99 10.51,21 13,21c4.97,0 9,-4.03 9,-9s-4.03,-9 -9,-9z" +
            "M12,8v5l4.28,2.54 0.72,-1.21 -3.5,-2.08V8H12z"
    )
    val Sort = glyph("M3,18h6v-2H3v2zM3,6v2h18V6H3zM3,13h12v-2H3v2z")
    val Bookmark = glyph("M17,3H7c-1.1,0 -1.99,0.9 -1.99,2L5,21l7,-3 7,3V5c0,-1.1 -0.9,-2 -2,-2zM17,18l-5,-2.18L7,18V5h10v13z")
    val Check = glyph("M9,16.17L4.83,12l-1.42,1.41L9,19 21,7l-1.41,-1.41z")

    private fun glyph(path: String) = ImageVector.Builder(
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(pathData = addPathNodes(path), fill = SolidColor(Color.White)).build()
}
