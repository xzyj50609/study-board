package com.zyj.ritual.domain.model

import java.time.LocalDate

/**
 * 某一天的计划内容。
 *
 * @param date 自然日
 * @param planDayIndex 从 0 开始的计划日序号；为 null 表示该日不是计划日（计划开始前 / 休息日）
 * @param articleIndex 篇号（1-based）；为 null 表示非计划日
 * @param phase 相位（1-based，即该篇的第几天）；为 null 表示非计划日
 * @param taskIndices 当日计划的任务编号列表（1-based，如 [1,2,3]）；空列表表示非计划日
 */
data class DayPlan(
    val date: LocalDate,
    val planDayIndex: Int?,
    val articleIndex: Int?,
    val phase: Int?,
    val taskIndices: List<Int>,
) {
    val isPlanDay: Boolean get() = planDayIndex != null
}
