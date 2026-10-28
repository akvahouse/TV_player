package ua.tvplayer.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.jsoup.Jsoup
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** Scrapes uaserial.tv catalog pages and the embedded hdvbua player. */
object UaSerial {
    const val BASE = "https://uaserial.tv"
    const val USER_AGENT =
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"
    const val PLAYER_REFERER = "https://hdvbua.pro/"

    val SECTIONS = listOf(
        Section("Мультсеріали", "/cartoon-series"),
        Section("Мультфільми", "/cartoon-movie"),
        Section("Аніме", "/anime"),
        Section("Серіали", "/serial"),
        Section("Фільми", "/movie"),
    )

    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("User-Agent", USER_AGENT).build())
        }
        .build()

    private suspend fun fetch(url: String, referer: String? = null): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url)
            .apply { if (referer != null) header("Referer", referer) }
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}: $url")
            response.body!!.string()
        }
    }

    /** Pages are /section, /section/2, /section/3 … */
    suspend fun list(path: String, page: Int = 1): ListPage {
        val url = if (page <= 1) BASE + path else "$BASE$path/$page"
        return parseList(fetch(url), url, path, page)
    }

    /**
     * The site's filter page: one content type, sorted, optionally narrowed to a genre and year.
     * Pages are /filters?…, /filters/2?…, /filters/3?…
     */
    suspend fun catalog(filter: CatalogFilter, page: Int = 1): ListPage {
        val query = listOfNotNull(
            "type=${filter.type}",
            "priority=${filter.sort.key}",
            filter.genre?.let { "genre=$it" },
            filter.year?.let { "date=$it" },
        ).joinToString("&")
        val path = if (page <= 1) "/filters" else "/filters/$page"
        val html = fetch("$BASE$path?$query")
        val list = parseList(html, "$BASE$path?$query", null, page)
        val hasNext = Jsoup.parse(html).select("a[href]").any { it.attr("href").startsWith("/filters/${page + 1}?") }
        return list.copy(hasNext = hasNext)
    }

    /** Genres and years offered by the site's filter page. */
    suspend fun filterOptions(): FilterOptions {
        val doc = Jsoup.parse(fetch("$BASE/filters"))
        fun options(prefix: String) = doc.select("input[id^=$prefix]").map {
            FilterOption(it.attr("value"), it.attr("data-text").ifBlank { it.attr("value") }.replaceFirstChar(Char::titlecase))
        }.distinctBy { it.value }
        return FilterOptions(genres = options("filter-main-genre-"), years = options("filter-main-year-"))
    }

    suspend fun search(query: String): List<Item> {
        val url = "$BASE/search?query=" + URLEncoder.encode(query, "UTF-8")
        return parseList(fetch(url), url, null, 1).items
    }

    suspend fun details(url: String): Details {
        val html = fetch(url)
        val embed = findEmbedUrl(html)
        val videos = if (embed != null) parsePlayer(fetch(embed, referer = "$BASE/")) else emptyList()
        return parseDetails(html, url, videos)
    }
}

internal fun parseList(html: String, url: String, path: String?, page: Int): ListPage {
    val doc = Jsoup.parse(html, url)
    val items = doc.select("div.col > div.item:has(.img-wrap)").mapNotNull { el ->
        val link = el.selectFirst("a[href]") ?: return@mapNotNull null
        val href = link.absUrl("href").ifEmpty { return@mapNotNull null }
        val img = el.selectFirst("img")
        Item(
            url = href,
            title = el.selectFirst(".name")?.text()?.ifBlank { null } ?: link.attr("title"),
            poster = (img?.absUrl("data-src")?.ifEmpty { null } ?: img?.absUrl("src"))?.ifEmpty { null },
            label = el.selectFirst(".last-episode")?.text()?.ifBlank { null },
            rating = el.selectFirst(".mark")?.attr("data-mark")?.ifBlank { null },
        )
    }.distinctBy { it.url }
    val next = "$path/${page + 1}"
    val hasNext = path != null && doc.select("a[href]").any { it.attr("href") == next }
    return ListPage(items, hasNext)
}

internal fun findEmbedUrl(html: String): String? {
    val match = Regex("""(?:https?:)?//[^"'\s<>]+/embed/\d+[^"'\s<>]*""").find(html)?.value ?: return null
    return if (match.startsWith("//")) "https:$match" else match
}

internal fun parseDetails(html: String, url: String, videos: List<Video>): Details {
    val doc = Jsoup.parse(html, url)
    fun og(property: String) =
        doc.selectFirst("meta[property=og:$property]")?.attr("content")?.ifBlank { null }

    return Details(
        url = url,
        title = doc.selectFirst("h1")?.text()?.ifBlank { null } ?: og("title").orEmpty(),
        description = doc.select("div.text").map { it.text() }.maxByOrNull { it.length }
            ?.takeIf { it.length > 40 } ?: og("description"),
        poster = doc.selectFirst(".poster img")?.absUrl("src")?.ifEmpty { null } ?: og("image"),
        videos = videos,
        pageSeason = Regex("""/season-(\d+)""").find(url)?.groupValues?.get(1)?.toIntOrNull(),
    )
}

/**
 * The embed page creates `new Playerjs({file: ...})`. `file` is either a single URL (movies) or a
 * JSON tree: seasons → voices → episodes for series, or voices → files for some movies.
 */
internal fun parsePlayer(html: String): List<Video> {
    val raw = Regex("""\bfile\s*:\s*(['"])(.*?)(?<!\\)\1""", RegexOption.DOT_MATCHES_ALL)
        .find(html)?.groupValues?.get(2) ?: return emptyList()
    val value = unescapeJs(raw).trim()
    if (!value.startsWith("[")) {
        return listOfNotNull(cleanFile(value)?.let { Video("", "", "", it, null, emptyList()) })
    }
    val out = mutableListOf<Video>()
    val tree = runCatching { JSONArray(value) }.getOrNull() ?: return emptyList()
    collect(tree, emptyList(), out)
    return out.distinctBy { Triple(it.season, it.voice, it.url) }
}

private fun collect(array: JSONArray, path: List<String>, out: MutableList<Video>) {
    for (i in 0 until array.length()) {
        val obj = array.optJSONObject(i) ?: continue
        val title = obj.optString("title").trim()
        val folder = obj.optJSONArray("folder")
        if (folder != null) {
            collect(folder, path + title, out)
            continue
        }
        val url = cleanFile(obj.optString("file")) ?: continue
        val (season, voice) = when {
            path.size >= 2 -> path[0] to path[1]
            path.size == 1 && path[0].contains("сезон", ignoreCase = true) -> path[0] to ""
            path.size == 1 -> "" to path[0]
            title.contains("сері", ignoreCase = true) -> "" to ""
            else -> "" to title
        }
        out += Video(
            season = season,
            voice = voice,
            title = title,
            url = url,
            poster = obj.optString("poster").ifBlank { null },
            subtitles = parseSubtitles(obj.optString("subtitle")),
        )
    }
}

/** Playerjs allows "[label]url,[label]url"; the HLS master playlist already carries all qualities. */
internal fun cleanFile(file: String): String? {
    val url = file.split(",")
        .map { it.replace(Regex("""^\s*\[[^\]]*]"""), "").trim() }
        .firstOrNull { it.startsWith("http") || it.startsWith("//") }
        ?: return null
    return if (url.startsWith("//")) "https:$url" else url
}

internal fun parseSubtitles(value: String): List<Subtitle> {
    if (value.isBlank()) return emptyList()
    val labeled = Regex("""\[([^\]]*)]\s*([^,\[\s]+)""").findAll(value)
        .map { Subtitle(it.groupValues[1].ifBlank { "Субтитри" }, it.groupValues[2]) }
        .toList()
    return labeled.ifEmpty {
        if (value.startsWith("http")) listOf(Subtitle("Субтитри", value.trim())) else emptyList()
    }
}

/** Decodes a JavaScript string literal's body: \' \" \\ \/ \n \t \uXXXX and friends. */
internal fun unescapeJs(text: String): String {
    if ('\\' !in text) return text
    val out = StringBuilder(text.length)
    var i = 0
    while (i < text.length) {
        val c = text[i]
        if (c != '\\' || i == text.lastIndex) {
            out.append(c)
            i++
            continue
        }
        val next = text[i + 1]
        i += 2
        when (next) {
            'n' -> out.append('\n')
            't' -> out.append('\t')
            'r' -> out.append('\r')
            'u' -> {
                val code = text.substring(i, minOf(i + 4, text.length)).toIntOrNull(16)
                if (code != null && i + 4 <= text.length) {
                    out.append(code.toChar())
                    i += 4
                } else {
                    out.append("\\u")
                }
            }
            else -> out.append(next)
        }
    }
    return out.toString()
}
