package com.zyj.ritual.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.zyj.ritual.domain.model.RecordSource
import com.zyj.ritual.domain.model.TaskRecord
import java.time.Instant
import java.time.LocalDate

/**
 * Room 实体：任务完成记录。
 *
 * 和 domain 层 TaskRecord 的区别：
 * - completedAt / plannedDate 用 String 存储（ISO-8601），因为 Room 原生不支持 java.time
 * - source 用 String 存储
 * - 其他字段一致
 */
@Entity(tableName = "task_record")
data class TaskRecordEntity(
    @PrimaryKey val id: String,
    val articleIndex: Int,
    val taskIndex: Int,
    /** ISO-8601 字符串；IMPORTED 记录为 null */
    val completedAt: String?,
    /** ISO-8601 本地日期字符串；可能为 null */
    val plannedDate: String?,
    val source: String, // RecordSource.name
) {
    fun toDomain(): TaskRecord = TaskRecord(
        id = id,
        articleIndex = articleIndex,
        taskIndex = taskIndex,
        completedAt = completedAt?.let { Instant.parse(it) },
        plannedDate = plannedDate?.let { LocalDate.parse(it) },
        source = RecordSource.valueOf(source),
    )

    companion object {
        fun fromDomain(record: TaskRecord): TaskRecordEntity = TaskRecordEntity(
            id = record.id,
            articleIndex = record.articleIndex,
            taskIndex = record.taskIndex,
            completedAt = record.completedAt?.toString(),
            plannedDate = record.plannedDate?.toString(),
            source = record.source.name,
        )
    }
}
