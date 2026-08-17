package com.zyj.ritual.domain.model

import java.time.Instant
import java.time.LocalDate

/**
 * 一整套英语卷子的完成记录（如「2016 年卷」）。
 *
 * 一套卷 = 4 篇阅读 + 完形 + 新题型 + 翻译，共 7 个卷面部分（PARTS_COUNT）。
 * 完成一套卷后进入消化期：完成日当天剩余阅读任务豁免，
 * 之后 DIGESTION_DAYS 个完整自然日为消化日，消化期内不排新计划、不产生欠账。
 *
 * ⚠️ 整卷不计入 42 篇完成数——它完全不产生 TaskRecord，
 * 「做过卷子」和「完成六步精读」是两种统计，不能混。
 */
data class PaperSession(
    val id: Long = 0,
    val name: String,
    /** 完成这套卷的自然日 */
    val completedDate: LocalDate,
    /** 卷面部分数，固定 7（4 阅读 + 完形 + 新题型 + 翻译） */
    val partsCount: Int = PARTS_COUNT,
    /** 完整消化日天数（不含完成日当天；当天走"剩余任务豁免"） */
    val digestionDays: Int = DIGESTION_DAYS,
    val createdAt: Instant,
) {
    companion object {
        const val PARTS_COUNT = 7
        const val DIGESTION_DAYS = 4
    }
}
