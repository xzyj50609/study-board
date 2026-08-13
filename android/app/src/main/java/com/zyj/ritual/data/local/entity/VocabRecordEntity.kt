package com.zyj.ritual.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.zyj.ritual.domain.vocab.VocabRecord

@Entity(tableName = "vocab_record")
data class VocabRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val date: String,      // "YYYY-MM-DD" (UTC+8)
    val words: Int,
    val kind: String,      // "new" | "backlog"
    /** 写入时刻（epoch 毫秒）。0 = 迁移前/备份导入的老记录，只有日期没有时刻 */
    val createdAt: Long = 0,
) {
    fun toDomain(): VocabRecord = VocabRecord(
        date = date,
        words = words,
        kind = kind,
        createdAt = createdAt,
    )

    companion object {
        fun fromDomain(r: VocabRecord): VocabRecordEntity = VocabRecordEntity(
            date = r.date,
            words = r.words,
            kind = r.kind,
            createdAt = r.createdAt,
        )
    }
}
