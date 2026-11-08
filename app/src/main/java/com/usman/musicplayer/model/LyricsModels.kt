package com.usman.musicplayer.model

import android.util.Log

data class LyricLine(
    val id: Int,
    val timestampMs: Long,
    val text: String,
    val isTimestamped: Boolean
)

data class ParsedLyrics(
    val lines: List<LyricLine>,
    val isSynchronized: Boolean
)

object LyricsParser {

    private val TIMESTAMP_REGEX = Regex("""\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?\]""")
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
                var lineText = if (textStartIndex < line.length) line.substring(textStartIndex).trim() else ""
                lineText = HTML_TAG_REGEX.replace(lineText, "").trim()

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
                            text = lineText.ifBlank { "♪" },
                            isTimestamped = true
                        )
                    )
                }
            }
        }

        if (timestampedLines.isNotEmpty()) {
            val sorted = timestampedLines.sortedBy { it.timestampMs }.mapIndexed { idx, item ->
                item.copy(id = idx)
            }
            Log.d("USMAN_LYRICS", "[LyricsParser] Successfully parsed ${sorted.size} synchronized LRC lyric lines")
            return ParsedLyrics(lines = sorted, isSynchronized = true)
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
        var activeIdx = -1
        for ((index, line) in lines.withIndex()) {
            if (line.isTimestamped && line.timestampMs <= currentPositionMs) {
                activeIdx = index
            } else if (line.isTimestamped && line.timestampMs > currentPositionMs) {
                break
            }
        }
        return if (activeIdx == -1 && lines.any { it.isTimestamped }) 0 else activeIdx
    }
}
