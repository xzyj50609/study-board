package com.zyj.ritual.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.zyj.ritual.data.local.entity.HistoryEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryEventDao {

    /** 观察全部历史事件，倒序（最新在前） */
    @Query("SELECT * FROM history_event ORDER BY id DESC")
    fun observeAll(): Flow<List<HistoryEventEntity>>

    /** 同步获取全部 */
    @Query("SELECT * FROM history_event ORDER BY id DESC")
    suspend fun getAll(): List<HistoryEventEntity>

    @Insert
    suspend fun insert(event: HistoryEventEntity): Long

    @Insert
    suspend fun insertAll(events: List<HistoryEventEntity>)

    @Query("DELETE FROM history_event")
    suspend fun deleteAll()
}
