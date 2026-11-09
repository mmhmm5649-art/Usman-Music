package com.usman.musicplayer.model

import android.util.Log

data class LyricWord(
    val text: String,
    val startMs: Long,
    val endMs: Long
)

data class LyricLine(
    val id: Int,
    val timestampMs: Long,
    val text: String,
    val isTimestamped: Boolean,
    val endTimestampMs: Long = -1L,
    val durationMs: Long = 0L,
    val words: List<LyricWord> = emptyList()
)

data class ParsedLyrics(
    val lines: List<LyricLine>,
    val isSynchronized: Boolean
)

object LyricsParser {

    private val TIMESTAMP_REGEX = Regex("""\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?\]""")
    private val WORD_TIMESTAMP_REGEX = Regex("""<(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?>""")
    private val HTML_TAG_REGEX = Regex("""<[^>]*>""")

    /**
     * Parses synchronized LRC timestamped lyrics or plain lyrics.
     * Never invents artificial timestamps for plain lyrics.
     */
    fun parse(raw: String?, totalDurationMs: Long = 0L): ParsedLyrics {
        if (raw.isNullOrBlank()) {
            Log.d("USMAN_LYRICS", "[LyricsParser] Raw lyrics input is null or blank")
            return ParsedLyrics(emptyList(), isSynchronized = false)
        }

        val cleaned = cleanHtml(raw)
        val rawLines = cleaned.lines().map { it.trim() }.filter { it.isNotEmpty() }

        val timestampedLines = mutableListOf<LyricLine>()
        var globalLineCounter = 0

        for (line in rawLines) {
            val matches = TIMESTAMP_REGEX.findAll(line).toList()
            if (matches.isNotEmpty()) {
                val lastMatch = matches.last()
                val textStartIndex = lastMatch.range.last + 1
                var lineContent = if (textStartIndex < line.length) line.substring(textStartIndex).trim() else ""

                // Check for word-level timestamps <mm:ss.xx>
                val wordMatches = WORD_TIMESTAMP_REGEX.findAll(lineContent).toList()
                val explicitWords = mutableListOf<LyricWord>()
                if (wordMatches.isNotEmpty()) {
                    for (i in wordMatches.indices) {
                        val curr = wordMatches[i]
                        val min = curr.groupValues[1].toLongOrNull() ?: 0L
                        val sec = curr.groupValues[2].toLongOrNull() ?: 0L
                        val msStr = curr.groupValues.getOrNull(3)
                        val ms = when {
                            msStr.isNullOrEmpty() -> 0L
                            msStr.length == 1 -> msStr.toLong() * 100L
                            msStr.length == 2 -> msStr.toLong() * 10L
                            else -> msStr.take(3).toLong()
                        }
                        val startMs = (min * 60 + sec) * 1000L + ms
                        val textStart = curr.range.last + 1
                        val textEnd = if (i + 1 < wordMatches.size) wordMatches[i + 1].range.first else lineContent.length
                        val wordText = if (textStart < textEnd) lineContent.substring(textStart, textEnd).trim() else ""

                        val nextMin = wordMatches.getOrNull(i + 1)?.groupValues?.get(1)?.toLongOrNull() ?: min
                        val nextSec = wordMatches.getOrNull(i + 1)?.groupValues?.get(2)?.toLongOrNull() ?: (sec + 1)
                        val nextMs = (nextMin * 60 + nextSec) * 1000L

                        if (wordText.isNotBlank()) {
                            explicitWords.add(LyricWord(text = wordText, startMs = startMs, endMs = nextMs.coerceAtLeast(startMs + 100L)))
                        }
                    }
                }

                var cleanLineText = HTML_TAG_REGEX.replace(lineContent, "").trim()
                if (cleanLineText.isBlank()) {
                    cleanLineText = "♪"
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
                            words = explicitWords
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

                // If explicit word timestamps were not present in the LRC, generate word bounds for smooth pacing
                val effectiveWords = if (item.words.isNotEmpty()) {
                    item.words
                } else {
                    val rawWords = item.text.split(Regex("""\s+""")).filter { it.isNotBlank() }
                    val totalChars = rawWords.sumOf { it.length }.coerceAtLeast(1)
                    var runningOffset = 0L
                    rawWords.map { w ->
                        val wordDur = ((w.length.toFloat() / totalChars.toFloat()) * dur).toLong().coerceAtLeast(80L)
                        val wStart = item.timestampMs + runningOffset
                        val wEnd = (wStart + wordDur).coerceAtMost(nextTs)
                        runningOffset += wordDur
                        LyricWord(text = w, startMs = wStart, endMs = wEnd)
                    }
                }

                item.copy(
                    id = idx,
                    endTimestampMs = nextTs,
                    durationMs = dur,
                    words = effectiveWords
                )
            }
            Log.d("USMAN_LYRICS", "[LyricsParser] Successfully parsed ${mapped.size} synchronized LRC lyric lines")
            return ParsedLyrics(lines = mapped, isSynchronized = true)
        }

        // Plain text lines (no LRC timestamps detected)
        val plainLines = rawLines.mapIndexed { idx, line ->
            val cleanLine = HTML_TAG_REGEX.replace(line, "").trim()
            LyricLine(
                id = idx,
                timestampMs = -1L,
                text = cleanLine,
                isTimestamped = false
            )
        }.filter { it.text.isNotBlank() }

        Log.d("USMAN_LYRICS", "[LyricsParser] No LRC timestamps detected. Parsed ${plainLines.size} plain text lines (isSynchronized=false)")
        return ParsedLyrics(lines = plainLines, isSynchronized = false)
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
}

