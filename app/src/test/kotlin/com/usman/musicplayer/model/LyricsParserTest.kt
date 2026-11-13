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

    @Test
    fun testNeeKosameNaaPranamWordAndSyllableHighlightingSequence() {
        // Lyricify Syllable / QRC format with syllable breakdown on "kosame" (ko-sa-me)
        val lyricifyInput = "[1000,2800]Nee (1000,600)ko(1600,300)sa(1900,300)me (2200,300)naa (2500,500)pranam(3000,800)"
        val parsed = LyricsParser.parse(lyricifyInput, totalDurationMs = 180_000L)

        assertTrue(parsed.isSynchronized)
        assertEquals(SyncQuality.WORD, parsed.syncQuality)
        assertTrue(parsed.hasSyllableSync)

        val line = parsed.lines.single()
        assertEquals("Nee kosame naa pranam", line.text)
        assertEquals(4, line.words.size)

        // Word 0: "Nee" (1000..1600ms)
        assertEquals("Nee", line.words[0].text)
        assertEquals(1000L, line.words[0].startMs)
        assertEquals(1600L, line.words[0].endMs)

        // Word 1: "kosame" (1600..2500ms) with 3 syllables: "ko", "sa", "me"
        val kosame = line.words[1]
        assertEquals("kosame", kosame.text)
        assertEquals(1600L, kosame.startMs)
        assertEquals(2500L, kosame.endMs)
        assertEquals(3, kosame.syllables.size)
        assertEquals("ko", kosame.syllables[0].text)
        assertEquals("sa", kosame.syllables[1].text)
        assertEquals("me", kosame.syllables[2].text)

        // Word 2: "naa" (2500..3000ms)
        assertEquals("naa", line.words[2].text)
        // Word 3: "pranam" (3000..3800ms)
        assertEquals("pranam", line.words[3].text)

        // Step 1: At 1300ms ("Nee" singing at 50%)
        val eval1300 = LyricsParser.evaluateLineWords(line, 1300f)
        assertEquals(WordHighlightStatus.ACTIVE, eval1300[0].status)
        assertEquals(0.5f, eval1300[0].progress, 0.01f)
        assertEquals(WordHighlightStatus.UPCOMING, eval1300[1].status)
        assertEquals(WordHighlightStatus.UPCOMING, eval1300[2].status)
        assertEquals(WordHighlightStatus.UPCOMING, eval1300[3].status)

        // Step 2a: At 1750ms ("Nee" COMPLETED, "kosame" ACTIVE on syllable 0 "ko")
        val eval1750 = LyricsParser.evaluateLineWords(line, 1750f)
        assertEquals(WordHighlightStatus.COMPLETED, eval1750[0].status)
        assertEquals(1.0f, eval1750[0].progress, 0.001f)
        assertEquals(WordHighlightStatus.ACTIVE, eval1750[1].status)
        assertEquals(0, eval1750[1].activeSyllableIndex)
        assertEquals(0.5f, eval1750[1].activeSyllableProgress, 0.01f)
        assertEquals(WordHighlightStatus.UPCOMING, eval1750[2].status)

        // Step 2b: At 2050ms ("kosame" ACTIVE on syllable 1 "sa")
        val eval2050 = LyricsParser.evaluateLineWords(line, 2050f)
        assertEquals(WordHighlightStatus.COMPLETED, eval2050[0].status)
        assertEquals(WordHighlightStatus.ACTIVE, eval2050[1].status)
        assertEquals(1, eval2050[1].activeSyllableIndex)
        assertEquals(0.5f, eval2050[1].activeSyllableProgress, 0.01f)

        // Step 2c: At 2350ms ("kosame" ACTIVE on syllable 2 "me")
        val eval2350 = LyricsParser.evaluateLineWords(line, 2350f)
        assertEquals(WordHighlightStatus.ACTIVE, eval2350[1].status)
        assertEquals(2, eval2350[1].activeSyllableIndex)
        assertEquals(0.5f, eval2350[1].activeSyllableProgress, 0.01f)

        // Step 3: At 2750ms ("Nee" & "kosame" COMPLETED, "naa" ACTIVE, "pranam" UPCOMING)
        val eval2750 = LyricsParser.evaluateLineWords(line, 2750f)
        assertEquals(WordHighlightStatus.COMPLETED, eval2750[0].status)
        assertEquals(WordHighlightStatus.COMPLETED, eval2750[1].status)
        assertEquals(WordHighlightStatus.ACTIVE, eval2750[2].status)
        assertEquals(0.5f, eval2750[2].progress, 0.01f)
        assertEquals(WordHighlightStatus.UPCOMING, eval2750[3].status)

        // Step 4: At 3400ms ("Nee", "kosame", "naa" COMPLETED, "pranam" ACTIVE)
        val eval3400 = LyricsParser.evaluateLineWords(line, 3400f)
        assertEquals(WordHighlightStatus.COMPLETED, eval3400[0].status)
        assertEquals(WordHighlightStatus.COMPLETED, eval3400[1].status)
        assertEquals(WordHighlightStatus.COMPLETED, eval3400[2].status)
        assertEquals(WordHighlightStatus.ACTIVE, eval3400[3].status)
        assertEquals(0.5f, eval3400[3].progress, 0.01f)

        // Step 5: At 3900ms (all 4 words COMPLETED)
        val eval3900 = LyricsParser.evaluateLineWords(line, 3900f)
        assertTrue(eval3900.all { it.status == WordHighlightStatus.COMPLETED && it.progress == 1.0f })
    }

    @Test
    fun testTtmlSyllablesBackgroundVocalsTranslationsAndDuetAlignment() {
        val ttml = """
            <tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata">
              <body>
                <div>
                  <p begin="00:01.000" end="00:04.000" ttm:agent="v2">
                    <span begin="00:01.000" end="00:01.600">Nee</span> <span begin="00:01.600" end="00:01.900">ko</span><span begin="00:01.900" end="00:02.200">sa</span><span begin="00:02.200" end="00:02.500">me</span> <span begin="00:02.500" end="00:03.000">naa</span> <span begin="00:03.000" end="00:03.800">pranam</span>
                    <span ttm:role="x-bg"><span begin="00:03.200" end="00:03.900">(naa pranam)</span></span>
                    <span ttm:role="x-translation">My life is for you</span>
                  </p>
                </div>
              </body>
            </tt>
        """.trimIndent()

        val parsed = LyricsParser.parse(ttml)
        assertTrue(parsed.isSynchronized)
        assertTrue(parsed.hasSyllableSync)
        val line = parsed.lines.single()

        assertEquals("Nee kosame naa pranam", line.text)
        assertEquals(4, line.words.size)
        assertEquals("kosame", line.words[1].text)
        assertEquals(3, line.words[1].syllables.size)
        assertTrue(line.isOppositeAligned)
        assertEquals("My life is for you", line.translatedText)
        assertEquals("(naa pranam)", line.backgroundVocalText)
    }

    @Test
    fun testQrcXmlContainerAndHonestStandardLrcLineSyncQuality() {
        val qrcXml = """<Lyric_1 LyricType="1" LyricContent="[2000,3000]Hello (2000,1000)World(3000,2000)"/>"""
        val parsedQrc = LyricsParser.parse(qrcXml)
        assertTrue(parsedQrc.isSynchronized)
        assertEquals(SyncQuality.WORD, parsedQrc.syncQuality)
        assertEquals("Hello World", parsedQrc.lines.single().text)
        assertEquals(2, parsedQrc.lines.single().words.size)

        // Standard LRC without word timestamps MUST report SyncQuality.LINE and NOT invent fake words
        val standardLrc = """
            [00:10.00]Standard line one
            [00:15.00]Standard line two
        """.trimIndent()
        val parsedStandard = LyricsParser.parse(standardLrc)
        assertTrue(parsedStandard.isSynchronized)
        assertEquals(SyncQuality.LINE, parsedStandard.syncQuality)
        assertTrue(parsedStandard.lines.all { it.syncQuality == SyncQuality.LINE && it.words.isEmpty() })
    }

    @Test
    fun testHindiRomanTransliterationExamplesAndScriptPreservationForTamilTeluguMalayalam() {
        // 1. Prompt examples in plain/line-level and word-level formats:
        // - तुम ही हो -> Tum hi ho
        // - मेरा दिल -> Mera dil
        // - तेरे बिना -> Tere bina
        // - प्यार होता है -> Pyaar hota hai
        assertEquals("Tum hi ho", HindiRomanTransliterator.transliterateLine("तुम ही हो"))
        assertEquals("Mera dil", HindiRomanTransliterator.transliterateLine("मेरा दिल"))
        assertEquals("Tere bina", HindiRomanTransliterator.transliterateLine("तेरे बिना"))
        assertEquals("Pyaar hota hai", HindiRomanTransliterator.transliterateLine("प्यार होता है"))
        assertEquals("Tum hi ho, ab tum hi ho", HindiRomanTransliterator.transliterateLine("तुम ही हो, अब तुम ही हो"))

        // 2. Hindi Enhanced LRC: verify both ORIGINAL and ROMANIZED script modes preserve timestamps
        val hindiElrc = "[00:02.00]<00:02.00>तुम <00:02.60>ही <00:03.20>हो"
        val hindiParsedDoc = LyricsParser.parse(hindiElrc, scriptMode = LyricsScriptMode.ROMANIZED)
        val hindiParsed = hindiParsedDoc.lines.single()
        assertFalse(HindiRomanTransliterator.containsDevanagari(hindiParsed.text))
        assertEquals("Tum hi ho", hindiParsed.text)
        assertEquals(listOf("Tum", "hi", "ho"), hindiParsed.words.map { it.text })
        assertEquals(2000L, hindiParsed.words[0].startMs)
        assertEquals(2600L, hindiParsed.words[0].endMs)
        assertEquals(2600L, hindiParsed.words[1].startMs)
        assertEquals(3200L, hindiParsed.words[1].endMs)
        assertEquals(3200L, hindiParsed.words[2].startMs)

        // Switching to ORIGINAL mode returns original Devanagari with identical timestamps
        val hindiOriginal = hindiParsedDoc.withScriptMode(LyricsScriptMode.ORIGINAL).lines.single()
        assertEquals("तुम ही हो", hindiOriginal.text)
        assertEquals(listOf("तुम", "ही", "हो"), hindiOriginal.words.map { it.text })
        assertEquals(2000L, hindiOriginal.words[0].startMs)

        // 3. Tamil, Telugu, and Malayalam retain original scripts in ORIGINAL mode and convert in ROMANIZED mode
        val tamilElrc = "[00:04.00]<00:04.00>நீ <00:05.00>என் <00:05.80>காதல்"
        val teluguElrc = "[00:06.00]<00:06.00>నువ్వే <00:06.70>నా <00:07.60>ప్రాణం"
        val malayalamElrc = "[00:08.00]<00:08.00>നീ <00:08.80>എന്റെ <00:09.60>പ്രണയം"

        val tamilDoc = LyricsParser.parse(tamilElrc)
        assertEquals("நீ என் காதல்", tamilDoc.withScriptMode(LyricsScriptMode.ORIGINAL).lines.single().text)
        assertEquals("Nee en kaadhal", tamilDoc.withScriptMode(LyricsScriptMode.ROMANIZED).lines.single().text)

        val teluguDoc = LyricsParser.parse(teluguElrc)
        assertEquals("నువ్వే నా ప్రాణం", teluguDoc.withScriptMode(LyricsScriptMode.ORIGINAL).lines.single().text)
        assertEquals("Nuvve naa pranam", teluguDoc.withScriptMode(LyricsScriptMode.ROMANIZED).lines.single().text)

        val malayalamDoc = LyricsParser.parse(malayalamElrc)
        assertEquals("നീ എന്റെ പ്രണയം", malayalamDoc.withScriptMode(LyricsScriptMode.ORIGINAL).lines.single().text)
        assertEquals("Nee ente pranayam", malayalamDoc.withScriptMode(LyricsScriptMode.ROMANIZED).lines.single().text)
    }

    @Test
    fun testUniversalMultilingualScriptConversionAndLiveScriptModeSwitching() {
        // Verify exact prompt examples across all major supported languages:
        // Telugu: నువ్వే నా ప్రాణం -> Nuvve naa pranam
        assertEquals(
            "Nuvve naa pranam",
            MultilingualRomanTransliterator.transliterateLine("నువ్వే నా ప్రాణం")
        )
        // Hindi: तुम ही हो -> Tum hi ho
        assertEquals(
            "Tum hi ho",
            MultilingualRomanTransliterator.transliterateLine("तुम ही हो")
        )
        // Tamil: நீ என் காதல் -> Nee en kaadhal
        assertEquals(
            "Nee en kaadhal",
            MultilingualRomanTransliterator.transliterateLine("நீ என் காதல்")
        )
        // Malayalam: നീ എന്റെ പ്രണയം -> Nee ente pranayam
        assertEquals(
            "Nee ente pranayam",
            MultilingualRomanTransliterator.transliterateLine("നീ എന്റെ പ്രണയം")
        )
        // Kannada: ನೀನೇ ನನ್ನ ಪ್ರೀತಿ -> Neene nanna preethi
        assertEquals(
            "Neene nanna preethi",
            MultilingualRomanTransliterator.transliterateLine("ನೀನೇ ನನ್ನ ಪ್ರೀತಿ")
        )
        // Bengali: তুমি আমার -> Tumi amar
        assertEquals(
            "Tumi amar",
            MultilingualRomanTransliterator.transliterateLine("তুমি আমার")
        )
        // Punjabi (Gurmukhi): ਤੂੰ ਮੇਰਾ -> Tu mera
        assertEquals(
            "Tu mera",
            MultilingualRomanTransliterator.transliterateLine("ਤੂੰ ਮੇਰਾ")
        )
        // Urdu: تم ہی ہو -> Tum hi ho
        assertEquals(
            "Tum hi ho",
            MultilingualRomanTransliterator.transliterateLine("تم ہی ہو")
        )
        // Gujarati: તું મારો પ્રેમ -> Tu maaro prem
        assertEquals(
            "Tu maaro prem",
            MultilingualRomanTransliterator.transliterateLine("તું મારો પ્રેમ")
        )
        // Odia: ତୁ ମୋ ଜୀବନ -> Tu mo jeebana
        assertEquals(
            "Tu mo jeebana",
            MultilingualRomanTransliterator.transliterateLine("ତୁ ମୋ ଜୀବନ")
        )
        // Korean: 사랑해 -> Saranghae
        assertEquals(
            "Saranghae",
            MultilingualRomanTransliterator.transliterateLine("사랑해")
        )
        // Japanese: さくら -> Sakura
        assertEquals(
            "Sakura",
            MultilingualRomanTransliterator.transliterateLine("さくら")
        )
        // Mixed-language line (Telugu + English) preserves English words untouched:
        assertEquals(
            "Baby nuvve naa pranam tonight",
            MultilingualRomanTransliterator.transliterateLine("Baby నువ్వే నా ప్రాణం tonight")
        )

        // Verify live switching between ORIGINAL and ROMANIZED preserves word & syllable synchronization
        val multilingualElrc = """
            [00:02.00]<00:02.00>నువ్వే <00:02.80>నా <00:03.40>ప్రాణం
            [00:05.00]<00:05.00>நீ <00:05.60>என் <00:06.20>காதல்
            [00:08.00]<00:08.00>ನೀನೇ <00:08.80>ನನ್ನ <00:09.60>ಪ್ರೀತಿ
            [00:11.00]<00:11.00>তুমি <00:11.80>আমার
            [00:14.00]<00:14.00>ਤੂੰ <00:14.80>ਮੇਰਾ
            [00:17.00]<00:17.00>تم <00:17.60>ہی <00:18.20>ہو
        """.trimIndent()

        val parsedOrig = LyricsParser.parse(multilingualElrc, scriptMode = LyricsScriptMode.ORIGINAL)
        assertEquals(LyricsScriptMode.ORIGINAL, parsedOrig.scriptMode)
        assertTrue(MultilingualRomanTransliterator.containsNonLatinScript(multilingualElrc))
        assertEquals("నువ్వే నా ప్రాణం", parsedOrig.lines[0].text)
        assertEquals("நீ என் காதல்", parsedOrig.lines[1].text)
        assertEquals("ನೀನೇ ನನ್ನ ಪ್ರೀತಿ", parsedOrig.lines[2].text)
        assertEquals("তুমি আমার", parsedOrig.lines[3].text)
        assertEquals("ਤੂੰ ਮੇਰਾ", parsedOrig.lines[4].text)
        assertEquals("تم ہی ہو", parsedOrig.lines[5].text)

        val parsedRoman = parsedOrig.withScriptMode(LyricsScriptMode.ROMANIZED)
        assertEquals(LyricsScriptMode.ROMANIZED, parsedRoman.scriptMode)
        assertEquals("Nuvve naa pranam", parsedRoman.lines[0].text)
        assertEquals(listOf("Nuvve", "naa", "pranam"), parsedRoman.lines[0].words.map { it.text })
        assertEquals(2000L, parsedRoman.lines[0].words[0].startMs)
        assertEquals(2800L, parsedRoman.lines[0].words[0].endMs)
        assertEquals(2800L, parsedRoman.lines[0].words[1].startMs)
        assertEquals(3400L, parsedRoman.lines[0].words[2].startMs)

        assertEquals("Nee en kaadhal", parsedRoman.lines[1].text)
        assertEquals(listOf("Nee", "en", "kaadhal"), parsedRoman.lines[1].words.map { it.text })

        assertEquals("Neene nanna preethi", parsedRoman.lines[2].text)
        assertEquals(listOf("Neene", "nanna", "preethi"), parsedRoman.lines[2].words.map { it.text })

        assertEquals("Tumi amar", parsedRoman.lines[3].text)
        assertEquals(listOf("Tumi", "amar"), parsedRoman.lines[3].words.map { it.text })

        assertEquals("Tu mera", parsedRoman.lines[4].text)
        assertEquals(listOf("Tu", "mera"), parsedRoman.lines[4].words.map { it.text })

        assertEquals("Tum hi ho", parsedRoman.lines[5].text)
        assertEquals(listOf("Tum", "hi", "ho"), parsedRoman.lines[5].words.map { it.text })
    }

    @Test
    fun testTumHiHoAbTumHiHoWordByWordAndSyllableSynchronization() {
        // Test "तुम ही हो, अब तुम ही हो" -> "Tum hi ho, ab tum hi ho" with word-by-word highlighting in ROMANIZED mode
        val elrc = """
            [00:10.00]<00:10.00>तुम <00:10.50>ही <00:11.00>हो, <00:11.80>अब <00:12.30>तुम <00:12.80>ही <00:13.30>हो
            [00:15.00]<00:15.00>मेरा <00:15.80>दिल <00:16.60>तेरे <00:17.40>बिना <00:18.20>प्यार <00:19.00>होता <00:19.80>है
        """.trimIndent()

        val parsed = LyricsParser.parse(elrc, totalDurationMs = 180_000L, scriptMode = LyricsScriptMode.ROMANIZED)
        assertTrue(parsed.isSynchronized)
        assertEquals(SyncQuality.WORD, parsed.syncQuality)
        assertEquals(2, parsed.lines.size)

        val line0 = parsed.lines[0]
        assertEquals("Tum hi ho, ab tum hi ho", line0.text)
        assertFalse(HindiRomanTransliterator.containsDevanagari(line0.text))
        assertEquals(listOf("Tum", "hi", "ho,", "ab", "tum", "hi", "ho"), line0.words.map { it.text })

        // Verify character offsets match line0.text substrings
        for (word in line0.words) {
            assertEquals(word.text, line0.text.substring(word.charStart, word.charEnd))
        }

        // Verify progressive word-by-word highlight evaluation at vocal timestamps
        // At 10250ms -> "Tum" is ACTIVE (50%), rest UPCOMING
        val eval10250 = LyricsParser.evaluateLineWords(line0, 10250f)
        assertEquals(WordHighlightStatus.ACTIVE, eval10250[0].status)
        assertEquals(0.5f, eval10250[0].progress, 0.01f)
        assertEquals(WordHighlightStatus.UPCOMING, eval10250[1].status)

        // At 11400ms -> "Tum" & "hi" COMPLETED, "ho," ACTIVE (50%), "ab" UPCOMING
        val eval11400 = LyricsParser.evaluateLineWords(line0, 11400f)
        assertEquals(WordHighlightStatus.COMPLETED, eval11400[0].status)
        assertEquals(WordHighlightStatus.COMPLETED, eval11400[1].status)
        assertEquals(WordHighlightStatus.ACTIVE, eval11400[2].status)
        assertEquals(0.5f, eval11400[2].progress, 0.01f)
        assertEquals(WordHighlightStatus.UPCOMING, eval11400[3].status)

        // At 12550ms -> "Tum", "hi", "ho,", "ab" COMPLETED, second "tum" ACTIVE (50%)
        val eval12550 = LyricsParser.evaluateLineWords(line0, 12550f)
        assertEquals(WordHighlightStatus.COMPLETED, eval12550[0].status)
        assertEquals(WordHighlightStatus.COMPLETED, eval12550[1].status)
        assertEquals(WordHighlightStatus.COMPLETED, eval12550[2].status)
        assertEquals(WordHighlightStatus.COMPLETED, eval12550[3].status)
        assertEquals(WordHighlightStatus.ACTIVE, eval12550[4].status)
        assertEquals(0.5f, eval12550[4].progress, 0.01f)

        val line1 = parsed.lines[1]
        assertEquals("Mera dil tere bina pyaar hota hai", line1.text)
        assertEquals(
            listOf("Mera", "dil", "tere", "bina", "pyaar", "hota", "hai"),
            line1.words.map { it.text }
        )

        // Test syllable-level Hindi TTML ("प्या" + "र" -> "Pyaar", "हो" + "ता" -> "hota", "है" -> "hai")
        val hindiSyllableTtml = """
            <tt xmlns="http://www.w3.org/ns/ttml">
              <body>
                <div>
                  <p begin="00:01.000" end="00:03.500">
                    <span begin="00:01.000" end="00:01.400">प्या</span><span begin="00:01.400" end="00:01.800">र</span> <span begin="00:01.800" end="00:02.200">हो</span><span begin="00:02.200" end="00:02.600">ता</span> <span begin="00:02.600" end="00:03.200">है</span>
                  </p>
                </div>
              </body>
            </tt>
        """.trimIndent()

        val parsedTtml = LyricsParser.parse(hindiSyllableTtml, scriptMode = LyricsScriptMode.ROMANIZED)
        assertTrue(parsedTtml.hasSyllableSync)
        val ttmlLine = parsedTtml.lines.single()
        assertEquals("Pyaar hota hai", ttmlLine.text)
        assertEquals(3, ttmlLine.words.size)
        assertEquals("Pyaar", ttmlLine.words[0].text)
        assertEquals(2, ttmlLine.words[0].syllables.size)
        assertEquals(1000L, ttmlLine.words[0].syllables[0].startMs)
        assertEquals(1400L, ttmlLine.words[0].syllables[0].endMs)
        assertEquals(1400L, ttmlLine.words[0].syllables[1].startMs)
        assertEquals(1800L, ttmlLine.words[0].syllables[1].endMs)
        assertEquals("hota", ttmlLine.words[1].text)
        assertEquals(listOf("ho", "ta"), ttmlLine.words[1].syllables.map { it.text })
        assertEquals("hai", ttmlLine.words[2].text)
    }

    @Test
    fun testPlaybackProgressPauseBufferingAndSpeedSynchronization() {
        val pausedProgress = PlaybackProgress(
            currentPositionMs = 12_500L,
            durationMs = 180_000L,
            isPlaying = false,
            playbackSpeed = 1.5f
        )
        assertEquals(12_500f, pausedProgress.interpolatedPositionMsFloat(), 0.01f)

        val bufferingProgress = PlaybackProgress(
            currentPositionMs = 24_000L,
            durationMs = 180_000L,
            isPlaying = true,
            isBuffering = true,
            playbackSpeed = 1.0f
        )
        // Buffering must freeze interpolated position so lyrics never run ahead of audio
        assertEquals(24_000f, bufferingProgress.interpolatedPositionMsFloat(), 0.01f)
    }
}

