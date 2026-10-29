package com.fuelx.util

/**
 * Sanitizes artist and title strings from YouTube Music metadata.
 * Normalizes awkward spacing around commas, dots, and ampersands
 * (e.g. "Singer A, Singer B  &Singer C" -> "Singer A, Singer B & Singer C").
 */
fun cleanArtistString(raw: String): String {
    if (raw.isBlank()) return ""
    return raw
        .replace(Regex("\\s+"), " ")
        .replace(Regex("\\s*,\\s*"), ", ")
        .replace(Regex("\\s*&\\s*"), " & ")
        .replace(Regex("\\s*•\\s*"), " • ")
        .trim()
        .trimEnd(',', '&', '•', ' ', '-')
        .trimStart(',', '&', '•', ' ', '-')
        .replace(Regex("\\s+"), " ")
}

