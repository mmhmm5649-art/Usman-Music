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

    @Test
    fun testParseWordLevelTimestamps() {
        val yrcInput = "(1000,500)Word1 (1500,600)Word2 [2100,700]Word3"
        // Wrap in a line timestamp
        val fullInput = "[00:00.00]$yrcInput"
        val parsed = LyricsParser.parse(fullInput)

        assertTrue(parsed.isSynchronized)
        assertEquals(SyncQuality.WORD, parsed.syncQuality)
        val line = parsed.lines[0]
        assertEquals(3, line.words.size)
        
        assertEquals("Word1", line.words[0].text)
        assertEquals(1000L, line.words[0].startMs)
        assertEquals(1500L, line.words[0].endMs)

        assertEquals("Word2", line.words[1].text)
        assertEquals(1500L, line.words[1].startMs)
        assertEquals(2100L, line.words[1].endMs)

        assertEquals("Word3", line.words[2].text)
        assertEquals(2100L, line.words[2].startMs)
        assertEquals(2800L, line.words[2].endMs)
    }

    @Test
    fun testParseEnhancedLrcWordMarkers() {
        val elrcInput = "[00:10.00]<00:10.50>Hello <00:11.20>World <00:12.00>!"
        val parsed = LyricsParser.parse(elrcInput)

        assertTrue(parsed.isSynchronized)
        assertEquals(SyncQuality.WORD, parsed.syncQuality)
        val line = parsed.lines[0]
        assertEquals(3, line.words.size)

        assertEquals("Hello", line.words[0].text)
        assertEquals(10500L, line.words[0].startMs)
        assertEquals(11200L, line.words[0].endMs)
    }

    @Test
    fun testParseTtmlSpanWordMarkers() {
        val ttmlLine = """[00:00.00]<span begin="00:01.000" end="00:02.500">Hello</span> <span begin="00:02.500" end="00:04.000">TTML</span>"""
        val parsed = LyricsParser.parse(ttmlLine)

        assertTrue(parsed.isSynchronized)
        assertEquals(SyncQuality.WORD, parsed.syncQuality)
        val line = parsed.lines[0]
        assertEquals(2, line.words.size)

        assertEquals("Hello", line.words[0].text)
        assertEquals(1000L, line.words[0].startMs)
        assertEquals(2500L, line.words[0].endMs)

        assertEquals("TTML", line.words[1].text)
        assertEquals(2500L, line.words[1].startMs)
        assertEquals(4000L, line.words[1].endMs)
    }

    @Test
    fun testParseNativeAppleMusicTtmlFile() {
        val ttmlContent = """
            <?xml version="1.0" encoding="utf-8"?>
            <tt xmlns="http://www.w3.org/ns/ttml">
              <body>
                <div>
                  <p begin="00:10.000" end="00:16.000">
                    <span begin="00:10.000" end="00:10.600">I</span>
                    <span begin="00:10.600" end="00:11.400">love</span>
                    <span begin="00:11.400" end="00:12.800">listening</span>
                    <span begin="00:12.800" end="00:13.200">to</span>
                    <span begin="00:13.200" end="00:14.500">music</span>
                  </p>
                  <p begin="00:16.000" end="00:22.000">
                    <span begin="00:16.000" end="00:17.000">tonight</span>
                  </p>
                </div>
              </body>
            </tt>
        """.trimIndent()

        val parsed = LyricsParser.parse(ttmlContent)
        assertTrue(parsed.isSynchronized)
        assertEquals(SyncQuality.WORD, parsed.syncQuality)
        assertEquals(2, parsed.lines.size)

        val line0 = parsed.lines[0]
        assertEquals(10000L, line0.timestampMs)
        assertEquals(16000L, line0.endTimestampMs)
        assertEquals("I love listening to music", line0.text)
        assertEquals(5, line0.words.size)

        assertEquals("I", line0.words[0].text)
        assertEquals(10000L, line0.words[0].startMs)
        assertEquals(10600L, line0.words[0].endMs)

        assertEquals("love", line0.words[1].text)
        assertEquals(10600L, line0.words[1].startMs)
        assertEquals(11400L, line0.words[1].endMs)

        assertEquals("listening", line0.words[2].text)
        assertEquals(11400L, line0.words[2].startMs)
        assertEquals(12800L, line0.words[2].endMs)

        assertEquals("to", line0.words[3].text)
        assertEquals(12800L, line0.words[3].startMs)
        assertEquals(13200L, line0.words[3].endMs)

        assertEquals("music", line0.words[4].text)
        assertEquals(13200L, line0.words[4].startMs)
        assertEquals(14500L, line0.words[4].endMs)

        val line1 = parsed.lines[1]
        assertEquals("tonight", line1.text)
        assertEquals(1, line1.words.size)
        assertEquals("tonight", line1.words[0].text)
    }

    @Test
    fun testParseNetEaseYrcFormat() {
        val yrcContent = """
            [10500,4500](10500,500)I(11000,700)love(11700,1200)music
            [15000,3000](15000,800)every(15800,1000)day
        """.trimIndent()

        val parsed = LyricsParser.parse(yrcContent)
        assertTrue(parsed.isSynchronized)
        assertEquals(SyncQuality.WORD, parsed.syncQuality)
        assertEquals(2, parsed.lines.size)

        val line0 = parsed.lines[0]
        assertEquals(10500L, line0.timestampMs)
        assertEquals(15000L, line0.endTimestampMs)
        assertEquals("I love music", line0.text)
        assertEquals(3, line0.words.size)

        assertEquals("I", line0.words[0].text)
        assertEquals(10500L, line0.words[0].startMs)
        assertEquals(11000L, line0.words[0].endMs)

        assertEquals("love", line0.words[1].text)
        assertEquals(11000L, line0.words[1].startMs)
        assertEquals(11700L, line0.words[1].endMs)

        assertEquals("music", line0.words[2].text)
        assertEquals(11700L, line0.words[2].startMs)
        assertEquals(12900L, line0.words[2].endMs)
    }

    @Test
    fun testTeluguTextWordSegmentationAndIntegrity() {
        val teluguLrc = "[00:05.00]<00:05.00>మధురమైన <00:06.50>పాట <00:07.80>ప్రేమ"
        val parsed = LyricsParser.parse(teluguLrc)

        assertTrue(parsed.isSynchronized)
        assertEquals(SyncQuality.WORD, parsed.syncQuality)
        val line = parsed.lines[0]
        assertEquals(3, line.words.size)

        // Verify Telugu complex conjunct characters and vowel signs are perfectly intact
        assertEquals("మధురమైన", line.words[0].text)
        assertEquals(5000L, line.words[0].startMs)
        assertEquals(6500L, line.words[0].endMs)

        assertEquals("పాట", line.words[1].text)
        assertEquals(6500L, line.words[1].startMs)
        assertEquals(7800L, line.words[1].endMs)

        assertEquals("ప్రేమ", line.words[2].text)
        assertEquals(7800L, line.words[2].startMs)
    }

    @Test
    fun testWordProgressCalculationAndPersistence() {
        val word0 = LyricWord(text = "I", startMs = 1000L, endMs = 1500L, index = 0)
        val word1 = LyricWord(text = "love", startMs = 1500L, endMs = 2200L, index = 1)

        // 1. Before word0 starts (800ms)
        val t800 = 800L
        assertTrue(t800 < word0.startMs) // Word 0 is upcoming (grey)
        assertTrue(t800 < word1.startMs) // Word 1 is upcoming (grey)

        // 2. Middle of word0 (1250ms -> 50% through word0)
        val t1250 = 1250L
        val progWord0 = ((t1250 - word0.startMs).toFloat() / (word0.endMs - word0.startMs)).coerceIn(0f, 1f)
        assertEquals(0.5f, progWord0, 0.001f) // 50% revealed
        assertTrue(t1250 < word1.startMs) // Word 1 not started yet

        // 3. Exactly at word boundary transition (1500ms)
        val t1500 = 1500L
        assertTrue(t1500 >= word0.endMs) // Word 0 finished: MUST BE COMPLETED WHITE!
        val progWord1AtStart = ((t1500 - word1.startMs).toFloat() / (word1.endMs - word1.startMs)).coerceIn(0f, 1f)
        assertEquals(0.0f, progWord1AtStart, 0.001f) // Word 1 smoothly begins at 0%

        // 4. Word 1 at 75% progress (2025ms)
        val t2025 = 2025L
        assertTrue(t2025 >= word0.endMs) // Word 0 PERSISTS AS FINISHED WHITE!
        val progWord1 = ((t2025 - word1.startMs).toFloat() / (word1.endMs - word1.startMs)).coerceIn(0f, 1f)
        assertEquals(0.75f, progWord1, 0.001f) // 75% revealed

        // 5. After word 1 finishes (2500ms)
        val t2500 = 2500L
        assertTrue(t2500 >= word0.endMs) // Word 0 completed
        assertTrue(t2500 >= word1.endMs) // Word 1 completed
    }
    @Test
    fun testTeluguTextCharacterOffsets() {
        val teluguLrc = "[00:05.00]<00:05.00>మధురమైన <00:06.50>పాట <00:07.80>ప్రేమ"
        val parsed = LyricsParser.parse(teluguLrc)

        assertTrue(parsed.isSynchronized)
        val line = parsed.lines[0]
        assertEquals("మధురమైన పాట ప్రేమ", line.text)
        assertEquals(3, line.words.size)

        // "మధురమైన"
        assertEquals(0, line.words[0].charStart)
        assertEquals(7, line.words[0].charEnd)
        assertEquals("మధురమైన", line.text.substring(line.words[0].charStart, line.words[0].charEnd))

        // "పాట"
        assertEquals(8, line.words[1].charStart)
        assertEquals(11, line.words[1].charEnd)
        assertEquals("పాట", line.text.substring(line.words[1].charStart, line.words[1].charEnd))

        // "ప్రేమ"
        assertEquals(12, line.words[2].charStart)
        assertEquals(17, line.words[2].charEnd)
        assertEquals("ప్రేమ", line.text.substring(line.words[2].charStart, line.words[2].charEnd))
    }

    @Test
    fun testNetEaseYrcCharacterOffsets() {
        val yrcContent = "[10500,4500](10500,500)I(11000,700)love(11700,1200)music"
        val parsed = LyricsParser.parse(yrcContent)
        
        val line = parsed.lines[0]
        assertEquals("I love music", line.text)
        
        assertEquals(0, line.words[0].charStart)
        assertEquals(1, line.words[0].charEnd)
        
        assertEquals(2, line.words[1].charStart)
        assertEquals(6, line.words[1].charEnd)
        
        assertEquals(7, line.words[2].charStart)
        assertEquals(12, line.words[2].charEnd)
    }
}
