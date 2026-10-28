package ua.tvplayer.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

/** One show the user played: the page to return to and the episode they were on. */
data class HistoryEntry(
    val key: String,
    val url: String,
    val title: String,
    val poster: String?,
    /** e.g. "2 сезон · 5 серія"; null for movies. */
    val episode: String?,
    val videoUrl: String,
    /** 0 for shows imported from before history existed. */
    val time: Long,
)

/** Recently watched shows, newest first. Readers touch [version] so Compose refreshes on change. */
object History {
    private const val MAX_ENTRIES = 100
    private lateinit var prefs: SharedPreferences
    private var version by mutableIntStateOf(0)

    fun init(context: Context) {
        prefs = context.getSharedPreferences("history", Context.MODE_PRIVATE)
    }

    /** Parsed once per change rather than on every recomposition. */
    private var cache: List<HistoryEntry>? = null

    fun entries(): List<HistoryEntry> {
        version
        cache?.let { return it }
        return parse(prefs.getString("entries", null)).also { cache = it }
    }

    /** Bad or old-format rows are skipped instead of failing the whole list. */
    internal fun parse(json: String?): List<HistoryEntry> {
        val array = runCatching { JSONArray(json ?: "[]") }.getOrNull() ?: return emptyList()
        return (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            val key = o.optString("key").ifBlank { null } ?: return@mapNotNull null
            val url = o.optString("url").ifBlank { null } ?: return@mapNotNull null
            HistoryEntry(
                key = key,
                url = url,
                title = o.optString("title"),
                poster = o.optString("poster").ifBlank { null },
                episode = o.optString("episode").ifBlank { null },
                videoUrl = o.optString("video"),
                time = o.optLong("time"),
            )
        }.distinctBy { it.key }
    }

    /** Called when playback starts from a details page. */
    fun record(url: String, title: String, poster: String?, video: Video) {
        put(HistoryEntry(showKey(url), url, title, poster, episodeLabel(video), video.url, System.currentTimeMillis()))
    }

    /** Called when the player moves on to another episode of the same show. */
    fun updateEpisode(key: String, video: Video) {
        val entry = entries().firstOrNull { it.key == key } ?: return
        put(entry.copy(episode = episodeLabel(video), videoUrl = video.url, time = System.currentTimeMillis()))
    }

    fun contains(key: String) = entries().any { it.key == key }

    /**
     * Adds shows watched before history was recorded, behind the existing entries.
     * [find] turns a show key and its last stream into an entry, or null if the show is gone;
     * if it throws (no network), the import is tried again on the next launch.
     */
    suspend fun importOnce(lastWatched: Map<String, String>, find: suspend (key: String, videoUrl: String) -> HistoryEntry?) {
        if (prefs.getBoolean("imported", false)) return
        var failed = false
        val found = lastWatched.filterKeys { !contains(it) }.mapNotNull { (key, url) ->
            runCatching { find(key, url) }.onFailure { failed = true }.getOrNull()
        }
        if (found.isNotEmpty()) save(entries() + found)
        if (!failed) prefs.edit().putBoolean("imported", true).apply()
    }

    fun clear() {
        prefs.edit().remove("entries").apply()
        cache = null
        version++
    }

    private fun put(entry: HistoryEntry) {
        save(listOf(entry) + entries().filter { it.key != entry.key })
    }

    private fun save(entries: List<HistoryEntry>) {
        val list = entries.take(MAX_ENTRIES)
        val array = JSONArray()
        list.forEach {
            array.put(
                JSONObject()
                    .put("key", it.key)
                    .put("url", it.url)
                    .put("title", it.title)
                    .put("poster", it.poster.orEmpty())
                    .put("episode", it.episode.orEmpty())
                    .put("video", it.videoUrl)
                    .put("time", it.time)
            )
        }
        prefs.edit().putString("entries", array.toString()).apply()
        cache = list
        version++
    }

    fun episodeLabel(video: Video): String? =
        listOf(video.season, video.title).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { null }
}
