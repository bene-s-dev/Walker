package com.benewalker.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        try {
            db.execSQL("ALTER TABLE walk_records ADD COLUMN morningDistanceMeters REAL NOT NULL DEFAULT 0.0")
        } catch (_: Exception) {}
        try {
            db.execSQL("ALTER TABLE walk_records ADD COLUMN eveningDistanceMeters REAL NOT NULL DEFAULT 0.0")
        } catch (_: Exception) {}
        try {
            db.execSQL("ALTER TABLE walk_records ADD COLUMN morningRouteJson TEXT")
        } catch (_: Exception) {}
        try {
            db.execSQL("ALTER TABLE walk_records ADD COLUMN eveningRouteJson TEXT")
        } catch (_: Exception) {}
    }
}

@Database(entities = [WalkRecord::class], version = 2, exportSchema = false)
abstract class WalkDatabase : RoomDatabase() {
    abstract fun walkDao(): WalkDao

    companion object {
        @Volatile
        private var INSTANCE: WalkDatabase? = null

        fun getInstance(context: Context): WalkDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    WalkDatabase::class.java,
                    "benewalker.db"
                )
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
