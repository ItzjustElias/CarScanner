package com.example.carscanner.ui.components

import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToLong

private val SUFFIXES = listOf("", "k", "M", "B", "T")

fun formatCompact(value: Long, compactFrom: Long = 1_000L): String {
    if (value < compactFrom) return NumberFormat.getIntegerInstance(Locale.US).format(value)

    var scaled = value.toDouble()
    var index = 0
    while (scaled >= 1_000 && index < SUFFIXES.lastIndex) {
        scaled /= 1_000
        index++
    }
    var rounded = (scaled * 10).roundToLong() / 10.0      // one decimal
    if (rounded >= 1_000 && index < SUFFIXES.lastIndex) { // 999,999 -> "1M", not "1000k"
        rounded /= 1_000
        index++
    }
    val number = if (rounded % 1.0 == 0.0) rounded.toLong().toString() else rounded.toString()
    return number + SUFFIXES[index]
}

fun formatCompactUsd(value: Long): String = "\$" + formatCompact(value)
