package com.example.carscanner.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// migration 1
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE cars ADD COLUMN photoHash TEXT")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_cars_userId_photoHash ON cars (userId, photoHash)")
    }
}

// migration 2
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE cars ADD COLUMN note TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE cars ADD COLUMN spottedAt INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE cars SET spottedAt = scannedAt")
    }
}

// migration 3
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS car_tags (" +
                "carId INTEGER NOT NULL, tag TEXT NOT NULL, " +
                "PRIMARY KEY(carId, tag), " +
                "FOREIGN KEY(carId) REFERENCES cars(id) ON UPDATE NO ACTION ON DELETE CASCADE)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_car_tags_carId ON car_tags (carId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_car_tags_tag ON car_tags (tag)")
    }
}
