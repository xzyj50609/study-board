package com.zyj.ritual.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.zyj.ritual.domain.model.HistoryEvent
import com.zyj.ritual.domain.model.HistoryEventType
import java.time.Instant

/**
 * Room 实体：历史事件。
 *
 * 每一次勾上、撤销、补打卡、重排各产生一条。
 * 撤销的记录会被删除，但撤销事件要留在历史里（7.3 节）。
 */
@Entity(tableName = "history_event")
data class HistoryEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val at: String,        // ISO-8601 Instant
    val type: String,      // HistoryEventType.name
    val articleIndex: Int? = null,
    val taskIndex: Int? = null,
    val taskName: String? = null,
    val note: String? = null,
) {
    fun toDomain(): HistoryEvent = HistoryEvent(
        id = id,
        at = Instant.parse(at),
        type = HistoryEventType.valueOf(type),
        articleIndex = articleIndex,
        taskIndex = taskIndex,
        taskName = taskName,
        note = note,
    )

    companion object {
        fun fromDomain(event: HistoryEvent): HistoryEventEntity = HistoryEventEntity(
            id = event.id,
            at = event.at.toString(),
            type = event.type.name,
            articleIndex = event.articleIndex,
            taskIndex = event.taskIndex,
            taskName = event.taskName,
            note = event.note,
        )
    }
}
