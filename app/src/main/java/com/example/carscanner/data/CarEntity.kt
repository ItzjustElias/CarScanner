package com.example.carscanner.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.carscanner.domain.Rarity

@Entity(tableName = "cars", indices = [Index("userId"), Index("userId", "photoHash")])
data class CarEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: String,
    val make: String,
    val model: String,
    val yearText: String,
    val bodyStyle: String,
    val rarity: String,
    val productionCount: Long?,
    val valueUsd: Long?,
    val funFact: String,
    val confidence: Float,
    val points: Int,
    val imagePath: String,
    val scannedAt: Long,
    val photoHash: String? = null,
    val note: String = "",
    val spottedAt: Long = 0L,
) {
    val rarityEnum: Rarity get() = Rarity.parse(rarity)
    val title: String get() = "$make $model".trim()
}
