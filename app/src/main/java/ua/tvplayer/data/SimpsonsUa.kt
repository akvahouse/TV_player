package ua.tvplayer.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.jsoup.Jsoup
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

data class EpisodeInfo(val title: String, val description: String?, val image: String?)

/**
 * Ukrainian episode titles, descriptions and stills from simpsonsua.tv, which covers a few
 * animated shows. Each show has an index page linking to one page per season.
 */
object SimpsonsUa {
    private const val BASE = "https://simpsonsua.tv"

    private class Show(val indexPath: String, val seasonDir: Regex)

    /** Keyed by the uaserial.tv show slug (uaserial.tv/<slug>/season-N). */
    private val SHOWS = mapOf(
        "the-simpsons" to Show("/simpsony/", Regex("""^/(?:sezon-|s)0*(\d+)/$""")),
        "futurama" to Show("/futurama24/", Regex("""^/futurama-sezon-(\d+)/$""")),
        "family-guy" to Show("/family-guy/", Regex("""^/family-guy-sezon-(\d+)/$""")),
        "south-park" to Show("/south-park/", Regex("""^/south-park-sezon-(\d+)/$""")),
    )

    private val seasonUrls = ConcurrentHashMap<String, Map<Int, String>>()
    private val seasons = ConcurrentHashMap<String, Map<Int, EpisodeInfo>>()

    fun supports(uaserialUrl: String) = slug(uaserialUrl) in SHOWS

    /** Episode number → info for one season, or empty when the show or season isn't covered. */
    suspend fun season(uaserialUrl: String, season: Int): Map<Int, EpisodeInfo> {
        val slug = slug(uaserialUrl)
        val show = SHOWS[slug] ?: return emptyMap()
        val index = seasonUrls[slug] ?: parseSeasonIndex(fetch(BASE + show.indexPath), show.seasonDir)
            .also { seasonUrls[slug] = it }
        val url = index[season] ?: return emptyMap()
        return seasons[url] ?: parseSeason(fetch(url), url).also { seasons[url] = it }
    }

    private fun slug(url: String) = url.removePrefix(UaSerial.BASE).trim('/').substringBefore('/')

    private suspend fun fetch(url: String): String = withContext(Dispatchers.IO) {
        UaSerial.http.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}: $url")
            response.body!!.string()
        }
    }

    internal fun parseSeasonIndex(html: String, seasonDir: Regex): Map<Int, String> {
        val doc = Jsoup.parse(html, BASE)
        val result = mutableMapOf<Int, String>()
        for (link in doc.select("a[href]")) {
            val url = link.absUrl("href")
            if (!url.startsWith(BASE)) continue
            val number = seasonDir.find(url.removePrefix(BASE))?.groupValues?.get(1)?.toIntOrNull() ?: continue
            result.putIfAbsent(number, url)
        }
        return result
    }

    internal fun parseSeason(html: String, url: String): Map<Int, EpisodeInfo> {
        val doc = Jsoup.parse(html, url)
        val result = mutableMapOf<Int, EpisodeInfo>()
        for (item in doc.select("div.movie_item")) {
            val link = item.selectFirst("a[href]") ?: continue
            val number = Regex("""-\d+-sezon-(\d+)-seriya\.html""").find(link.attr("href"))
                ?.groupValues?.get(1)?.toIntOrNull() ?: continue
            val title = item.selectFirst(".descr.nazva")?.text()?.trim().orEmpty()
            result[number] = EpisodeInfo(
                title = if (title == title.uppercase()) titleCase(title) else title,
                description = item.select(".descr:not(.nazva)").text().trim().ifEmpty { null },
                image = item.selectFirst("img")?.absUrl("src")?.ifEmpty { null },
            )
        }
        return result
    }

    /** "ОСТАННЯ СПОКУСА ГОМЕРА" → "Остання Спокуса Гомера", so names keep their capitals. */
    private fun titleCase(text: String) =
        text.lowercase().split(" ").joinToString(" ") { word -> word.replaceFirstChar { it.titlecase() } }
}

/** "5 сезон" → 5, "3 серія" → 3. */
fun leadingNumber(text: String): Int? = Regex("""\d+""").find(text)?.value?.toIntOrNull()
