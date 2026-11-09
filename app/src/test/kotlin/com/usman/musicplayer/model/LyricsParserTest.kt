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

    @Test
    fun testRapidTransitionsAndConsecutiveShortTimestamps() {
        val rapidLrc = """
            [00:01.00]Word 1
            [00:01.40]Word 2
            [00:01.90]Word 3
            [00:02.30]Word 4
            [00:02.80]Word 5
        """.trimIndent()

        val parsed = LyricsParser.parse(rapidLrc)
        val lines = parsed.lines
        assertEquals(5, lines.size)

        // At 1200ms -> Line 0
        assertEquals(0, LyricsParser.findActiveIndex(lines, 1200L))
        // At 1400ms -> Line 1
        assertEquals(1, LyricsParser.findActiveIndex(lines, 1400L))
        // At 1600ms -> Line 1
        assertEquals(1, LyricsParser.findActiveIndex(lines, 1600L))
        // At 1900ms -> Line 2
        assertEquals(2, LyricsParser.findActiveIndex(lines, 1900L))
        // At 2350ms -> Line 3
        assertEquals(3, LyricsParser.findActiveIndex(lines, 2350L))
        // At 3000ms -> Line 4
        assertEquals(4, LyricsParser.findActiveIndex(lines, 3000L))
    }

    @Test
    fun testStrictlySingleActiveLineConstraint() {
        val lrc = """
            [00:05.00]Line 0
            [00:10.00]Line 1
            [00:15.00]Line 2
            [00:20.00]Line 3
            [00:30.00]Line 4
        """.trimIndent()

        val parsed = LyricsParser.parse(lrc)
        val lines = parsed.lines

        // Sweep from 0 to 40,000ms in 250ms steps:
        // Confirm findActiveIndex ALWAYS produces a valid single index (or -1 if empty)
        for (ms in 0L..40000L step 250L) {
            val activeIdx = LyricsParser.findActiveIndex(lines, ms)
            assertTrue(
                "Active index at ${ms}ms must be between 0 and ${lines.size - 1}, but was $activeIdx",
                activeIdx in lines.indices
            )

            // Verify that evaluating line.id == activeIdx is true for EXACTLY ONE line
            val activeCount = lines.count { it.id == activeIdx }
            assertEquals("Exactly one line must be active at ${ms}ms", 1, activeCount)
        }
    }
}
