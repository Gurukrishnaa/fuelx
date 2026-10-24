package com.fuelx.model

data class LyricWord(
    val text: String,
    val startTimeMs: Long,
    val endTimeMs: Long
)

data class LyricLine(
    val timeMs: Long,
    val text: String,
    val words: List<LyricWord> = emptyList()
)
