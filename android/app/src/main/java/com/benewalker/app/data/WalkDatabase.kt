package com.benewalker.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [WalkRecord::class], version = 1, exportSchema = false)
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
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
