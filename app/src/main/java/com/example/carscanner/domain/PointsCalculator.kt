package com.example.carscanner.domain

import java.util.Calendar
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.roundToInt

object PointsCalculator {
    private val yearRegex = Regex("(19|20)\\d{2}")

    fun calculate(scan: CarScan, duplicate: Boolean): Int {
        var points = scan.rarity.basePoints.toDouble()

        scan.valueUsd?.takeIf { it > 0 }?.let { points += log10(it.toDouble()) * 8 }

        // Low production run bonus
        scan.productionCount?.takeIf { it > 0 }?.let {
            points += when {
                it < 50 -> 150
                it < 500 -> 80
                it < 5_000 -> 40
                it < 50_000 -> 15
                else -> 0
            }
        }

        yearRegex.find(scan.yearText)?.value?.toIntOrNull()?.let { year ->
            val age = Calendar.getInstance().get(Calendar.YEAR) - year
            points += when {
                age >= 50 -> 40
                age >= 30 -> 15
                else -> 0
            }
        }

        points *= 0.6 + 0.4 * scan.confidence.coerceIn(0f, 1f)

        if (duplicate) points *= 0.1

        return max(1, points.roundToInt())
    }
}