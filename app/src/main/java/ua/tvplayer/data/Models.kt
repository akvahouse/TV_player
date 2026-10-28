package ua.tvplayer.data

/** A card in a catalog listing: a series season page or a movie page. */
data class Item(
    val url: String,
    val title: String,
    val poster: String?,
    /** e.g. "1 сезон 8 серія" */
    val label: String?,
    val rating: String?,
)

data class Section(val title: String, val path: String)

data class ListPage(val items: List<Item>, val hasNext: Boolean)

enum class CatalogSort(val key: String, val title: String) {
    Newest("date", "За новизною"),
    Popular("popular", "За популярністю"),
    Views("views", "За переглядами"),
    Rating("rating", "За рейтингом"),
    Added("added", "Нещодавно додані"),
}

/** [type] is the section path without the slash: cartoon-series, movie, … */
data class CatalogFilter(
    val type: String,
    val sort: CatalogSort = CatalogSort.Newest,
    val genre: String? = null,
    val year: String? = null,
)

data class FilterOption(val value: String, val title: String)

data class FilterOptions(val genres: List<FilterOption>, val years: List<FilterOption>)

data class Subtitle(val label: String, val url: String)

/** One playable stream. Season and voice are empty when the player has no such level. */
data class Video(
    val season: String,
    val voice: String,
    val title: String,
    val url: String,
    val poster: String?,
    val subtitles: List<Subtitle>,
)

data class Details(
    val url: String,
    val title: String,
    val description: String?,
    val poster: String?,
    val videos: List<Video>,
    /** Season number from the page URL (/name/season-N), used to preselect a season. */
    val pageSeason: Int?,
)

/** Key shared by all season pages of one series. */
fun showKey(url: String): String = url.substringBefore("/season-")
