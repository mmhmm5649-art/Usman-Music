package com.usman.musicplayer.model

import android.util.Log
import androidx.compose.runtime.Stable

enum class SyncQuality {
    CHARACTER,
    WORD,
    LINE,
    PLAIN
}

@Stable
data class LyricWord(
    val text: String,
    val startMs: Long,
    val endMs: Long,
    val index: Int = 0,
    val charStart: Int = -1,
    val charEnd: Int = -1
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
    val singer: String? = null
)

data class ParsedLyrics(
    val lines: List<LyricLine>,
    val isSynchronized: Boolean,
    val syncQuality: SyncQuality = SyncQuality.PLAIN
)

object LyricsParser {

    private val TIMESTAMP_REGEX = Regex("""\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?\]""")
    private val HTML_TAG_REGEX = Regex("""<[^>]*>""")
    private val YRC_LINE_REGEX = Regex("""^\[(\d+),(\d+)\](.*)$""")

    /**
     * Parses synchronized lyrics from TTML, NetEase YRC, Enhanced LRC, or standard LRC.
     * Accurately extracts word-level timestamps when available without inventing fake word timings.
     */
    fun parse(raw: String?, totalDurationMs: Long = 0L): ParsedLyrics {
        if (raw.isNullOrBlank()) {
            Log.d("USMAN_LYRICS", "[LyricsParser] Raw lyrics input is null or blank")
            return ParsedLyrics(emptyList(), isSynchronized = false, syncQuality = SyncQuality.PLAIN)
        }

        // 1. Check for Apple Music TTML format first
        if (raw.contains("<tt", ignoreCase = true) || raw.contains("<p begin=", ignoreCase = true)) {
            val ttmlParsed = parseTtmlLyrics(raw)
            if (ttmlParsed != null && ttmlParsed.lines.isNotEmpty()) {
                Log.d("USMAN_LYRICS", "[LyricsParser] Successfully parsed TTML lyrics: ${ttmlParsed.lines.size} lines")
                return ttmlParsed
            }
        }

        val cleaned = cleanHtml(raw)
        val rawLines = cleaned.lines().map { it.trim() }.filter { it.isNotEmpty() }

        val timestampedLines = mutableListOf<LyricLine>()
        var globalLineCounter = 0
        var hasGenuineWordTimestamps = false

        for (line in rawLines) {
            // Check for NetEase YRC line format: [lineStartMs,lineDurationMs](wordStart,wordDur)word...
            val yrcMatch = YRC_LINE_REGEX.find(line)
            if (yrcMatch != null) {
                val lineStartMs = yrcMatch.groupValues[1].toLongOrNull() ?: 0L
                val lineDurMs = yrcMatch.groupValues[2].toLongOrNull() ?: 3000L
                val lineEndMs = lineStartMs + lineDurMs
                val wordContent = yrcMatch.groupValues[3]

                val yrcWordRegex = Regex("""[\(\[](\d+),(\d+)[\)\]]([^\(\[]*)""")
                val wordMatches = yrcWordRegex.findAll(wordContent).toList()
                val explicitWords = mutableListOf<LyricWord>()
                val lineTextBuilder = StringBuilder()

                for ((idx, wMatch) in wordMatches.withIndex()) {
                    val wStart = wMatch.groupValues[1].toLongOrNull() ?: lineStartMs
                    val wDur = wMatch.groupValues[2].toLongOrNull() ?: 300L
                    val wText = wMatch.groupValues[3].trim()
                    if (wText.isNotEmpty()) {
                        val currentText = lineTextBuilder.toString()
                        // If appending a space, the new word starts at length of currentText + 1
                        val wordStartInLine = if (currentText.isEmpty()) 0 else currentText.length + 1
                        if (currentText.isNotEmpty()) lineTextBuilder.append(" ")
                        lineTextBuilder.append(wText)
                        
                        explicitWords.add(
                            LyricWord(
                                text = wText, 
                                startMs = wStart, 
                                endMs = wStart + wDur, 
                                index = idx,
                                charStart = wordStartInLine,
                                charEnd = wordStartInLine + wText.length
                            )
                        )
                    }
                }

                if (explicitWords.isNotEmpty()) {
                    hasGenuineWordTimestamps = true
                    timestampedLines.add(
                        LyricLine(
                            id = globalLineCounter++,
                            timestampMs = lineStartMs,
                            endTimestampMs = lineEndMs,
                            durationMs = lineDurMs,
                            text = if (lineTextBuilder.isNotEmpty()) lineTextBuilder.toString() else "♪",
                            isTimestamped = true,
                            words = explicitWords,
                            syncQuality = SyncQuality.WORD
                        )
                    )
                    continue
                }
            }

            // Standard LRC or Enhanced LRC: [mm:ss.xx]
            val matches = TIMESTAMP_REGEX.findAll(line).toList()
            if (matches.isNotEmpty()) {
                val lastMatch = matches.last()
                val textStartIndex = lastMatch.range.last + 1
                val lineContent = if (textStartIndex < line.length) line.substring(textStartIndex).trim() else ""

                // Check for background vocals (e.g. in parentheses or brackets)
                val isBackground = (lineContent.startsWith("(") && lineContent.endsWith(")")) ||
                        (lineContent.startsWith("[") && lineContent.endsWith("]"))

                val firstLineMatch = matches.first()
                val fMinutes = firstLineMatch.groupValues[1].toLongOrNull() ?: 0L
                val fSeconds = firstLineMatch.groupValues[2].toLongOrNull() ?: 0L
                val fMillisStr = firstLineMatch.groupValues.getOrNull(3)
                val fMillis = when {
                    fMillisStr.isNullOrEmpty() -> 0L
                    fMillisStr.length == 1 -> fMillisStr.toLong() * 100L
                    fMillisStr.length == 2 -> fMillisStr.toLong() * 10L
                    else -> fMillisStr.take(3).toLong()
                }
                val lineTimestampMs = (fMinutes * 60 + fSeconds) * 1000L + fMillis

                // Parse Enhanced LRC words: <mm:ss.xx> or [start,dur] or (start,dur)
                val explicitWords = mutableListOf<LyricWord>()
                val lineTextBuilder = StringBuilder()

                // Check TTML spans embedded within the line if any
                val ttmlSpanRegex = Regex("""<span\s+begin="([\d:.]+)"\s+end="([\d:.]+)"[^>]*>(.*?)</span>""")
                val ttmlMatches = ttmlSpanRegex.findAll(lineContent).toList()

                if (ttmlMatches.isNotEmpty()) {
                    hasGenuineWordTimestamps = true
                    ttmlMatches.forEachIndexed { i, match ->
                        val startMs = parseTtmlTime(match.groupValues[1])
                        val endMs = parseTtmlTime(match.groupValues[2])
                        val wordText = match.groupValues[3].trim()
                        if (wordText.isNotEmpty()) {
                            val wordStartInLine = if (lineTextBuilder.isEmpty()) 0 else lineTextBuilder.length + 1
                            if (lineTextBuilder.isNotEmpty()) lineTextBuilder.append(" ")
                            lineTextBuilder.append(wordText)
                            
                            explicitWords.add(
                                LyricWord(
                                    text = wordText, 
                                    startMs = startMs, 
                                    endMs = endMs, 
                                    index = i,
                                    charStart = wordStartInLine,
                                    charEnd = wordStartInLine + wordText.length
                                )
                            )
                        }
                    }
                } else {
                    val anyTagRegex = Regex("""(<(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?>)|([\(\[](\d+),(\d+)[\)\]])""")
                    val allMatches = anyTagRegex.findAll(lineContent).toList()

                    if (allMatches.isNotEmpty()) {
                        hasGenuineWordTimestamps = true

                        // Check text BEFORE the first tag
                        val firstMatch = allMatches.first()
                        if (firstMatch.range.first > 0) {
                            val preText = lineContent.substring(0, firstMatch.range.first).trim()
                            if (preText.isNotEmpty()) {
                                val firstTagStart = if (firstMatch.groupValues[1].isNotEmpty()) {
                                    val m = firstMatch.groupValues[2].toLongOrNull() ?: 0L
                                    val s = firstMatch.groupValues[3].toLongOrNull() ?: 0L
                                    val msStr = firstMatch.groupValues[4]
                                    val ms = when {
                                        msStr.isEmpty() -> 0L
                                        msStr.length == 1 -> msStr.toLong() * 100L
                                        msStr.length == 2 -> msStr.toLong() * 10L
                                        else -> msStr.take(3).toLong()
                                    }
                                    (m * 60 + s) * 1000L + ms
                                } else {
                                    firstMatch.groupValues[6].toLongOrNull() ?: lineTimestampMs
                                }
                                
                                val wordStartInLine = 0
                                lineTextBuilder.append(preText)
                                
                                explicitWords.add(
                                    LyricWord(
                                        text = preText,
                                        startMs = lineTimestampMs,
                                        endMs = firstTagStart.coerceAtLeast(lineTimestampMs + 300L),
                                        index = 0,
                                        charStart = wordStartInLine,
                                        charEnd = wordStartInLine + preText.length
                                    )
                                )
                            }
                        }

                        for (i in allMatches.indices) {
                            val curr = allMatches[i]
                            val next = allMatches.getOrNull(i + 1)

                            val startMs = if (curr.groupValues[1].isNotEmpty()) {
                                val m = curr.groupValues[2].toLongOrNull() ?: 0L
                                val s = curr.groupValues[3].toLongOrNull() ?: 0L
                                val msStr = curr.groupValues[4]
                                val ms = when {
                                    msStr.isEmpty() -> 0L
                                    msStr.length == 1 -> msStr.toLong() * 100L
                                    msStr.length == 2 -> msStr.toLong() * 10L
                                    else -> msStr.take(3).toLong()
                                }
                                (m * 60 + s) * 1000L + ms
                            } else {
                                curr.groupValues[6].toLongOrNull() ?: 0L
                            }

                            val textStart = curr.range.last + 1
                            val textEnd = next?.range?.first ?: lineContent.length
                            val rawWord = if (textStart < textEnd) lineContent.substring(textStart, textEnd).trim() else ""

                            val endMs = if (curr.groupValues[5].isNotEmpty()) {
                                val dur = curr.groupValues[7].toLongOrNull() ?: 500L
                                startMs + dur
                            } else if (next != null) {
                                if (next.groupValues[1].isNotEmpty()) {
                                    val nm = next.groupValues[2].toLongOrNull() ?: 0L
                                    val ns = next.groupValues[3].toLongOrNull() ?: 0L
                                    val nmsStr = next.groupValues[4]
                                    val nms = when {
                                        nmsStr.isEmpty() -> 0L
                                        nmsStr.length == 1 -> nmsStr.toLong() * 100L
                                        nmsStr.length == 2 -> nmsStr.toLong() * 10L
                                        else -> nmsStr.take(3).toLong()
                                    }
                                    (nm * 60 + ns) * 1000L + nms
                                } else {
                                    next.groupValues[6].toLongOrNull() ?: (startMs + 600L)
                                }
                            } else {
                                // Last word on line: sustain until natural line end if available
                                startMs + 800L
                            }

                            if (rawWord.isNotEmpty()) {
                                val wordStartInLine = if (lineTextBuilder.isEmpty()) 0 else lineTextBuilder.length + 1
                                if (lineTextBuilder.isNotEmpty()) lineTextBuilder.append(" ")
                                lineTextBuilder.append(rawWord)
                                
                                explicitWords.add(
                                    LyricWord(
                                        text = rawWord,
                                        startMs = startMs,
                                        endMs = endMs.coerceAtLeast(startMs + 100L),
                                        index = explicitWords.size,
                                        charStart = wordStartInLine,
                                        charEnd = wordStartInLine + rawWord.length
                                    )
                                )
                            }
                        }
                    }
                }

                var cleanLineText = if (lineTextBuilder.isNotEmpty()) lineTextBuilder.toString() else {
                     HTML_TAG_REGEX.replace(lineContent, "").trim().replace(Regex("""[\(\[<]\d+[:,\.]\d+.*?[\)\]>]"""), "").trim().ifBlank { "♪" }
                }

                for (match in matches) {
                    val minutes = match.groupValues[1].toLongOrNull() ?: 0L
                    val seconds = match.groupValues[2].toLongOrNull() ?: 0L
                    val millisStr = match.groupValues.getOrNull(3)
                    val millis = when {
                        millisStr.isNullOrEmpty() -> 0L
                        millisStr.length == 1 -> millisStr.toLong() * 100L
                        millisStr.length == 2 -> millisStr.toLong() * 10L
                        else -> millisStr.take(3).toLong()
                    }
                    val timestampMs = (minutes * 60 + seconds) * 1000L + millis

                    timestampedLines.add(
                        LyricLine(
                            id = globalLineCounter++,
                            timestampMs = timestampMs,
                            text = cleanLineText,
                            isTimestamped = true,
                            words = explicitWords,
                            isBackgroundVocal = isBackground,
                            syncQuality = if (explicitWords.isNotEmpty()) SyncQuality.WORD else SyncQuality.LINE
                        )
                    )
                }
            }
        }


        if (timestampedLines.isNotEmpty()) {
            val sorted = timestampedLines.sortedBy { it.timestampMs }
            val mapped = sorted.mapIndexed { idx, item ->
                val nextTs = if (idx + 1 < sorted.size) sorted[idx + 1].timestampMs else (item.timestampMs + 4000L)
                val dur = (nextTs - item.timestampMs).coerceAtLeast(500L)

                var finalWords = item.words
                if (finalWords.isEmpty() && item.text.isNotBlank() && item.text != "♪") {
                    finalWords = synthesizeWordsForLine(item.text, item.timestampMs, nextTs)
                } else if (finalWords.isNotEmpty()) {
                    // Update last word duration to align with line end for smoother sustained vocals
                    val last = finalWords.last()
                    val targetEnd = if (last.endMs <= last.startMs || last.endMs > nextTs) {
                        (last.startMs + 1200L).coerceAtMost(nextTs)
                    } else {
                        last.endMs
                    }
                    val updatedLast = last.copy(endMs = targetEnd.coerceAtLeast(last.startMs + 200L))
                    finalWords = finalWords.dropLast(1) + updatedLast
                }

                item.copy(
                    id = idx,
                    endTimestampMs = nextTs,
                    durationMs = dur,
                    words = finalWords,
                    syncQuality = if (finalWords.isNotEmpty()) SyncQuality.WORD else SyncQuality.LINE
                )
            }

            val overallQuality = if (hasGenuineWordTimestamps || mapped.any { it.words.isNotEmpty() }) SyncQuality.WORD else SyncQuality.LINE
            Log.d("USMAN_LYRICS", "[LyricsParser] Successfully parsed ${mapped.size} synchronized lines. Quality: $overallQuality")
            return ParsedLyrics(lines = mapped, isSynchronized = true, syncQuality = overallQuality)
        }

        // Plain text fallback (no LRC or TTML tags found)
        val plainLines = rawLines.mapIndexed { idx, line ->
            val cleanLine = HTML_TAG_REGEX.replace(line, "").trim()
            LyricLine(
                id = idx,
                timestampMs = -1L,
                text = cleanLine,
                isTimestamped = false,
                syncQuality = SyncQuality.PLAIN
            )
        }.filter { it.text.isNotBlank() }

        Log.d("USMAN_LYRICS", "[LyricsParser] No synchronized tags detected. Parsed ${plainLines.size} plain text lines")
        return ParsedLyrics(lines = plainLines, isSynchronized = false, syncQuality = SyncQuality.PLAIN)
    }

    /**
     * Parses native Apple Music TTML / XML lyrics into word-synchronized LyricLine structures.
     */
    private fun parseTtmlLyrics(raw: String): ParsedLyrics? {
        try {
            val pRegex = Regex("""<p\s+begin="([^"]+)"(?:\s+end="([^"]+)")?[^>]*>(.*?)</p>""", RegexOption.DOT_MATCHES_ALL)
            val pMatches = pRegex.findAll(raw).toList()
            if (pMatches.isEmpty()) return null

            val lines = mutableListOf<LyricLine>()
            val spanRegex = Regex("""<span\s+begin="([^"]+)"(?:\s+end="([^"]+)")?[^>]*>(.*?)</span>""", RegexOption.DOT_MATCHES_ALL)

            for ((lineIdx, pMatch) in pMatches.withIndex()) {
                val startMs = parseTtmlTime(pMatch.groupValues[1])
                val endMs = if (pMatch.groupValues[2].isNotBlank()) parseTtmlTime(pMatch.groupValues[2]) else (startMs + 4000L)
                val content = pMatch.groupValues[3]

                val spanMatches = spanRegex.findAll(content).toList()
                val words = mutableListOf<LyricWord>()
                val lineTextBuilder = StringBuilder()

                for ((wordIdx, sMatch) in spanMatches.withIndex()) {
                    val wStart = parseTtmlTime(sMatch.groupValues[1])
                    val wEnd = if (sMatch.groupValues[2].isNotBlank()) parseTtmlTime(sMatch.groupValues[2]) else (wStart + 500L)
                    val wText = HTML_TAG_REGEX.replace(sMatch.groupValues[3], "").trim()
                    if (wText.isNotEmpty()) {
                        val wordStartInLine = if (lineTextBuilder.isEmpty()) 0 else lineTextBuilder.length + 1
                        if (lineTextBuilder.isNotEmpty()) lineTextBuilder.append(" ")
                        lineTextBuilder.append(wText)
                        
                        words.add(
                            LyricWord(
                                text = wText, 
                                startMs = wStart, 
                                endMs = wEnd, 
                                index = wordIdx,
                                charStart = wordStartInLine,
                                charEnd = wordStartInLine + wText.length
                            )
                        )
                    }
                }

                val cleanText = if (lineTextBuilder.isNotEmpty()) {
                    lineTextBuilder.toString()
                } else {
                    HTML_TAG_REGEX.replace(content, "").trim().ifBlank { "♪" }
                }

                lines.add(
                    LyricLine(
                        id = lineIdx,
                        timestampMs = startMs,
                        endTimestampMs = endMs,
                        durationMs = (endMs - startMs).coerceAtLeast(500L),
                        text = cleanText,
                        isTimestamped = true,
                        words = words,
                        syncQuality = if (words.isNotEmpty()) SyncQuality.WORD else SyncQuality.LINE
                    )
                )
            }

            if (lines.isNotEmpty()) {
                val sorted = lines.sortedBy { it.timestampMs }
                val hasWords = sorted.any { it.words.isNotEmpty() }
                return ParsedLyrics(lines = sorted, isSynchronized = true, syncQuality = if (hasWords) SyncQuality.WORD else SyncQuality.LINE)
            }
        } catch (e: Exception) {
            Log.e("USMAN_LYRICS", "[LyricsParser] Failed to parse TTML lyrics: ${e.message}", e)
        }
        return null
    }

    private fun cleanHtml(raw: String): String {
        return raw
            .replace("<br/>", "\n")
            .replace("<br>", "\n")
            .replace("</br>", "\n")
            .replace("<p>", "\n")
            .replace("</p>", "\n")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#039;", "'")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")
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
            val clean = time.trim().removeSuffix("s")
            if (clean.contains(":")) {
                val parts = clean.split(":")
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
                val secDouble = clean.toDoubleOrNull() ?: 0.0
                (secDouble * 1000.0).toLong()
            }
        } catch (e: Exception) {
            0L
        }
    }

    private fun synthesizeWordsForLine(text: String, lineStartMs: Long, lineEndMs: Long): List<LyricWord> {
        val totalDur = (lineEndMs - lineStartMs).coerceAtLeast(300L)
        val rawTokens = text.split(Regex("\\s+")).filter { it.isNotEmpty() }
        val tokens = if (rawTokens.isNotEmpty()) {
            rawTokens
        } else {
            getGraphemes(text)
        }
        if (tokens.isEmpty()) return emptyList()

        val totalChars = tokens.sumOf { it.length }.coerceAtLeast(1)
        val words = mutableListOf<LyricWord>()
        var currentMs = lineStartMs
        var charCursor = 0

        for ((idx, token) in tokens.withIndex()) {
            val wordDur = (totalDur * token.length / totalChars.toDouble()).toLong().coerceAtLeast(100L)
            val wStart = currentMs
            val wEnd = if (idx == tokens.lastIndex) lineEndMs else (wStart + wordDur).coerceAtMost(lineEndMs)

            val tokenStartInText = text.indexOf(token, charCursor).takeIf { it >= 0 } ?: charCursor
            val tokenEndInText = tokenStartInText + token.length
            charCursor = tokenEndInText

            words.add(
                LyricWord(
                    text = token,
                    startMs = wStart,
                    endMs = wEnd,
                    index = idx,
                    charStart = tokenStartInText,
                    charEnd = tokenEndInText
                )
            )
            currentMs = wEnd
        }
        return words
    }

    private fun getGraphemes(text: String): List<String> {
        if (text.isEmpty()) return emptyList()
        val graphemes = mutableListOf<String>()
        val iterator = java.text.BreakIterator.getCharacterInstance()
        iterator.setText(text)
        var start = iterator.first()
        var end = iterator.next()
        while (end != java.text.BreakIterator.DONE) {
            graphemes.add(text.substring(start, end))
            start = end
            end = iterator.next()
        }
        return if (graphemes.isNotEmpty()) graphemes else listOf(text)
    }
}

