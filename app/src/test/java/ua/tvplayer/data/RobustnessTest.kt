package ua.tvplayer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Offline checks for inputs the review flagged as crash risks. */
class RobustnessTest {

    @Test
    fun playerJsonWithEscapedQuotesParses() {
        val html = """new Playerjs({file: '[{\"title\":\"1 сезон\",\"folder\":[{\"title\":\"М1\",\"folder\":[{\"title\":\"1 серія\",\"file\":\"https:\/\/x.test\/a.m3u8\"}]}]}]'});"""
        val videos = parsePlayer(html)
        assertEquals(1, videos.size)
        assertEquals("https://x.test/a.m3u8", videos.first().url)
    }

    @Test
    fun titleWithQuoteSurvives() {
        val html = """new Playerjs({file: '[{"title":"Серія \\"Пілот\\"","file":"https://x.test/b.m3u8"}]'});"""
        assertEquals("Серія \"Пілот\"", parsePlayer(html).single().title)
    }

    @Test
    fun brokenPlayerJsonGivesNoVideos() {
        assertTrue(parsePlayer("new Playerjs({file: '[{\"title\":'});").isEmpty())
        assertTrue(parsePlayer("no player here").isEmpty())
    }

    @Test
    fun repeatedStreamsAreDropped() {
        val html = """new Playerjs({file: '[{"title":"1 серія","file":"https://x.test/c.m3u8"},{"title":"1 серія","file":"https://x.test/c.m3u8"}]'});"""
        assertEquals(1, parsePlayer(html).size)
    }

    @Test
    fun corruptHistoryIsSkipped() {
        assertTrue(History.parse("not json").isEmpty())
        assertTrue(History.parse(null).isEmpty())
        val mixed = """[{"key":"k1","url":"u1","title":"A","video":"v","time":5}, 42, {"url":"no key"}, {"key":"k2","url":"u2"}]"""
        assertEquals(listOf("k1", "k2"), History.parse(mixed).map { it.key })
    }

    @Test
    fun jsUnescape() {
        assertEquals("a'b\"c\\d/e\nf", unescapeJs("""a\'b\"c\\d\/e\nf"""))
        assertEquals("ї", unescapeJs("""ї"""))
        assertEquals("trailing\\", unescapeJs("trailing\\"))
    }
}
