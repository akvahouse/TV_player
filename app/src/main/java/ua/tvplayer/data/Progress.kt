package ua.tvplayer.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * Watch positions per stream URL and the last watched stream per show.
 * Readers touch [version] so Compose screens refresh after returning from the player.
 */
object Progress {
    private lateinit var prefs: SharedPreferences
    private var version by mutableIntStateOf(0)

    fun init(context: Context) {
        prefs = context.getSharedPreferences("progress", Context.MODE_PRIVATE)
    }

    fun save(videoUrl: String, position: Long, duration: Long) {
        if (duration <= 0) return
        prefs.edit().putLong("p:$videoUrl", position).putLong("d:$videoUrl", duration).apply()
        version++
    }

    fun fraction(videoUrl: String): Float {
        version
        val duration = prefs.getLong("d:$videoUrl", 0)
        if (duration <= 0) return 0f
        return (prefs.getLong("p:$videoUrl", 0).toFloat() / duration).coerceIn(0f, 1f)
    }

    fun isWatched(videoUrl: String) = fraction(videoUrl) > 0.95f

    /** Where to start playback; a finished stream starts over. */
    fun resumePosition(videoUrl: String): Long =
        if (isWatched(videoUrl)) 0 else prefs.getLong("p:$videoUrl", 0)

    fun setLastWatched(showKey: String, video: Video) {
        prefs.edit().putString("last:$showKey", video.url).putString("voice:$showKey", video.voice).apply()
        version++
    }

    fun lastWatched(showKey: String): String? {
        version
        return prefs.getString("last:$showKey", null)
    }

    fun preferredVoice(showKey: String): String? = prefs.getString("voice:$showKey", null)

    /** Show key → last stream URL for every show played so far. */
    fun allLastWatched(): Map<String, String> =
        prefs.all.filterKeys { it.startsWith("last:") }
            .mapNotNull { (key, value) -> (value as? String)?.let { key.removePrefix("last:") to it } }
            .toMap()
}
