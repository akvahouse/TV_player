package ua.tvplayer.player

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil.compose.AsyncImage

/** How long before the end of an episode the next one is offered. */
const val UP_NEXT_MS = 10_000L

/**
 * MEGOGO-style card in the bottom-right corner: the next episode's still with a ring that runs
 * down from 10 seconds, then the player moves on by itself.
 */
@Composable
fun UpNextCard(
    image: String?,
    title: String,
    remainingMs: Long,
    onPlayNow: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { playFocus.requestFocus() } }
    val seconds = ((remainingMs + 999) / 1000).coerceIn(0, UP_NEXT_MS / 1000)
    // Smooth between the 100ms position polls.
    val fraction by animateFloatAsState(
        (remainingMs.toFloat() / UP_NEXT_MS).coerceIn(0f, 1f),
        animationSpec = tween(120, easing = LinearEasing),
        label = "upNext",
    )
    Column(
        modifier
            .width(372.dp)
            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(26.dp))
            .glass(RoundedCornerShape(26.dp))
            .padding(12.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(16.dp)),
        ) {
            AsyncImage(image, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(64.dp)
                    .glass(CircleShape),
            ) {
                CountdownRing(fraction, Modifier.fillMaxSize().padding(5.dp))
                Text("$seconds", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "НАСТУПНА СЕРІЯ",
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.2.sp,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            title,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassPill("Дивитись зараз", onClick = onPlayNow, modifier = Modifier.focusRequester(playFocus))
            GlassPill("Скасувати", onClick = onCancel)
        }
    }
}

/** A white arc that shrinks as time runs out, over a faint full track. */
@Composable
private fun CountdownRing(fraction: Float, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val stroke = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
        drawArc(Color.White.copy(alpha = 0.2f), 0f, 360f, useCenter = false, style = stroke)
        drawArc(Color.White, -90f, 360f * fraction, useCenter = false, style = stroke)
    }
}

/**
 * Shown while a stream loads: the episode still, dimmed, under a glass disc with a spinning
 * gradient arc. [still] is null once the first frame is on screen and only rebuffering is left.
 */
@Composable
fun LoadingOverlay(still: String?, title: String?, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (still != null) {
            AsyncImage(still, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Black.copy(alpha = 0.85f))))
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            GlassSpinner(76.dp)
            if (still != null && title != null) {
                Spacer(Modifier.height(20.dp))
                Text(title, color = Color.White.copy(alpha = 0.9f), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun GlassSpinner(size: Dp, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "spinner")
    val angle by transition.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(1100, easing = LinearEasing)),
        label = "angle",
    )
    // The arc breathes between short and long, like the tvOS activity ring.
    val sweep by transition.animateFloat(
        60f, 250f,
        infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "sweep",
    )
    Box(modifier.size(size).glass(CircleShape), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(size * 0.2f)) {
            val stroke = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
            rotate(angle) {
                drawArc(
                    Brush.sweepGradient(listOf(Color.Transparent, Color(0xFFFFB27A), Color.White)),
                    0f, sweep, useCenter = false, style = stroke,
                )
            }
        }
    }
}
