package com.example.carscanner.domain

object Notes {
    const val MAX_LENGTH = 500

    fun clean(raw: String): String = raw.trim().take(MAX_LENGTH)
}
