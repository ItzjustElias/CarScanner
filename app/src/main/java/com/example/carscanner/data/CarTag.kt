package com.example.carscanner.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

// deleting the car deletes the tags too...
@Entity(
    tableName = "car_tags",
    primaryKeys = ["carId", "tag"],
    foreignKeys = [
        ForeignKey(
            entity = CarEntity::class,
            parentColumns = ["id"],
            childColumns = ["carId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("carId"), Index("tag")],
)
data class CarTag(
    val carId: Long,
    val tag: String,
)
