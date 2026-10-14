package com.example.carscanner.domain

import java.util.Locale

object Tags {
    const val MAX_LENGTH = 24
    const val MAX_PER_CAR = 10

    fun normalize(raw: String): String? {
        val cleaned = raw.trim()
            .trimStart('#')
            .replace(Regex("\\s+"), " ")
            .lowercase(Locale.ROOT)
            .take(MAX_LENGTH)
            .trim()
        return cleaned.ifBlank { null }
    }

    fun label(tag: String): String = tag.replaceFirstChar { it.uppercase() }
}
