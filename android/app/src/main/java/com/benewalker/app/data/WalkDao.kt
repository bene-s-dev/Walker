package com.benewalker.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface WalkDao {
    @Query("SELECT * FROM walk_records ORDER BY date DESC")
    fun getAllRecordsFlow(): Flow<List<WalkRecord>>

    @Query("SELECT * FROM walk_records ORDER BY date DESC")
    suspend fun getAllRecords(): List<WalkRecord>

    @Query("SELECT * FROM walk_records WHERE date = :date LIMIT 1")
    suspend fun getRecordByDate(date: String): WalkRecord?

    @Query("SELECT * FROM walk_records WHERE date = :date LIMIT 1")
    fun getRecordByDateFlow(date: String): Flow<WalkRecord?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(record: WalkRecord)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<WalkRecord>)

    @Query("DELETE FROM walk_records WHERE date = :date")
    suspend fun deleteByDate(date: String)

    @Query("DELETE FROM walk_records")
    suspend fun deleteAll()
}
