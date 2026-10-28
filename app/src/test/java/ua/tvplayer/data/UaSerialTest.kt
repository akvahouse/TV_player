package ua.tvplayer.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Hits the live site: checks the parser still matches its markup. */
class UaSerialTest {

    @Test
    fun listsCartoonSeries() = runBlocking {
        val page = UaSerial.list("/cartoon-series")
        println("cartoon-series: ${page.items.size} items, next=${page.hasNext}, first=${page.items.first()}")
        assertTrue(page.items.size >= 10)
        assertTrue(page.hasNext)
        assertTrue(page.items.all { it.title.isNotBlank() && it.poster != null })
    }

    @Test
    fun secondPageDiffers() = runBlocking {
        val first = UaSerial.list("/cartoon-movie", 1).items.map { it.url }.toSet()
        val second = UaSerial.list("/cartoon-movie", 2).items.map { it.url }
        println("cartoon-movie p2: ${second.size} items")
        assertTrue(second.isNotEmpty())
        assertTrue(second.none { it in first })
    }

    @Test
    fun seriesHasSeasonsVoicesEpisodes() = runBlocking {
        val details = UaSerial.details("${UaSerial.BASE}/lupin1/season-4")
        val seasons = details.videos.map { it.season }.distinct()
        val voices = details.videos.map { it.voice }.distinct()
        println("${details.title}: seasons=$seasons voices=$voices videos=${details.videos.size}")
        println("description=${details.description?.take(80)} poster=${details.poster}")
        println("first=${details.videos.first()}")
        assertEquals(4, details.pageSeason)
        assertTrue(details.videos.all { it.url.contains(".m3u8") })
        assertTrue(seasons.size > 1)
    }

    @Test
    fun movieHasSingleStream() = runBlocking {
        val details = UaSerial.details("${UaSerial.BASE}/movie-ratatouille")
        println("${details.title}: ${details.videos}")
        assertTrue(details.videos.isNotEmpty())
        assertTrue(details.videos.first().url.contains(".m3u8"))
    }

    @Test
    fun cartoonSeriesDetails() = runBlocking {
        val item = UaSerial.list("/cartoon-series").items.first()
        val details = UaSerial.details(item.url)
        println("${details.title}: ${details.videos.size} videos, voices=${details.videos.map { it.voice }.distinct()}")
        assertTrue(details.videos.isNotEmpty())
    }

    @Test
    fun searchFindsResults() = runBlocking {
        val results = UaSerial.search("люпен")
        println("search: ${results.map { it.title }}")
        assertTrue(results.isNotEmpty())
    }

    @Test
    fun catalogSortsFiltersAndPages() = runBlocking {
        val byRating = UaSerial.catalog(CatalogFilter("cartoon-series", CatalogSort.Rating))
        val newest = UaSerial.catalog(CatalogFilter("cartoon-series", CatalogSort.Newest))
        val page2 = UaSerial.catalog(CatalogFilter("cartoon-series", CatalogSort.Rating), 2)
        val comedy2024 = UaSerial.catalog(CatalogFilter("cartoon-series", CatalogSort.Popular, genre = "comedy", year = "2024"))
        println("rating: ${byRating.items.take(3).map { it.title }} next=${byRating.hasNext}")
        println("newest: ${newest.items.take(3).map { it.title }}")
        println("rating p2: ${page2.items.take(3).map { it.title }}")
        println("comedy 2024: ${comedy2024.items.take(3).map { it.title }}")
        assertTrue(byRating.items.size >= 20 && byRating.hasNext)
        assertTrue(byRating.items.first().url != newest.items.first().url)
        assertTrue(page2.items.none { it in byRating.items })
        assertTrue(comedy2024.items.isNotEmpty())
    }

    @Test
    fun filterOptionsHaveGenresAndYears() = runBlocking {
        val options = UaSerial.filterOptions()
        println("genres=${options.genres.take(5)} years=${options.years.take(5)}")
        assertTrue(options.genres.size > 10)
        assertTrue(options.years.any { it.value == "2024" })
    }
}
