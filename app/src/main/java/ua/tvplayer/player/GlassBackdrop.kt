package ua.tvplayer.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The artwork behind the page, pre-blurred, so glass can show it frosted. Android 11 has no live
 * blur, but the backdrop is our own image, so a blurred copy drawn at the matching spot looks the same.
 */
class GlassBackdrop internal constructor(
    private val currentImage: () -> ImageBitmap?,
    private val previousImage: () -> ImageBitmap?,
    private val mixValue: () -> Float,
) {
    val current: ImageBitmap? get() = currentImage()
    val previous: ImageBitmap? get() = previousImage()

    /** 0 → showing [previous], 1 → showing [current]; follows the page backdrop's crossfade. */
    val mix: Float get() = mixValue()
}

val LocalGlassBackdrop = compositionLocalOf<GlassBackdrop?> { null }

/** Makes the blurred copy of [url] available to every glass element in [content]. */
@Composable
fun ProvideGlassBackdrop(url: String?, content: @Composable () -> Unit) {
    val context = LocalContext.current.applicationContext
    var current by remember { mutableStateOf<ImageBitmap?>(null) }
    var previous by remember { mutableStateOf<ImageBitmap?>(null) }
    val mix = remember { Animatable(1f) }
    LaunchedEffect(url) {
        val blurred = url?.let { runCatching { blurredBackdrop(context, it) }.getOrNull() }
        previous = current
        current = blurred
        mix.snapTo(0f)
        mix.animateTo(1f, tween(500))
    }
    val backdrop = remember { GlassBackdrop({ current }, { previous }, { mix.value }) }
    CompositionLocalProvider(LocalGlassBackdrop provides backdrop, content = content)
}

private const val WIDTH = 384
private const val HEIGHT = 216
private const val RADIUS = 7

/** The image cropped like the backdrop (fill a 16:9 screen, anchored top-centre), small and blurred. */
private suspend fun blurredBackdrop(context: Context, url: String): ImageBitmap? {
    val request = ImageRequest.Builder(context).data(url).allowHardware(false).size(960).build()
    val result = context.imageLoader.execute(request) as? SuccessResult ?: return null
    val source = result.drawable.toBitmap()
    return withContext(Dispatchers.Default) {
        val scale = maxOf(WIDTH.toFloat() / source.width, HEIGHT.toFloat() / source.height)
        val cropW = (WIDTH / scale).toInt().coerceAtMost(source.width)
        val cropH = (HEIGHT / scale).toInt().coerceAtMost(source.height)
        val left = (source.width - cropW) / 2
        val small = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        Canvas(small).drawBitmap(
            source,
            Rect(left, 0, left + cropW, cropH),
            Rect(0, 0, WIDTH, HEIGHT),
            Paint(Paint.FILTER_BITMAP_FLAG),
        )
        val pixels = IntArray(WIDTH * HEIGHT)
        small.getPixels(pixels, 0, WIDTH, 0, 0, WIDTH, HEIGHT)
        // Three box passes approximate a Gaussian.
        repeat(3) { boxBlur(pixels, WIDTH, HEIGHT, RADIUS) }
        small.setPixels(pixels, 0, WIDTH, 0, 0, WIDTH, HEIGHT)
        small.asImageBitmap()
    }
}

/** One horizontal then one vertical box pass over ARGB pixels, in place. */
internal fun boxBlur(pixels: IntArray, w: Int, h: Int, r: Int) {
    val tmp = IntArray(pixels.size)
    pass(pixels, tmp, w, h, r, horizontal = true)
    pass(tmp, pixels, w, h, r, horizontal = false)
}

private fun pass(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
    val lines = if (horizontal) h else w
    val length = if (horizontal) w else h
    val window = 2 * r + 1
    for (line in 0 until lines) {
        fun at(i: Int): Int {
            val c = i.coerceIn(0, length - 1)
            return if (horizontal) line * w + c else c * w + line
        }
        var a = 0; var rr = 0; var g = 0; var b = 0
        for (i in -r..r) {
            val p = src[at(i)]
            a += p ushr 24; rr += (p shr 16) and 0xFF; g += (p shr 8) and 0xFF; b += p and 0xFF
        }
        for (i in 0 until length) {
            dst[at(i)] = ((a / window) shl 24) or ((rr / window) shl 16) or ((g / window) shl 8) or (b / window)
            val out = src[at(i - r)]
            val inn = src[at(i + r + 1)]
            a += (inn ushr 24) - (out ushr 24)
            rr += ((inn shr 16) and 0xFF) - ((out shr 16) and 0xFF)
            g += ((inn shr 8) and 0xFF) - ((out shr 8) and 0xFF)
            b += (inn and 0xFF) - (out and 0xFF)
        }
    }
}
