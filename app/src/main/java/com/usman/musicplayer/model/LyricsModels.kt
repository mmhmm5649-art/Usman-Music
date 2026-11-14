package com.usman.musicplayer.model

import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable

enum class SyncQuality {
    CHARACTER,
    WORD,
    LINE,
    PLAIN
}

enum class WordHighlightStatus {
    UPCOMING,
    ACTIVE,
    COMPLETED
}

@Stable
data class LyricSyllable(
    val text: String,
    val startMs: Long,
    val endMs: Long,
    val index: Int = 0,
    val charStartInWord: Int = 0,
    val charEndInWord: Int = text.length,
    val charStartInLine: Int = -1,
    val charEndInLine: Int = -1
) {
    fun progressAt(currentTimeMs: Float): Float {
        if (currentTimeMs <= startMs.toFloat()) return 0f
        if (currentTimeMs >= endMs.toFloat()) return 1f
        val duration = (endMs - startMs).coerceAtLeast(1L).toFloat()
        return ((currentTimeMs - startMs.toFloat()) / duration).coerceIn(0f, 1f)
    }
}

@Stable
data class LyricWord(
    val text: String,
    val startMs: Long,
    val endMs: Long,
    val index: Int = 0,
    val charStart: Int = -1,
    val charEnd: Int = -1,
    val syllables: List<LyricSyllable> = emptyList()
) {
    val hasSyllableTimestamps: Boolean
        get() = syllables.size > 1

    /**
     * Computes exact progress [0f..1f] for this word at [currentTimeMs].
     * If syllable-level timestamps are available, interpolates within the active syllable
     * weighted by character span so multi-syllable words animate syllable-by-syllable.
     */
    fun progressAt(currentTimeMs: Float): Float {
        if (currentTimeMs <= startMs.toFloat()) return 0f
        if (currentTimeMs >= endMs.toFloat()) return 1f
        if (syllables.size > 1 && text.isNotEmpty()) {
            val totalChars = text.length.coerceAtLeast(1).toFloat()
            for (syl in syllables) {
                val sylStartFrac = (syl.charStartInWord.coerceIn(0, text.length)) / totalChars
                val sylEndFrac = (syl.charEndInWord.coerceIn(syl.charStartInWord.coerceAtLeast(0), text.length)) / totalChars
                if (currentTimeMs < syl.startMs.toFloat()) {
                    return sylStartFrac.coerceIn(0f, 1f)
                }
                if (currentTimeMs <= syl.endMs.toFloat()) {
                    val localProg = syl.progressAt(currentTimeMs)
                    return (sylStartFrac + (sylEndFrac - sylStartFrac) * localProg).coerceIn(0f, 1f)
                }
            }
            return 1f
        }
        val duration = (endMs - startMs).coerceAtLeast(1L).toFloat()
        return ((currentTimeMs - startMs.toFloat()) / duration).coerceIn(0f, 1f)
    }

    fun statusAt(currentTimeMs: Float): WordHighlightStatus {
        return when {
            currentTimeMs >= endMs.toFloat() -> WordHighlightStatus.COMPLETED
            currentTimeMs >= startMs.toFloat() -> WordHighlightStatus.ACTIVE
            else -> WordHighlightStatus.UPCOMING
        }
    }
}

@Stable
data class WordHighlightEvaluation(
    val word: LyricWord,
    val status: WordHighlightStatus,
    val progress: Float,
    val activeSyllableIndex: Int = -1,
    val activeSyllableProgress: Float = 0f
)

@Stable
data class LyricLine(
    val id: Int,
    val timestampMs: Long,
    val text: String,
    val isTimestamped: Boolean,
    val syncQuality: SyncQuality = SyncQuality.PLAIN,
    val endTimestampMs: Long = -1L,
    val durationMs: Long = 0L,
    val words: List<LyricWord> = emptyList(),
    val isBackgroundVocal: Boolean = false,
    val singer: String? = null,
    val isOppositeAligned: Boolean = false,
    val backgroundVocalText: String? = null,
    val backgroundVocalWords: List<LyricWord> = emptyList(),
    val translatedText: String? = null,
    val romanizedText: String? = null,
    val hasSyllableSync: Boolean = false
)

@Immutable
data class ParsedLyrics(
    val lines: List<LyricLine>,
    val isSynchronized: Boolean,
    val syncQuality: SyncQuality = SyncQuality.PLAIN,
    val hasSyllableSync: Boolean = false,
    val originalLines: List<LyricLine> = lines,
    val romanizedLines: List<LyricLine> = lines,
    val scriptMode: LyricsScriptMode = LyricsScriptMode.ORIGINAL
) {
    /**
     * Switches the active displayed lines between [LyricsScriptMode.ORIGINAL] and
     * [LyricsScriptMode.ROMANIZED] in-memory without re-parsing or interrupting playback.
     */
    fun withScriptMode(mode: LyricsScriptMode): ParsedLyrics {
        val targetLines = when (mode) {
            LyricsScriptMode.ORIGINAL -> originalLines
            LyricsScriptMode.ROMANIZED -> romanizedLines
        }
        return copy(
            lines = targetLines,
            scriptMode = mode
        )
    }
}

object LyricsParser {

    private val TIMESTAMP_REGEX = Regex("""\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?\]""")
    private val HTML_TAG_REGEX = Regex("""<[^>]*>""")
    private val YRC_LINE_REGEX = Regex("""^\[(\d+),(\d+)\](.*)$""")
    private val QRC_XML_ATTR_REGEX = Regex("""LyricContent\s*=\s*"([^"]+)"""", RegexOption.DOT_MATCHES_ALL)
    private val METADATA_HEADER_REGEX = Regex("""^\[(ti|ar|al|by|offset|re|ve|length|id|qq|total|language):.*\]$""", RegexOption.IGNORE_CASE)
    private val INLINE_TTML_SPAN_REGEX = Regex("""<span\s+begin="([\d:.]+)"\s+end="([\d:.]+)"[^>]*>(.*?)</span>""")
    private val INLINE_LRC_TAG_REGEX = Regex("""<\d{1,2}:\d{2}(?:[.:]\d{1,3})?>""")
    private val INLINE_BRACKET_TIMING_REGEX = Regex("""[\(\[]\d+,\d+(?:,\d+)?[\)\]]""")
    private val YRC_TOKEN_REGEX = Regex("""[\(\[](\d+),(\d+)(?:,\d+)?[\)\]]([^\(\[]*)""")
    private val QRC_TOKEN_REGEX = Regex("""([^\(\[]+?)\((\d+),(\d+)(?:,\d+)?\)""")
    private val INLINE_QRC_CONTAINS_REGEX = Regex("""[^\(\[]+\(\d+,\d+(?:,\d+)?\)""")
    private val ANY_INLINE_TAG_REGEX = Regex("""(<(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?>)|([\(\[](\d+),(\d+)(?:,\d+)?[\)\]])""")
    private val TTML_P_REGEX = Regex("""<p\s+([^>]*?)begin="([^"]+)"([^>]*)>(.*?)</p>""", RegexOption.DOT_MATCHES_ALL)
    private val TTML_ROLE_SPAN_REGEX = Regex("""<span\s+[^>]*ttm:role="([^"]+)"[^>]*>(.*?)</span>""", RegexOption.DOT_MATCHES_ALL)
    private val TTML_TIMED_SPAN_REGEX = Regex("""<span\s+[^>]*begin="([^"]+)"(?:\s+[^>]*?end="([^"]+)"|\s+end="([^"]+)")?[^>]*>(.*?)</span>""", RegexOption.DOT_MATCHES_ALL)
    private val TTML_SIMPLE_SPAN_REGEX = Regex("""<span\s+begin="([^"]+)"(?:\s+end="([^"]+)")?[^>]*>(.*?)</span>""", RegexOption.DOT_MATCHES_ALL)
    private val TTML_END_ATTR_REGEX = Regex("""end="([^"]+)"""")
    private val TTML_AGENT_ATTR_REGEX = Regex("""ttm:agent="([^"]+)"""")

    private data class RawTimedToken(
        val text: String,
        val startMs: Long,
        val endMs: Long,
        val endsWord: Boolean
    )

    /**
     * Parses synchronized lyrics from:
     * - Apple Music TTML (word & syllable spans, background vocals, translations, duet agents)
     * - NetEase YRC (`[lineStart,lineDur](start,dur[,0])token...`)
     * - QQ Music QRC (`[lineStart,lineDur]token(start,dur)...` or QRC XML attribute)
     * - Lyricify Syllable (`[lineStart,lineDur]syllable(start,dur)...` or `[mm:ss.xx]syllable(start,dur)...`)
     * - Enhanced LRC (`[mm:ss.xx]<mm:ss.xx>word...`)
     * - Standard LRC (`[mm:ss.xx]Line text` as an honest line-level fallback without fake word timestamps)
     */
    fun parse(
        raw: String?,
        totalDurationMs: Long = 0L,
        preferRomanized: Boolean = false,
        scriptMode: LyricsScriptMode = if (preferRomanized) LyricsScriptMode.ROMANIZED else LyricsScriptMode.ORIGINAL
    ): ParsedLyrics {
        if (raw.isNullOrBlank()) {
            safeLogD("[LyricsParser] Raw lyrics input is null or blank")
            return ParsedLyrics(emptyList(), isSynchronized = false, syncQuality = SyncQuality.PLAIN)
        }

        val effectiveMode = if (preferRomanized) LyricsScriptMode.ROMANIZED else scriptMode

        // 1. Check for Apple Music TTML format first
        if (raw.contains("<tt", ignoreCase = true) || raw.contains("<p begin=", ignoreCase = true)) {
            val ttmlParsed = parseTtmlLyrics(raw, scriptMode = effectiveMode)
            if (ttmlParsed != null && ttmlParsed.lines.isNotEmpty()) {
                safeLogD("[LyricsParser] Successfully parsed TTML lyrics: ${ttmlParsed.lines.size} lines")
                return ttmlParsed
            }
        }

        // 2. Unwrap QQ Music QRC XML container if present: <Lyric_1 LyricType="1" LyricContent="..."/>
        val unwrappedRaw = QRC_XML_ATTR_REGEX.find(raw)?.groupValues?.getOrNull(1) ?: raw

        val cleaned = cleanHtmlPreservingInlineTags(unwrappedRaw)
        val rawLines = cleaned.lines().map { it.trim() }.filter { it.isNotEmpty() }

        val timestampedLines = mutableListOf<LyricLine>()
        var globalLineCounter = 0
        var hasGenuineWordTimestamps = false
        var hasGenuineSyllableTimestamps = false

        for (line in rawLines) {
            // Skip metadata headers like [ti:...], [ar:...], [al:...], [by:...], [offset:...]
            if (METADATA_HEADER_REGEX.matches(line)) {
                continue
            }

            // 3. Check for [lineStartMs,lineDurMs] header used by NetEase YRC, QQ Music QRC, and Lyricify Syllable
            val yrcQrcMatch = YRC_LINE_REGEX.find(line)
            if (yrcQrcMatch != null) {
                val lineStartMs = yrcQrcMatch.groupValues[1].toLongOrNull() ?: 0L
                val lineDurMs = yrcQrcMatch.groupValues[2].toLongOrNull() ?: 3000L
                val lineEndMs = lineStartMs + lineDurMs
                val body = yrcQrcMatch.groupValues[3]

                val parsedWords = parseBracketTimedBody(body, lineStartMs)
                if (parsedWords != null && parsedWords.words.isNotEmpty()) {
                    hasGenuineWordTimestamps = true
                    if (parsedWords.hasSyllables) hasGenuineSyllableTimestamps = true
                    timestampedLines.add(
                        LyricLine(
                            id = globalLineCounter++,
                            timestampMs = lineStartMs,
                            endTimestampMs = lineEndMs,
                            durationMs = lineDurMs.coerceAtLeast(300L),
                            text = parsedWords.lineText.ifBlank { "♪" },
                            isTimestamped = true,
                            words = parsedWords.words,
                            syncQuality = SyncQuality.WORD,
                            hasSyllableSync = parsedWords.hasSyllables
                        )
                    )
                    continue
                }
            }

            // 4. Standard LRC, Enhanced LRC (<mm:ss.xx>), or LRC-header QRC/Lyricify Syllable ([mm:ss.xx]word(start,dur))
            val matches = TIMESTAMP_REGEX.findAll(line).toList()
            if (matches.isNotEmpty()) {
                // Only treat leading [mm:ss.xx] tags as line timestamps
                val leadingMatches = mutableListOf<MatchResult>()
                var expectedStart = 0
                for (m in matches) {
                    if (m.range.first == expectedStart) {
                        leadingMatches.add(m)
                        expectedStart = m.range.last + 1
                    } else {
                        break
                    }
                }
                if (leadingMatches.isEmpty()) {
                    leadingMatches.add(matches.first())
                    expectedStart = matches.first().range.last + 1
                }

                val lineContent = if (expectedStart < line.length) line.substring(expectedStart).trim() else ""
                val firstLineTimestampMs = parseLrcMatchMs(leadingMatches.first())

                val isBackground = (lineContent.startsWith("(") && lineContent.endsWith(")") && !lineContent.contains(")(")) ||
                    (lineContent.startsWith("[") && lineContent.endsWith("]"))

                var explicitWords: List<LyricWord> = emptyList()
                var cleanLineText = ""
                var lineHasSyllables = false

                // 4a. Check inline TTML spans inside an LRC line: <span begin="..." end="...">
                val ttmlMatches = INLINE_TTML_SPAN_REGEX.findAll(lineContent).toList()

                if (ttmlMatches.isNotEmpty()) {
                    val tokens = buildTtmlTokensFromMatches(lineContent, ttmlMatches)
                    val built = buildWordsFromTimedTokens(tokens)
                    if (built.words.isNotEmpty()) {
                        hasGenuineWordTimestamps = true
                        if (built.hasSyllables) hasGenuineSyllableTimestamps = true
                        explicitWords = built.words
                        cleanLineText = built.lineText
                        lineHasSyllables = built.hasSyllables
                    }
                }

                // 4b. Check Enhanced LRC (<mm:ss.xx>) or inline YRC/QRC/Lyricify Syllable tags
                if (explicitWords.isEmpty()) {
                    val elrcParsed = parseEnhancedLrcOrInlineTimedContent(lineContent, firstLineTimestampMs)
                    if (elrcParsed != null && elrcParsed.words.isNotEmpty()) {
                        hasGenuineWordTimestamps = true
                        if (elrcParsed.hasSyllables) hasGenuineSyllableTimestamps = true
                        explicitWords = elrcParsed.words
                        cleanLineText = elrcParsed.lineText
                        lineHasSyllables = elrcParsed.hasSyllables
                    }
                }

                if (cleanLineText.isBlank()) {
                    cleanLineText = HTML_TAG_REGEX.replace(lineContent, "")
                        .replace(INLINE_LRC_TAG_REGEX, "")
                        .replace(INLINE_BRACKET_TIMING_REGEX, "")
                        .trim()
                        .ifBlank { "♪" }
                }

                for (match in leadingMatches) {
                    val timestampMs = parseLrcMatchMs(match)
                    timestampedLines.add(
                        LyricLine(
                            id = globalLineCounter++,
                            timestampMs = timestampMs,
                            text = cleanLineText,
                            isTimestamped = true,
                            words = explicitWords,
                            isBackgroundVocal = isBackground,
                            syncQuality = if (explicitWords.isNotEmpty()) SyncQuality.WORD else SyncQuality.LINE,
                            hasSyllableSync = lineHasSyllables
                        )
                    )
                }
            }
        }

        if (timestampedLines.isNotEmpty()) {
            val sorted = timestampedLines.sortedBy { it.timestampMs }
            val mapped = sorted.mapIndexed { idx, item ->
                val nextTs = if (idx + 1 < sorted.size) sorted[idx + 1].timestampMs else {
                    if (totalDurationMs > item.timestampMs) totalDurationMs else (item.timestampMs + 4000L)
                }
                val dur = (nextTs - item.timestampMs).coerceAtLeast(500L)

                val finalWords = if (item.words.isNotEmpty()) {
                    val last = item.words.last()
                    val targetEnd = if (last.endMs <= last.startMs || last.endMs > nextTs) {
                        (last.startMs + 1200L).coerceAtMost(nextTs)
                    } else {
                        last.endMs
                    }.coerceAtLeast(last.startMs + 150L)

                    val updatedSyllables = if (last.syllables.isNotEmpty()) {
                        val lastSyl = last.syllables.last()
                        last.syllables.dropLast(1) + lastSyl.copy(endMs = targetEnd.coerceAtLeast(lastSyl.startMs + 100L))
                    } else {
                        emptyList()
                    }
                    val updatedLast = last.copy(endMs = targetEnd, syllables = updatedSyllables)
                    item.words.dropLast(1) + updatedLast
                } else {
                    // Honest line-level LRC fallback: do NOT invent fake word timestamps
                    emptyList()
                }

                item.copy(
                    id = idx,
                    endTimestampMs = maxOf(item.endTimestampMs, nextTs),
                    durationMs = dur,
                    words = finalWords,
                    syncQuality = if (finalWords.isNotEmpty()) SyncQuality.WORD else SyncQuality.LINE,
                    hasSyllableSync = item.hasSyllableSync || finalWords.any { it.hasSyllableTimestamps }
                )
            }

            val romanizedMapped = mapped.map { MultilingualRomanTransliterator.toRomanizedLyricLine(it) }
            val activeLines = if (effectiveMode == LyricsScriptMode.ROMANIZED) romanizedMapped else mapped

            val overallQuality = if (hasGenuineWordTimestamps || mapped.any { it.words.isNotEmpty() }) {
                SyncQuality.WORD
            } else {
                SyncQuality.LINE
            }
            safeLogD("[LyricsParser] Successfully parsed ${mapped.size} synchronized lines. Quality: $overallQuality (syllableSync=$hasGenuineSyllableTimestamps)")
            return ParsedLyrics(
                lines = activeLines,
                isSynchronized = true,
                syncQuality = overallQuality,
                hasSyllableSync = hasGenuineSyllableTimestamps,
                originalLines = mapped,
                romanizedLines = romanizedMapped,
                scriptMode = effectiveMode
            )
        }

        // Plain text fallback (no LRC, YRC, QRC, Lyricify, or TTML tags found)
        val plainOriginalLines = rawLines.mapIndexed { idx, line ->
            val cleanLine = HTML_TAG_REGEX.replace(line, "").trim()
            LyricLine(
                id = idx,
                timestampMs = -1L,
                text = cleanLine,
                isTimestamped = false,
                syncQuality = SyncQuality.PLAIN
            )
        }.filter { it.text.isNotBlank() }

        val plainRomanizedLines = plainOriginalLines.map { MultilingualRomanTransliterator.toRomanizedLyricLine(it) }
        val activePlainLines = if (effectiveMode == LyricsScriptMode.ROMANIZED) plainRomanizedLines else plainOriginalLines

        safeLogD("[LyricsParser] No synchronized tags detected. Parsed ${plainOriginalLines.size} plain text lines")
        return ParsedLyrics(
            lines = activePlainLines,
            isSynchronized = false,
            syncQuality = SyncQuality.PLAIN,
            originalLines = plainOriginalLines,
            romanizedLines = plainRomanizedLines,
            scriptMode = effectiveMode
        )
    }

    private data class BuiltWordList(
        val lineText: String,
        val words: List<LyricWord>,
        val hasSyllables: Boolean
    )

    /**
     * Parses a `[lineStart,lineDur]` body in either:
     * - NetEase YRC prefix style: `(start,dur[,0])token...` or `[start,dur]token...`
     * - QQ Music QRC / Lyricify Syllable suffix style: `token(start,dur[,0])...`
     */
    private fun parseBracketTimedBody(body: String, lineStartMs: Long): BuiltWordList? {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return null

        // Detect whether timing tags are prefix `(start,dur)text` (YRC) or suffix `text(start,dur)` (QRC / Lyricify Syllable)
        val startsWithTimingTag = trimmed.startsWith("(") || trimmed.startsWith("[")

        if (startsWithTimingTag) {
            val matches = YRC_TOKEN_REGEX.findAll(trimmed).toList()
            if (matches.isEmpty()) return null

            val rawPieces = matches.map { m ->
                val wStart = m.groupValues[1].toLongOrNull() ?: lineStartMs
                val wDur = (m.groupValues[2].toLongOrNull() ?: 300L).coerceAtLeast(50L)
                val rawPiece = m.groupValues[3]
                Triple(rawPiece, wStart, wStart + wDur)
            }.filter { it.first.trim().isNotEmpty() }

            if (rawPieces.isEmpty()) return null

            val hasExplicitWordSpaces = rawPieces.any { (piece, _, _) ->
                piece.endsWith(" ") || piece.startsWith(" ")
            }

            val tokens = rawPieces.mapIndexed { idx, (piece, startMs, endMs) ->
                val nextPieceStartsWithSpace = rawPieces.getOrNull(idx + 1)?.first?.startsWith(" ") == true
                val endsWord = if (hasExplicitWordSpaces) {
                    piece.endsWith(" ") || nextPieceStartsWithSpace || idx == rawPieces.lastIndex
                } else {
                    true
                }
                RawTimedToken(
                    text = piece.trim(),
                    startMs = startMs,
                    endMs = endMs,
                    endsWord = endsWord
                )
            }
            return buildWordsFromTimedTokens(tokens)
        } else {
            // QRC / Lyricify Syllable suffix format: `text(start,dur[,0])`
            val matches = QRC_TOKEN_REGEX.findAll(trimmed).toList()
            if (matches.isEmpty()) return null

            val rawPieces = matches.map { m ->
                val rawPiece = m.groupValues[1]
                val wStart = m.groupValues[2].toLongOrNull() ?: lineStartMs
                val wDur = (m.groupValues[3].toLongOrNull() ?: 300L).coerceAtLeast(50L)
                Triple(rawPiece, wStart, wStart + wDur)
            }.filter { it.first.trim().isNotEmpty() }

            if (rawPieces.isEmpty()) return null

            val hasExplicitWordSpaces = rawPieces.any { (piece, _, _) ->
                piece.endsWith(" ") || piece.startsWith(" ")
            }

            val tokens = rawPieces.mapIndexed { idx, (piece, startMs, endMs) ->
                val nextPieceStartsWithSpace = rawPieces.getOrNull(idx + 1)?.first?.startsWith(" ") == true
                val endsWord = if (hasExplicitWordSpaces) {
                    piece.endsWith(" ") || nextPieceStartsWithSpace || idx == rawPieces.lastIndex
                } else {
                    true
                }
                RawTimedToken(
                    text = piece.trim(),
                    startMs = startMs,
                    endMs = endMs,
                    endsWord = endsWord
                )
            }
            return buildWordsFromTimedTokens(tokens)
        }
    }

    /**
     * Parses Enhanced LRC (`<mm:ss.xx>token`), inline YRC (`(start,dur)token`), or inline QRC/Lyricify (`token(start,dur)`).
     */
    private fun parseEnhancedLrcOrInlineTimedContent(
        lineContent: String,
        lineTimestampMs: Long
    ): BuiltWordList? {
        if (!lineContent.trimStart().startsWith("<") &&
            !lineContent.trimStart().startsWith("(") &&
            !lineContent.trimStart().startsWith("[") &&
            INLINE_QRC_CONTAINS_REGEX.containsMatchIn(lineContent)
        ) {
            return parseBracketTimedBody(lineContent, lineTimestampMs)
        }

        val allMatches = ANY_INLINE_TAG_REGEX.findAll(lineContent).toList()
        if (allMatches.isEmpty()) return null

        val rawTokens = mutableListOf<RawTimedToken>()

        // Text before the very first inline tag
        val firstMatch = allMatches.first()
        if (firstMatch.range.first > 0) {
            val preRaw = lineContent.substring(0, firstMatch.range.first)
            val preTrimmed = preRaw.trim()
            if (preTrimmed.isNotEmpty()) {
                val firstTagStart = parseInlineTagStartMs(firstMatch, lineTimestampMs)
                rawTokens.add(
                    RawTimedToken(
                        text = preTrimmed,
                        startMs = lineTimestampMs,
                        endMs = firstTagStart.coerceAtLeast(lineTimestampMs + 200L),
                        endsWord = preRaw.endsWith(" ")
                    )
                )
            }
        }

        val sliceInfos = mutableListOf<Triple<String, Long, Long>>()
        for (i in allMatches.indices) {
            val curr = allMatches[i]
            val next = allMatches.getOrNull(i + 1)
            val startMs = parseInlineTagStartMs(curr, lineTimestampMs)

            val textStart = curr.range.last + 1
            val textEnd = next?.range?.first ?: lineContent.length
            val rawSlice = if (textStart < textEnd) lineContent.substring(textStart, textEnd) else ""

            val endMs = if (curr.groupValues[5].isNotEmpty()) {
                val dur = curr.groupValues[7].toLongOrNull() ?: 500L
                startMs + dur
            } else if (next != null) {
                parseInlineTagStartMs(next, startMs + 500L)
            } else {
                startMs + 800L
            }

            if (rawSlice.trim().isNotEmpty()) {
                sliceInfos.add(Triple(rawSlice, startMs, endMs.coerceAtLeast(startMs + 80L)))
            }
        }

        val hasExplicitSpaces = sliceInfos.any { (slice, _, _) ->
            slice.endsWith(" ") || slice.startsWith(" ")
        }

        for ((idx, triple) in sliceInfos.withIndex()) {
            val (rawSlice, startMs, endMs) = triple
            val nextStartsSpace = sliceInfos.getOrNull(idx + 1)?.first?.startsWith(" ") == true
            val endsWord = if (hasExplicitSpaces) {
                rawSlice.endsWith(" ") || nextStartsSpace || idx == sliceInfos.lastIndex
            } else {
                true
            }
            rawTokens.add(
                RawTimedToken(
                    text = rawSlice.trim(),
                    startMs = startMs,
                    endMs = endMs,
                    endsWord = endsWord
                )
            )
        }

        if (rawTokens.isEmpty()) return null
        return buildWordsFromTimedTokens(rawTokens)
    }

    /**
     * Groups [RawTimedToken]s into [LyricWord]s and [LyricSyllable]s.
     * Contiguous tokens where `endsWord == false` are combined as syllables of the same word
     * (e.g. "ko" + "sa" + "me" -> word "kosame" with 3 syllables), while tokens with `endsWord == true`
     * complete the current word and insert a space before the next word.
     */
    private fun buildWordsFromTimedTokens(tokens: List<RawTimedToken>): BuiltWordList {
        if (tokens.isEmpty()) return BuiltWordList("", emptyList(), false)

        val words = mutableListOf<LyricWord>()
        val lineTextBuilder = StringBuilder()
        val currentWordTokens = mutableListOf<RawTimedToken>()
        var anyMultiSyllable = false

        fun flushCurrentWord() {
            if (currentWordTokens.isEmpty()) return
            val wordStartInLine = if (lineTextBuilder.isEmpty()) 0 else {
                lineTextBuilder.append(" ")
                lineTextBuilder.length
            }
            val wordTextBuilder = StringBuilder()
            val syllables = mutableListOf<LyricSyllable>()

            for ((sylIdx, tok) in currentWordTokens.withIndex()) {
                val charStartInWord = wordTextBuilder.length
                wordTextBuilder.append(tok.text)
                val charEndInWord = wordTextBuilder.length
                val charStartInLine = wordStartInLine + charStartInWord
                val charEndInLine = wordStartInLine + charEndInWord

                syllables.add(
                    LyricSyllable(
                        text = tok.text,
                        startMs = tok.startMs,
                        endMs = tok.endMs.coerceAtLeast(tok.startMs + 40L),
                        index = sylIdx,
                        charStartInWord = charStartInWord,
                        charEndInWord = charEndInWord,
                        charStartInLine = charStartInLine,
                        charEndInLine = charEndInLine
                    )
                )
            }

            val wordText = wordTextBuilder.toString()
            lineTextBuilder.append(wordText)
            val wStart = currentWordTokens.first().startMs
            val wEnd = currentWordTokens.last().endMs.coerceAtLeast(wStart + 80L)
            if (syllables.size > 1) anyMultiSyllable = true

            words.add(
                LyricWord(
                    text = wordText,
                    startMs = wStart,
                    endMs = wEnd,
                    index = words.size,
                    charStart = wordStartInLine,
                    charEnd = wordStartInLine + wordText.length,
                    syllables = syllables
                )
            )
            currentWordTokens.clear()
        }

        for (token in tokens) {
            if (token.text.isEmpty()) continue
            currentWordTokens.add(token)
            if (token.endsWord) {
                flushCurrentWord()
            }
        }
        flushCurrentWord()

        return BuiltWordList(
            lineText = lineTextBuilder.toString(),
            words = words,
            hasSyllables = anyMultiSyllable
        )
    }

    private fun buildTtmlTokensFromMatches(
        content: String,
        spanMatches: List<MatchResult>
    ): List<RawTimedToken> {
        val tokens = mutableListOf<RawTimedToken>()
        for ((idx, sMatch) in spanMatches.withIndex()) {
            val wStart = parseTtmlTime(sMatch.groupValues[1])
            val wEnd = if (sMatch.groupValues[2].isNotBlank()) {
                parseTtmlTime(sMatch.groupValues[2])
            } else {
                wStart + 500L
            }
            val rawInner = HTML_TAG_REGEX.replace(sMatch.groupValues[3], "")
            val trimmedInner = decodeXmlEntities(rawInner).trim()
            if (trimmedInner.isEmpty()) continue

            val nextMatch = spanMatches.getOrNull(idx + 1)
            val betweenText = if (nextMatch != null) {
                content.substring(sMatch.range.last + 1, nextMatch.range.first)
            } else {
                content.substring(sMatch.range.last + 1)
            }
            val trailingBetweenPunct = decodeXmlEntities(
                betweenText.takeWhile { !it.isWhitespace() && !it.isLetterOrDigit() && it != '<' && it != '>' }
            )
            val nextInnerStartsWithSpace = if (nextMatch != null) {
                HTML_TAG_REGEX.replace(nextMatch.groupValues[3], "").startsWith(" ")
            } else {
                false
            }
            // In TTML, zero whitespace between </span> and <span (and no trailing/leading space inside span)
            // means the two spans are syllables of the same word!
            val hasWhitespaceAfter = rawInner.endsWith(" ") ||
                betweenText.any { it.isWhitespace() } ||
                trailingBetweenPunct.isNotEmpty() ||
                nextInnerStartsWithSpace ||
                idx == spanMatches.lastIndex

            tokens.add(
                RawTimedToken(
                    text = trimmedInner + trailingBetweenPunct,
                    startMs = wStart,
                    endMs = wEnd.coerceAtLeast(wStart + 50L),
                    endsWord = hasWhitespaceAfter
                )
            )
        }
        return tokens
    }

    /**
     * Parses native Apple Music TTML / XML lyrics into word- and syllable-synchronized [LyricLine] structures,
     * including background vocals (`ttm:role="x-bg"`), translations (`ttm:role="x-translation"`),
     * romanizations (`ttm:role="x-roman"`), and secondary vocal alignment (`ttm:agent="v2"`).
     */
    private fun parseTtmlLyrics(
        raw: String,
        scriptMode: LyricsScriptMode = LyricsScriptMode.ORIGINAL
    ): ParsedLyrics? {
        try {
            val pMatches = TTML_P_REGEX.findAll(raw).toList()
            if (pMatches.isEmpty()) return null

            val lines = mutableListOf<LyricLine>()
            var anySyllableSync = false

            for ((lineIdx, pMatch) in pMatches.withIndex()) {
                val preAttrs = pMatch.groupValues[1]
                val beginStr = pMatch.groupValues[2]
                val postAttrs = pMatch.groupValues[3]
                val allAttrs = "$preAttrs $postAttrs"
                var content = pMatch.groupValues[4]

                val startMs = parseTtmlTime(beginStr)
                val endAttrMatch = TTML_END_ATTR_REGEX.find(allAttrs)
                val endMs = if (endAttrMatch != null) parseTtmlTime(endAttrMatch.groupValues[1]) else (startMs + 4000L)

                val agentMatch = TTML_AGENT_ATTR_REGEX.find(allAttrs)
                val agent = agentMatch?.groupValues?.getOrNull(1)
                val isOppositeAligned = agent != null && !agent.equals("v1", ignoreCase = true)

                var translatedText: String? = null
                var romanizedText: String? = null
                var bgVocalText: String? = null
                var bgVocalWords: List<LyricWord> = emptyList()

                // Extract special ttm:role spans first so they don't pollute the lead vocal spans
                val roleMatches = TTML_ROLE_SPAN_REGEX.findAll(content).toList()
                if (roleMatches.isNotEmpty()) {
                    for (rm in roleMatches) {
                        val role = rm.groupValues[1].trim()
                        val roleInner = rm.groupValues[2]
                        when {
                            role.equals("x-translation", ignoreCase = true) -> {
                                translatedText = decodeXmlEntities(HTML_TAG_REGEX.replace(roleInner, "")).trim().takeIf { it.isNotEmpty() }
                            }
                            role.equals("x-roman", ignoreCase = true) -> {
                                romanizedText = decodeXmlEntities(HTML_TAG_REGEX.replace(roleInner, "")).trim().takeIf { it.isNotEmpty() }
                            }
                            role.equals("x-bg", ignoreCase = true) -> {
                                val bgSpans = TTML_SIMPLE_SPAN_REGEX.findAll(roleInner).toList()
                                if (bgSpans.isNotEmpty()) {
                                    val bgTokens = buildTtmlTokensFromMatches(roleInner, bgSpans)
                                    val builtBg = buildWordsFromTimedTokens(bgTokens)
                                    bgVocalText = builtBg.lineText.takeIf { it.isNotEmpty() }
                                    bgVocalWords = builtBg.words
                                } else {
                                    bgVocalText = decodeXmlEntities(HTML_TAG_REGEX.replace(roleInner, "")).trim().takeIf { it.isNotEmpty() }
                                }
                            }
                        }
                    }
                    content = TTML_ROLE_SPAN_REGEX.replace(content, "")
                }

                val spanMatches = TTML_SIMPLE_SPAN_REGEX.findAll(content).toList().ifEmpty {
                    TTML_TIMED_SPAN_REGEX.findAll(content).mapNotNull { m ->
                        val b = m.groupValues[1]
                        val e = m.groupValues[2].ifEmpty { m.groupValues[3] }
                        val txt = m.groupValues[4]
                        TTML_SIMPLE_SPAN_REGEX.find("""<span begin="$b" end="$e">$txt</span>""")
                    }.toList()
                }

                val built = if (spanMatches.isNotEmpty()) {
                    val tokens = buildTtmlTokensFromMatches(content, spanMatches)
                    buildWordsFromTimedTokens(tokens)
                } else {
                    BuiltWordList("", emptyList(), false)
                }

                if (built.hasSyllables) anySyllableSync = true

                val cleanText = if (built.lineText.isNotEmpty()) {
                    built.lineText
                } else {
                    decodeXmlEntities(HTML_TAG_REGEX.replace(content, "")).trim().ifBlank { "♪" }
                }

                lines.add(
                    LyricLine(
                        id = lineIdx,
                        timestampMs = startMs,
                        endTimestampMs = endMs.coerceAtLeast(startMs + 300L),
                        durationMs = (endMs - startMs).coerceAtLeast(500L),
                        text = cleanText,
                        isTimestamped = true,
                        words = built.words,
                        syncQuality = if (built.words.isNotEmpty()) SyncQuality.WORD else SyncQuality.LINE,
                        singer = agent,
                        isOppositeAligned = isOppositeAligned,
                        backgroundVocalText = bgVocalText,
                        backgroundVocalWords = bgVocalWords,
                        translatedText = translatedText,
                        romanizedText = romanizedText,
                        hasSyllableSync = built.hasSyllables
                    )
                )
            }

            if (lines.isNotEmpty()) {
                val sortedOriginal = lines.sortedBy { it.timestampMs }.mapIndexed { idx, l -> l.copy(id = idx) }
                val sortedRomanized = sortedOriginal.map { MultilingualRomanTransliterator.toRomanizedLyricLine(it) }
                val activeLines = if (scriptMode == LyricsScriptMode.ROMANIZED) sortedRomanized else sortedOriginal
                val hasWords = sortedOriginal.any { it.words.isNotEmpty() }
                return ParsedLyrics(
                    lines = activeLines,
                    isSynchronized = true,
                    syncQuality = if (hasWords) SyncQuality.WORD else SyncQuality.LINE,
                    hasSyllableSync = anySyllableSync,
                    originalLines = sortedOriginal,
                    romanizedLines = sortedRomanized,
                    scriptMode = scriptMode
                )
            }
        } catch (e: Exception) {
            safeLogE("[LyricsParser] Failed to parse TTML lyrics: ${e.message}", e)
        }
        return null
    }

    /**
     * Evaluates every word in [line] at [currentTimeMs], returning its exact status
     * (UPCOMING, ACTIVE, or COMPLETED), word progress [0f..1f], and active syllable progress.
     */
    fun evaluateLineWords(line: LyricLine, currentTimeMs: Float): List<WordHighlightEvaluation> {
        if (line.words.isEmpty()) return emptyList()
        return line.words.map { word ->
            val status = word.statusAt(currentTimeMs)
            val progress = word.progressAt(currentTimeMs)
            var activeSylIdx = -1
            var activeSylProg = 0f
            if (status == WordHighlightStatus.ACTIVE && word.syllables.isNotEmpty()) {
                for (syl in word.syllables) {
                    if (currentTimeMs >= syl.startMs.toFloat() && currentTimeMs < syl.endMs.toFloat()) {
                        activeSylIdx = syl.index
                        activeSylProg = syl.progressAt(currentTimeMs)
                        break
                    } else if (currentTimeMs >= syl.endMs.toFloat()) {
                        activeSylIdx = syl.index
                        activeSylProg = 1f
                    }
                }
            } else if (status == WordHighlightStatus.COMPLETED && word.syllables.isNotEmpty()) {
                activeSylIdx = word.syllables.lastIndex
                activeSylProg = 1f
            }
            WordHighlightEvaluation(
                word = word,
                status = status,
                progress = progress,
                activeSyllableIndex = activeSylIdx,
                activeSyllableProgress = activeSylProg
            )
        }
    }

    private fun parseLrcMatchMs(match: MatchResult): Long {
        val minutes = match.groupValues[1].toLongOrNull() ?: 0L
        val seconds = match.groupValues[2].toLongOrNull() ?: 0L
        val millisStr = match.groupValues.getOrNull(3)
        val millis = when {
            millisStr.isNullOrEmpty() -> 0L
            millisStr.length == 1 -> millisStr.toLong() * 100L
            millisStr.length == 2 -> millisStr.toLong() * 10L
            else -> millisStr.take(3).toLong()
        }
        return (minutes * 60 + seconds) * 1000L + millis
    }

    private fun parseInlineTagStartMs(match: MatchResult, fallbackMs: Long): Long {
        return if (match.groupValues[1].isNotEmpty()) {
            val m = match.groupValues[2].toLongOrNull() ?: 0L
            val s = match.groupValues[3].toLongOrNull() ?: 0L
            val msStr = match.groupValues[4]
            val ms = when {
                msStr.isEmpty() -> 0L
                msStr.length == 1 -> msStr.toLong() * 100L
                msStr.length == 2 -> msStr.toLong() * 10L
                else -> msStr.take(3).toLong()
            }
            (m * 60 + s) * 1000L + ms
        } else {
            match.groupValues[6].toLongOrNull() ?: fallbackMs
        }
    }

    private fun decodeXmlEntities(raw: String): String {
        return raw
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#039;", "'")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")
    }

    private fun cleanHtmlPreservingInlineTags(raw: String): String {
        return decodeXmlEntities(
            raw
                .replace("<br/>", "\n")
                .replace("<br>", "\n")
                .replace("</br>", "\n")
                .replace("<p>", "\n")
                .replace("</p>", "\n")
        )
    }

    fun findActiveIndex(lines: List<LyricLine>, currentPositionMs: Long): Int {
        if (lines.isEmpty()) return -1
        if (!lines.any { it.isTimestamped }) return -1
        if (currentPositionMs < lines.first().timestampMs) return 0

        var low = 0
        var high = lines.size - 1
        var result = 0

        while (low <= high) {
            val mid = (low + high) ushr 1
            val line = lines[mid]
            if (line.isTimestamped && line.timestampMs <= currentPositionMs) {
                result = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return result
    }

    fun parseTtmlTime(time: String): Long {
        return try {
            val clean = time.trim()
            if (clean.endsWith("ms", ignoreCase = true)) {
                return clean.removeSuffix("ms").removeSuffix("MS").trim().toDoubleOrNull()?.toLong() ?: 0L
            }
            val withoutS = clean.removeSuffix("s").removeSuffix("S")
            if (withoutS.contains(":")) {
                val parts = withoutS.split(":")
                if (parts.size == 3) {
                    val hours = parts[0].toLongOrNull() ?: 0L
                    val minutes = parts[1].toLongOrNull() ?: 0L
                    val secParts = parts[2].split(".")
                    val seconds = secParts[0].toLongOrNull() ?: 0L
                    val millis = if (secParts.size > 1) secParts[1].padEnd(3, '0').take(3).toLongOrNull() ?: 0L else 0L
                    (hours * 3600 + minutes * 60 + seconds) * 1000L + millis
                } else if (parts.size == 2) {
                    val minutes = parts[0].toLongOrNull() ?: 0L
                    val secParts = parts[1].split(".")
                    val seconds = secParts[0].toLongOrNull() ?: 0L
                    val millis = if (secParts.size > 1) secParts[1].padEnd(3, '0').take(3).toLongOrNull() ?: 0L else 0L
                    (minutes * 60 + seconds) * 1000L + millis
                } else {
                    0L
                }
            } else {
                val secDouble = withoutS.toDoubleOrNull() ?: 0.0
                (secDouble * 1000.0).toLong()
            }
        } catch (e: Exception) {
            0L
        }
    }

    private fun safeLogD(msg: String) {
        try {
            Log.d("USMAN_LYRICS", msg)
        } catch (_: Throwable) {
        }
    }

    private fun safeLogE(msg: String, tr: Throwable? = null) {
        try {
            Log.e("USMAN_LYRICS", msg, tr)
        } catch (_: Throwable) {
        }
    }
}
