package ua.tvplayer.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Hits the live sites: checks episode info still parses and lines up with uaserial numbering. */
class SimpsonsUaTest {

    @Test
    fun simpsonsSeasonHasDescriptions() = runBlocking {
        val season = SimpsonsUa.season("${UaSerial.BASE}/the-simpsons/season-1-dj", 5)
        println("simpsons s5: ${season.size} episodes, e9=${season[9]}")
        assertTrue(season.size >= 20)
        assertEquals("Остання Спокуса Гомера", season[9]?.title)
        assertTrue(season.values.all { !it.description.isNullOrBlank() && it.image != null })
    }

    @Test
    fun oddSeasonUrlsAreFound() = runBlocking {
        for (n in listOf(1, 35, 37)) {
            val season = SimpsonsUa.season("${UaSerial.BASE}/the-simpsons/season-1-dj", n)
            println("simpsons s$n: ${season.size} episodes, e1=${season[1]?.title}")
            assertTrue(season.isNotEmpty())
        }
    }

    @Test
    fun otherShowsHaveDescriptions() = runBlocking {
        for (url in listOf("futurama/season-1", "family-guy/season-1-ak", "south-park/season-1")) {
            val season = SimpsonsUa.season("${UaSerial.BASE}/$url", 1)
            println("$url: ${season.size} episodes, e1=${season[1]}")
            assertTrue(season.isNotEmpty())
        }
    }

    @Test
    fun uaserialNumberingMatches() = runBlocking {
        val details = UaSerial.details("${UaSerial.BASE}/the-simpsons/season-1-dj")
        val seasons = details.videos.map { it.season }.distinct()
        println("uaserial simpsons seasons=${seasons.take(5)}… episodes=${details.videos.take(3).map { it.title }}")
        assertTrue(seasons.all { leadingNumber(it) != null })
        assertTrue(details.videos.all { leadingNumber(it.title) != null })
    }

    @Test
    fun unknownShowIsSkipped() = runBlocking {
        assertTrue(SimpsonsUa.season("${UaSerial.BASE}/lupin1/season-4", 1).isEmpty())
    }
}
