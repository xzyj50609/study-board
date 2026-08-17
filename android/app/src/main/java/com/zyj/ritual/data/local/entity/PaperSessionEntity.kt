package com.zyj.ritual.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.zyj.ritual.domain.model.PaperSession
import java.time.Instant
import java.time.LocalDate

@Entity(tableName = "paper_session")
data class PaperSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** ISO LocalDate，如 2026-08-13 */
    val completedDate: String,
    val partsCount: Int,
    val digestionDays: Int,
    /** epoch milli；UTC+8 由 BeijingClock 保证 */
    val createdAt: Long,
) {
    fun toDomain(): PaperSession = PaperSession(
        id = id,
        name = name,
        completedDate = LocalDate.parse(completedDate),
        partsCount = partsCount,
        digestionDays = digestionDays,
        createdAt = Instant.ofEpochMilli(createdAt),
    )

    companion object {
        fun fromDomain(s: PaperSession): PaperSessionEntity = PaperSessionEntity(
            id = s.id,
            name = s.name,
            completedDate = s.completedDate.toString(),
            partsCount = s.partsCount,
            digestionDays = s.digestionDays,
            createdAt = s.createdAt.toEpochMilli(),
        )
    }
}
