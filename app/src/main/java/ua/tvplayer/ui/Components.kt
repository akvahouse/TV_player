package ua.tvplayer.ui

import androidx.compose.animation.Crossfade
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import ua.tvplayer.data.Item

private val CardShape = RoundedCornerShape(12.dp)

/** Portrait poster; the title under it lights up while focused, like the Apple TV app. */
@Composable
fun PosterCard(
    item: Item,
    onClick: () -> Unit,
    onFocus: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp? = 120.dp,
    restoreFocus: Boolean = false,
    onRestored: () -> Unit = {},
    progress: Float = 0f,
    subtitle: String? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val requester = remember { FocusRequester() }
    if (restoreFocus) {
        LaunchedEffect(Unit) {
            runCatching { requester.requestFocus() }
            onRestored()
        }
    }
    Column(if (width != null) modifier.width(width) else modifier.fillMaxWidth()) {
        Card(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .focusRequester(requester)
                .onFocusChanged {
                    focused = it.isFocused || it.hasFocus
                    if (focused) onFocus()
                },
            shape = CardDefaults.shape(CardShape),
            scale = CardDefaults.scale(focusedScale = 1.1f),
            border = CardDefaults.border(
                focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = CardShape),
            ),
        ) {
            Box(Modifier.fillMaxSize()) {
                AsyncImage(
                    model = item.poster,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                // Label and progress share one bottom inset, on a scrim tall enough to keep both legible.
                if (item.label != null || progress > 0.01f) {
                    Column(
                        Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .background(Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.8f)))
                            .padding(start = 10.dp, end = 10.dp, top = 24.dp, bottom = 10.dp),
                    ) {
                        item.label?.let {
                            Text(
                                it,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White.copy(alpha = 0.92f),
                            )
                        }
                        if (progress > 0.01f) {
                            if (item.label != null) Spacer(Modifier.height(6.dp))
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(3.dp)
                                    .background(Color.White.copy(alpha = 0.28f), RoundedCornerShape(50)),
                            ) {
                                Box(Modifier.fillMaxHeight().fillMaxWidth(progress).background(Color.White, RoundedCornerShape(50)))
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            item.title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = if (focused) Color.White else TextDim,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
        subtitle?.let {
            Spacer(Modifier.height(2.dp))
            Text(
                it,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 11.sp,
                color = TextDim.copy(alpha = 0.7f),
                modifier = Modifier.padding(horizontal = 2.dp),
            )
        }
    }
}

/** Rounded text button: tabs, seasons, voices. */
@Composable
fun Pill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    onFocus: (() -> Unit)? = null,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.onFocusChanged { if (it.isFocused || it.hasFocus) onFocus?.invoke() },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = when {
                primary -> Color.White.copy(alpha = 0.9f)
                selected -> Color.White.copy(alpha = 0.2f)
                else -> Color.Transparent
            },
            contentColor = if (primary) Color.Black else Color.White.copy(alpha = 0.85f),
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black,
            pressedContainerColor = Color.White.copy(alpha = 0.8f),
            pressedContentColor = Color.Black,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
    ) {
        Text(
            text,
            maxLines = 1,
            fontSize = if (primary) 16.sp else 14.sp,
            fontWeight = if (primary || selected) FontWeight.SemiBold else FontWeight.Medium,
            modifier = Modifier.padding(
                horizontal = if (primary) 28.dp else 18.dp,
                vertical = if (primary) 12.dp else 8.dp,
            ),
        )
    }
}

val BackdropSideShade = Brush.horizontalGradient(
    0f to Bg.copy(alpha = 0.92f), 0.35f to Bg.copy(alpha = 0.6f), 0.7f to Color.Transparent,
)
val BackdropBottomShade = Brush.verticalGradient(
    0f to Bg.copy(alpha = 0.5f), 0.2f to Color.Transparent, 0.5f to Bg.copy(alpha = 0.45f), 0.8f to Bg,
)
const val BACKDROP_ALPHA = 0.85f

/** Full-screen artwork that fades between focused items, darkened toward the text and the rows. */
@Composable
fun Backdrop(image: String?) {
    Box(Modifier.fillMaxSize()) {
        Crossfade(targetState = image, animationSpec = tween(500), label = "backdrop") { url ->
            Box(Modifier.fillMaxSize()) {
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = BACKDROP_ALPHA },
                )
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(BackdropSideShade)
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(BackdropBottomShade)
        )
    }
}

@Composable
fun Message(text: String, modifier: Modifier = Modifier, start: Dp = 48.dp) {
    Text(text, color = TextDim, fontSize = 16.sp, modifier = modifier.padding(start = start, end = 48.dp, top = 16.dp, bottom = 16.dp))
}

/** A moving sheen across placeholder shapes while content loads. */
@Composable
fun shimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val x by transition.animateFloat(
        -600f, 1800f,
        infiniteRepeatable(tween(1400, easing = LinearEasing)),
        label = "shimmerX",
    )
    return Brush.linearGradient(
        listOf(Color.White.copy(alpha = 0.05f), Color.White.copy(alpha = 0.14f), Color.White.copy(alpha = 0.05f)),
        start = Offset(x, 0f),
        end = Offset(x + 500f, 260f),
    )
}

@Composable
private fun SkeletonPoster(brush: Brush, modifier: Modifier = Modifier) {
    Column(modifier) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).background(brush, RoundedCornerShape(12.dp)))
        Spacer(Modifier.height(16.dp))
        Box(Modifier.fillMaxWidth(0.7f).height(10.dp).background(brush, RoundedCornerShape(5.dp)))
    }
}

/** Placeholder posters in the shape of a row, so the page does not jump when it fills in. */
@Composable
fun SkeletonRow(modifier: Modifier = Modifier) {
    val brush = shimmerBrush()
    Row(
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        modifier = modifier.padding(start = 24.dp, bottom = 14.dp),
    ) {
        repeat(8) { SkeletonPoster(brush, Modifier.width(120.dp)) }
    }
}

@Composable
fun SkeletonGrid() {
    val brush = shimmerBrush()
    Column(
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier.padding(start = 24.dp, end = 48.dp, top = 18.dp),
    ) {
        repeat(2) {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                repeat(5) { SkeletonPoster(brush, Modifier.weight(1f)) }
            }
        }
    }
}

/** Title, text lines and a button in placeholder form, where the details page will appear. */
@Composable
fun DetailsSkeleton() {
    val brush = shimmerBrush()
    Column(Modifier.padding(start = 56.dp)) {
        Box(Modifier.width(260.dp).height(48.dp).background(brush, RoundedCornerShape(50)))
        Spacer(Modifier.height(30.dp))
        Box(Modifier.width(90.dp).height(14.dp).background(brush, RoundedCornerShape(7.dp)))
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            repeat(5) {
                Box(Modifier.width(200.dp).aspectRatio(16f / 9f).background(brush, RoundedCornerShape(10.dp)))
            }
        }
    }
}
