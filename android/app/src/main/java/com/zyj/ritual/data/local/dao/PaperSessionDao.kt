package com.zyj.ritual.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.zyj.ritual.data.local.entity.PaperSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PaperSessionDao {

    @Query("SELECT * FROM paper_session ORDER BY completedDate ASC, id ASC")
    fun observeAll(): Flow<List<PaperSessionEntity>>

    @Query("SELECT * FROM paper_session ORDER BY completedDate ASC, id ASC")
    suspend fun getAll(): List<PaperSessionEntity>

    @Insert
    suspend fun insert(entity: PaperSessionEntity): Long

    @Query("DELETE FROM paper_session WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM paper_session")
    suspend fun deleteAll()

    @Insert
    suspend fun insertAll(entities: List<PaperSessionEntity>)
}
