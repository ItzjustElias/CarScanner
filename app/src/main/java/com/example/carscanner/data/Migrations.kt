package com.example.carscanner.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE cars ADD COLUMN photoHash TEXT")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_cars_userId_photoHash ON cars (userId, photoHash)")
    }
}