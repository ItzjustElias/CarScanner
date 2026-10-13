package com.example.carscanner.domain

import kotlin.math.sqrt

enum class Rarity(val label: String, val basePoints: Int) {
    COMMON("Common", 10),
    UNCOMMON("Uncommon", 20),
    RARE("Rare", 40),
    EPIC("Epic", 70),
    LEGENDARY("Legendary", 120),
    MYTHICAL("Mythical", 200),
    DIVINE("Divine", 350);

    companion object {
        fun parse(value: String?): Rarity =
            entries.firstOrNull { it.name.equals(value?.trim(), ignoreCase = true) } ?: COMMON
    }
}

object RarityRules {
    fun floorFor(valueUsd: Long?, production: Long?): Rarity {
        val v = valueUsd ?: 0L
        val p = production ?: 0L
        return when {
            v >= 5_000_000 || (p in 1L..50L && v >= 500_000) -> Rarity.DIVINE
            v >= 1_000_000 || (p in 1L..500L && v >= 150_000) -> Rarity.MYTHICAL
            v >= 250_000 || (p in 1L..3_000L && v >= 100_000) -> Rarity.LEGENDARY
            else -> Rarity.COMMON
        }
    }

    fun resolve(ai: Rarity, valueUsd: Long?, production: Long?): Rarity =
        maxOf(ai, floorFor(valueUsd, production))
}

enum class SortMode(val label: String) {
    NEWEST("Newest first"),
    TOP_POINTS("Most points"),
}

data class CarScan(
    val make: String,
    val model: String,
    val yearText: String,
    val bodyStyle: String,
    val rarity: Rarity,
    val productionCount: Long?,
    val valueUsd: Long?,
    val funFact: String,
    val confidence: Float,
)

sealed interface AnalysisOutcome {
    data class Car(val scan: CarScan) : AnalysisOutcome
    data object NotACar : AnalysisOutcome
}

class ScanException(message: String) : Exception(message)

data class LevelInfo(val level: Int, val progress: Float, val pointsToNext: Int)

object Levels {
    fun forPoints(points: Int): LevelInfo {
        val level = sqrt(points / 50.0).toInt() + 1
        val start = 50 * (level - 1) * (level - 1)
        val next = 50 * level * level
        return LevelInfo(level, (points - start).toFloat() / (next - start), next - points)
    }
}