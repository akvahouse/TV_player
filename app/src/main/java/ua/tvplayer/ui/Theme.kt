package ua.tvplayer.ui

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.tv.material3.LocalTextStyle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Typography
import androidx.tv.material3.darkColorScheme
import ua.tvplayer.data.FontChoice
import ua.tvplayer.data.Settings
import ua.tvplayer.data.UniviaWeight

val Bg = Color(0xFF0B0B0F)
val TextDim = Color(0xFFA1A1AA)

/**
 * Univia Pro files are not in the repository (personal-use licence), so they are looked up by name:
 * a build without them still compiles and simply offers the system font only.
 */
private fun univiaName(weight: UniviaWeight, italic: Boolean): String {
    val base = "univia_pro_" + weight.name.replace(Regex("(?<=.)([A-Z])"), "_$1").lowercase()
    return when {
        !italic -> base
        weight == UniviaWeight.Regular -> "univia_pro_italic"
        else -> base + "_italic"
    }
}

/** Font resource id, or 0 when the file is not part of this build. */
@SuppressLint("DiscouragedApi")
fun univiaRes(context: Context, weight: UniviaWeight, italic: Boolean): Int =
    context.resources.getIdentifier(univiaName(weight, italic), "font", context.packageName)

fun univiaAvailable(context: Context): Boolean =
    UniviaWeight.entries.all { w -> listOf(false, true).all { univiaRes(context, w, it) != 0 } }

/** How many Univia cuts heavier than the base each requested weight is drawn. */
private val WEIGHT_STEPS = mapOf(
    FontWeight.W100 to -3, FontWeight.W200 to -2, FontWeight.W300 to -1, FontWeight.W400 to 0,
    FontWeight.W500 to 1, FontWeight.W600 to 2, FontWeight.W700 to 2, FontWeight.W800 to 3, FontWeight.W900 to 4,
)

/**
 * Univia Pro shifted so [base] is regular text and heavier weights step up from it: picking Light
 * makes the whole app lighter while titles stay bolder than body text.
 */
private fun univiaFamily(context: Context, base: UniviaWeight, italic: Boolean): FontFamily {
    if (!univiaAvailable(context)) return FontFamily.Default
    return steppedFamily(base) { cut, weight, style -> Font(univiaRes(context, cut, italic), weight, style) }
}

/**
 * Exo 2 and Montserrat ship as one static file per weight, cut from their variable fonts
 * (tools/instance_fonts.py): Android 11 ignores variation settings on resource fonts.
 */
@SuppressLint("DiscouragedApi")
private fun staticFamily(context: Context, name: String, base: UniviaWeight, italic: Boolean): FontFamily {
    val file = { cut: UniviaWeight ->
        context.resources.getIdentifier("${name}${if (italic) "_italic" else ""}_w${cut.value}", "font", context.packageName)
    }
    if (UniviaWeight.entries.any { file(it) == 0 }) return FontFamily.Default
    return steppedFamily(base) { cut, weight, style -> Font(file(cut), weight, style) }
}

private fun steppedFamily(base: UniviaWeight, font: (UniviaWeight, FontWeight, FontStyle) -> Font): FontFamily {
    val cuts = UniviaWeight.entries
    return FontFamily(
        WEIGHT_STEPS.flatMap { (weight, step) ->
            val cut = cuts[(base.ordinal + step).coerceIn(0, cuts.lastIndex)]
            // Registered as both styles so italic requests resolve to the same choice.
            listOf(FontStyle.Normal, FontStyle.Italic).map { font(cut, weight, it) }
        }
    )
}

/** The text font for a settings choice; the system font keeps italics as a style instead. */
fun fontFamilyFor(context: Context, choice: FontChoice, base: UniviaWeight, italic: Boolean): FontFamily = when (choice) {
    FontChoice.System -> FontFamily.Default
    FontChoice.Exo2 -> staticFamily(context, "exo2", base, italic)
    FontChoice.Montserrat -> staticFamily(context, "montserrat", base, italic)
    FontChoice.Univia -> univiaFamily(context, base, italic)
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val choice = Settings.family
    val base = Settings.weight
    val italic = Settings.italic
    val context = LocalContext.current
    val family = remember(choice, base, italic) { fontFamilyFor(context, choice, base, italic) }
    val systemItalic = italic && choice == FontChoice.System
    val typography = remember(family, systemItalic) { typographyWith(family, if (systemItalic) FontStyle.Italic else null) }
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale * Settings.textSize.scale)) {
        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = Color.White,
                onPrimary = Color.Black,
                background = Bg,
                onBackground = Color.White,
                surface = Color(0xFF1C1C22),
                onSurface = Color.White,
            ),
            typography = typography,
        ) {
            CompositionLocalProvider(LocalTextStyle provides typography.bodyLarge) {
                content()
            }
        }
    }
}

private fun typographyWith(family: FontFamily, style: FontStyle?): Typography {
    val t = Typography()
    fun androidx.compose.ui.text.TextStyle.f() = copy(fontFamily = family, fontStyle = style ?: fontStyle)
    return t.copy(
        displayLarge = t.displayLarge.f(), displayMedium = t.displayMedium.f(), displaySmall = t.displaySmall.f(),
        headlineLarge = t.headlineLarge.f(), headlineMedium = t.headlineMedium.f(), headlineSmall = t.headlineSmall.f(),
        titleLarge = t.titleLarge.f(), titleMedium = t.titleMedium.f(), titleSmall = t.titleSmall.f(),
        bodyLarge = t.bodyLarge.f(), bodyMedium = t.bodyMedium.f(), bodySmall = t.bodySmall.f(),
        labelLarge = t.labelLarge.f(), labelMedium = t.labelMedium.f(), labelSmall = t.labelSmall.f(),
    )
}
