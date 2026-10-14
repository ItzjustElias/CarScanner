package com.example.carscanner.domain

object SearchQuery {
    // makes it mysql LIKE safe
    fun escapeLike(input: String): String =
        input.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
}
