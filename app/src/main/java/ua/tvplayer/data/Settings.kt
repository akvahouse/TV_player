package ua.tvplayer.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Weight steps from lightest to heaviest; the chosen one is used for regular text. Named after the
 * Univia Pro cuts; variable fonts use [value] on their weight axis.
 */
enum class UniviaWeight(val title: String, val value: Int) {
    Thin("Тонкий", 100),
    UltraLight("Дуже легкий", 200),
    Light("Легкий", 300),
    Book("Книжковий", 350),
    Regular("Звичайний", 400),
    Medium("Середній", 500),
    Bold("Жирний", 700),
    Black("Чорний", 850),
    Ultra("Ультра", 900),
}

enum class FontChoice(val title: String) {
    System("Системний"),
    Exo2("Exo 2"),
    Montserrat("Montserrat"),

    /** Only offered when its files are in the build (they are not in the repository). */
    Univia("Univia Pro"),
}

/** Text size steps, like the larger-text slider tvOS 27 added. */
enum class TextSize(val title: String, val scale: Float) {
    Normal("Звичайний", 1f),
    Large("Великий", 1.12f),
    ExtraLarge("Дуже великий", 1.25f),
}

/** App-wide display settings. State-backed, so the UI restyles as soon as one changes. */
object Settings {
    private lateinit var prefs: SharedPreferences

    var family by mutableStateOf(FontChoice.System)
        private set
    var weight by mutableStateOf(UniviaWeight.Regular)
        private set
    var italic by mutableStateOf(false)
        private set
    var textSize by mutableStateOf(TextSize.Normal)
        private set

    fun init(context: Context) {
        prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        // "font" used to hold a Univia cut, with null meaning the system font.
        val savedWeight = prefs.getString("font", null)?.let { name -> UniviaWeight.entries.firstOrNull { it.name == name } }
        weight = savedWeight ?: UniviaWeight.Regular
        family = prefs.getString("family", null)?.let { name -> FontChoice.entries.firstOrNull { it.name == name } }
            ?: if (savedWeight != null) FontChoice.Univia else FontChoice.System
        italic = prefs.getBoolean("italic", false)
        textSize = prefs.getString("textSize", null)?.let { name -> TextSize.entries.firstOrNull { it.name == name } }
            ?: TextSize.Normal
    }

    fun updateFamily(value: FontChoice) {
        family = value
        prefs.edit().putString("family", value.name).apply()
    }

    fun updateWeight(value: UniviaWeight) {
        weight = value
        prefs.edit().putString("font", value.name).apply()
    }

    fun updateItalic(value: Boolean) {
        italic = value
        prefs.edit().putBoolean("italic", value).apply()
    }

    fun updateTextSize(value: TextSize) {
        textSize = value
        prefs.edit().putString("textSize", value.name).apply()
    }
}
