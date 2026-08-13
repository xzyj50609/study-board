package com.zyj.ritual.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.zyj.ritual.data.local.entity.TaskRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskRecordDao {

    /** 观察所有记录（按篇号+任务号排序） */
    @Query("SELECT * FROM task_record ORDER BY articleIndex ASC, taskIndex ASC")
    fun observeAll(): Flow<List<TaskRecordEntity>>

    /** 同步获取所有记录 */
    @Query("SELECT * FROM task_record ORDER BY articleIndex ASC, taskIndex ASC")
    suspend fun getAll(): List<TaskRecordEntity>

    /** 插入或替换（幂等） */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: TaskRecordEntity)

    /** 批量插入或替换 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<TaskRecordEntity>)

    /** 删除一条记录 */
    @Query("DELETE FROM task_record WHERE id = :id")
    suspend fun deleteById(id: String): Int

    /** 删除某篇的全部记录（撤销整篇） */
    @Query("DELETE FROM task_record WHERE articleIndex = :articleIndex")
    suspend fun deleteByArticle(articleIndex: Int): Int

    /**
     * 删除起点之外的 IMPORTED 行（重新设置起点时用）。
     * 只删 IMPORTED，真实完成记录不碰。
     */
    @Query("DELETE FROM task_record WHERE source = 'IMPORTED' AND articleIndex > :maxArticle")
    suspend fun deleteImportedAbove(maxArticle: Int): Int

    /** 清空全部（备份恢复用） */
    @Query("DELETE FROM task_record")
    suspend fun deleteAll()

    /** 统计总记录数 */
    @Query("SELECT COUNT(*) FROM task_record")
    suspend fun count(): Int
}
