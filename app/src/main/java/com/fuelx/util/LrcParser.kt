package com.fuelx.util

import com.fuelx.model.LyricLine
import com.fuelx.model.LyricWord

data class ParsedLyrics(
    val isSynchronized: Boolean,
    val isInstrumental: Boolean,
    val lines: List<LyricLine>,
    val plainText: String? = null
)

object LrcParser {

    private val lineTimestampRegex = Regex("\\[(\\d{1,2}):(\\d{2})(?:\\.(\\d{1,3}))?\\]")
    private val wordTimestampRegex = Regex("<(\\d{1,2}):(\\d{2})(?:\\.(\\d{1,3}))?>")
    private val tagRegex = Regex("^\\[(ti|ar|al|by|offset|length|re|ve):.*\\]", RegexOption.IGNORE_CASE)

    fun parse(lrcContent: String?): ParsedLyrics {
        if (lrcContent.isNullOrBlank()) {
            return ParsedLyrics(
                isSynchronized = false,
                isInstrumental = false,
                lines = emptyList(),
                plainText = null
            )
        }

        val rawLines = lrcContent.lines()
        val intermediateLines = mutableListOf<RawParsedLine>()
        var hasAnyTimestamp = false

        for (rawLine in rawLines) {
            val line = rawLine.trim()
            if (line.isEmpty() || tagRegex.matches(line)) {
                continue
            }

            // Find line-level timestamps [mm:ss.xx]
            val lineMatches = lineTimestampRegex.findAll(line).toList()

            if (lineMatches.isNotEmpty()) {
                hasAnyTimestamp = true
                val contentAfterTimestamps = line.substring(lineMatches.last().range.last + 1).trim()

                for (match in lineMatches) {
                    val timeMs = parseTimestampToMs(
                        match.groupValues[1],
                        match.groupValues[2],
                        match.groupValues.getOrNull(3)
                    )
                    intermediateLines.add(RawParsedLine(timeMs, contentAfterTimestamps))
                }
            }
        }

        if (!hasAnyTimestamp) {
            return ParsedLyrics(
                isSynchronized = false,
                isInstrumental = false,
                lines = emptyList(),
                plainText = lrcContent.trim()
            )
        }

        val sortedIntermediate = intermediateLines.sortedBy { it.timeMs }

        // Now process each line to build LyricWord items for word-by-word karaoke
        val finalLines = mutableListOf<LyricLine>()

        for (i in sortedIntermediate.indices) {
            val current = sortedIntermediate[i]
            val nextTimeMs = if (i + 1 < sortedIntermediate.size) {
                sortedIntermediate[i + 1].timeMs
            } else {
                current.timeMs + 4500L // Default duration for last line
            }

            val lineDuration = (nextTimeMs - current.timeMs).coerceIn(1000L, 10000L)
            val parsedWords = extractOrSynthesizeWords(current.text, current.timeMs, lineDuration)
            val cleanText = parsedWords.joinToString(" ") { it.text }

            finalLines.add(
                LyricLine(
                    timeMs = current.timeMs,
                    text = cleanText,
                    words = parsedWords
                )
            )
        }

        val isInstrumental = finalLines.all { line ->
            line.text.contains("instrumental", ignoreCase = true) || line.text.isBlank()
        }

        return ParsedLyrics(
            isSynchronized = true,
            isInstrumental = isInstrumental,
            lines = finalLines,
            plainText = null
        )
    }

    private fun extractOrSynthesizeWords(
        rawContent: String,
        lineStartTimeMs: Long,
        lineDurationMs: Long
    ): List<LyricWord> {
        val wordMatches = wordTimestampRegex.findAll(rawContent).toList()

        if (wordMatches.isNotEmpty()) {
            // Enhanced LRC format with embedded <mm:ss.xx> timestamps
            val words = mutableListOf<LyricWord>()
            for (idx in wordMatches.indices) {
                val match = wordMatches[idx]
                val wordStartTime = parseTimestampToMs(
                    match.groupValues[1],
                    match.groupValues[2],
                    match.groupValues.getOrNull(3)
                )

                val wordTextStart = match.range.last + 1
                val wordTextEnd = if (idx + 1 < wordMatches.size) {
                    wordMatches[idx + 1].range.first
                } else {
                    rawContent.length
                }

                val wordText = rawContent.substring(wordTextStart, wordTextEnd).trim()
                val wordEndTime = if (idx + 1 < wordMatches.size) {
                    val nextMatch = wordMatches[idx + 1]
                    parseTimestampToMs(
                        nextMatch.groupValues[1],
                        nextMatch.groupValues[2],
                        nextMatch.groupValues.getOrNull(3)
                    )
                } else {
                    lineStartTimeMs + lineDurationMs
                }

                if (wordText.isNotEmpty()) {
                    words.add(LyricWord(wordText, wordStartTime, wordEndTime))
                }
            }
            if (words.isNotEmpty()) return words
        }

        // Standard LRC: Synthesize natural word-by-word timing proportional to word length
        val rawTokens = rawContent.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (rawTokens.isEmpty()) return emptyList()

        val totalChars = rawTokens.sumOf { it.length }.coerceAtLeast(1)
        var currentMs = lineStartTimeMs
        val words = mutableListOf<LyricWord>()

        for (token in rawTokens) {
            val weight = token.length.toFloat() / totalChars.toFloat()
            val wordDuration = (lineDurationMs * weight).toLong().coerceAtLeast(200L)
            val wordEnd = currentMs + wordDuration

            words.add(
                LyricWord(
                    text = token,
                    startTimeMs = currentMs,
                    endTimeMs = wordEnd
                )
            )
            currentMs = wordEnd
        }

        return words
    }

    private fun parseTimestampToMs(
        minutesStr: String,
        secondsStr: String,
        millisStr: String?
    ): Long {
        val minutes = minutesStr.toLongOrNull() ?: 0L
        val seconds = secondsStr.toLongOrNull() ?: 0L
        val msString = millisStr.orEmpty()

        val millis = when (msString.length) {
            1 -> msString.toLong() * 100
            2 -> msString.toLong() * 10
            3 -> msString.toLong()
            else -> 0L
        }

        return (minutes * 60 * 1000L) + (seconds * 1000L) + millis
    }

    private data class RawParsedLine(val timeMs: Long, val text: String)
}
