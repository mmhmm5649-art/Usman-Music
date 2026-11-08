package com.usman.musicplayer.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LyricsParserTest {

    @Test
    fun testParseLrcTimestampedFormat() {
        val lrcInput = """
            [00:12.50]First line of lyrics
            [00:18.30]Second line of lyrics
            [01:05.120]Chorus line of song
        """.trimIndent()

        val parsed = LyricsParser.parse(lrcInput, 180000L)

        assertTrue(parsed.isSynchronized)
        assertEquals(3, parsed.lines.size)
        assertTrue(parsed.lines[0].isTimestamped)

        // Line 1: 0m 12s 500ms = 12500ms
        assertEquals(12500L, parsed.lines[0].timestampMs)
        assertEquals("First line of lyrics", parsed.lines[0].text)

        // Line 2: 0m 18s 300ms = 18300ms
        assertEquals(18300L, parsed.lines[1].timestampMs)

        // Line 3: 1m 05s 120ms = 65120ms
        assertEquals(65120L, parsed.lines[2].timestampMs)
    }

    @Test
    fun testMultipleTimestampsPerLine() {
        val lrcInput = "[00:10.00][01:00.00]Repeated chorus line"
        val parsed = LyricsParser.parse(lrcInput)

        assertTrue(parsed.isSynchronized)
        assertEquals(2, parsed.lines.size)
        assertEquals(10000L, parsed.lines[0].timestampMs)
        assertEquals(60000L, parsed.lines[1].timestampMs)
        assertEquals("Repeated chorus line", parsed.lines[0].text)
        assertEquals("Repeated chorus line", parsed.lines[1].text)
    }

    @Test
    fun testFindActiveIndex() {
        val lrcInput = """
            [00:10.00]Line 1
            [00:20.00]Line 2
            [00:30.00]Line 3
        """.trimIndent()

        val parsed = LyricsParser.parse(lrcInput)

        // Before first line -> returns index 0
        assertEquals(0, LyricsParser.findActiveIndex(parsed.lines, 5000L))

        // At 15 seconds -> Line 1 (10000ms)
        assertEquals(0, LyricsParser.findActiveIndex(parsed.lines, 15000L))

        // At 25 seconds -> Line 2 (20000ms)
        assertEquals(1, LyricsParser.findActiveIndex(parsed.lines, 25000L))

        // At 35 seconds -> Line 3 (30000ms)
        assertEquals(2, LyricsParser.findActiveIndex(parsed.lines, 35000L))
    }

    @Test
    fun testPlaybackSeekingAndPauseResumeSynchronization() {
        val lrcInput = """
            [00:05.00]Verse 1
            [00:15.00]Verse 2
            [00:25.00]Chorus
            [00:45.00]Outro
        """.trimIndent()

        val parsed = LyricsParser.parse(lrcInput)
        val lines = parsed.lines

        // 1. Playback progressing normally
        assertEquals(0, LyricsParser.findActiveIndex(lines, 8000L))
        assertEquals(1, LyricsParser.findActiveIndex(lines, 18000L))

        // 2. Pause & resume at 20000ms (should remain at Verse 2)
        assertEquals(1, LyricsParser.findActiveIndex(lines, 20000L))

        // 3. User seeks forward to Chorus at 30000ms
        val seekTargetMs = lines[2].timestampMs // 25000ms
        assertEquals(2, LyricsParser.findActiveIndex(lines, seekTargetMs))

        // 4. User seeks backward to Verse 1 at 6000ms
        val backwardSeekMs = lines[0].timestampMs // 5000ms
        assertEquals(0, LyricsParser.findActiveIndex(lines, backwardSeekMs))
    }

    @Test
    fun testSongSwitchingClearsPreviousLyrics() {
        val song1Lyrics = "[00:10.00]Song 1 Line 1"
        val song2Lyrics = "[00:05.00]Song 2 Line 1"

        var currentLyrics = LyricsParser.parse(song1Lyrics)
        assertEquals("Song 1 Line 1", currentLyrics.lines.first().text)

        // Song switch simulation
        currentLyrics = ParsedLyrics(emptyList(), isSynchronized = false)
        assertTrue(currentLyrics.lines.isEmpty())

        currentLyrics = LyricsParser.parse(song2Lyrics)
        assertEquals("Song 2 Line 1", currentLyrics.lines.first().text)
    }

    @Test
    fun testUnsynchronizedPlainLyricsFallback() {
        val plainInput = """
            Line A
            Line B
            Line C
        """.trimIndent()

        val parsed = LyricsParser.parse(plainInput, 90000L)

        assertFalse(parsed.isSynchronized)
        assertEquals(3, parsed.lines.size)
        assertFalse(parsed.lines[0].isTimestamped)
        assertEquals(-1L, parsed.lines[0].timestampMs)
        assertEquals("Line A", parsed.lines[0].text)
        assertEquals("Line B", parsed.lines[1].text)
        assertEquals("Line C", parsed.lines[2].text)
    }

    @Test
    fun testHtmlEntitiesCleaningAndNullHandling() {
        val htmlInput = "Line 1&amp;Line 2<br/>Line 3 &#039;test&#039;"
        val parsed = LyricsParser.parse(htmlInput, 60000L)

        assertNotNull(parsed)
        assertTrue(parsed.lines.isNotEmpty())
        assertTrue(parsed.lines[0].text.contains("Line 1&Line 2"))
        assertTrue(parsed.lines[1].text.contains("Line 3 'test'"))

        // Null or empty input handling
        val emptyParsed = LyricsParser.parse(null)
        assertFalse(emptyParsed.isSynchronized)
        assertTrue(emptyParsed.lines.isEmpty())
    }
}
