package com.example.carscanner.domain

object SearchQuery {

    fun escapeLike(input: String): String =
        input.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
}
