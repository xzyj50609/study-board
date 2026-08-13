package com.zyj.ritual.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.zyj.ritual.data.local.entity.VocabRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VocabRecordDao {
    @Query("SELECT * FROM vocab_record ORDER BY date ASC, id ASC")
    fun observeAll(): Flow<List<VocabRecordEntity>>

    @Query("SELECT * FROM vocab_record ORDER BY date ASC, id ASC")
    suspend fun getAll(): List<VocabRecordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: VocabRecordEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<VocabRecordEntity>)

    @Query("DELETE FROM vocab_record")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM vocab_record")
    suspend fun count(): Int
}
