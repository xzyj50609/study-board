package com.zyj.ritual.domain.model

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * 计划参数。
 *
 * 重排时只改 planStartDate 和 startArticle，
 * 已完成记录（含时间戳和计划日期快照）一律不动（R39）。
 */
data class Plan(
    val totalArticles: Int = 42,
    val tasksPerArticle: Int = 6,
    val startArticle: Int = 1,         // 下一篇要开始的篇号 = 已学完 + 1
    val completedBeforeStart: Int = 0, // 起点已学完的篇数
    val planStartDate: LocalDate,      // 计划开始日
    val daysPerArticle: Int = 2,       // 每篇几天（1 / 2 / 3）
    /**
     * 学习日。第一版固定全选七天（不做休息日），
     * 但字段保留在 Plan 里，将来要加休息日从这里进，不动上层。
     */
    val studyWeekdays: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
    val timezone: String = "Asia/Shanghai",
) {
    init {
        require(totalArticles > 0) { "totalArticles must be > 0" }
        require(startArticle in 1..totalArticles + 1) {
            "startArticle must be in 1..totalArticles+1, got $startArticle"
        }
        require(completedBeforeStart in 0 until totalArticles + 1) {
            "completedBeforeStart must be in 0..totalArticles, got $completedBeforeStart"
        }
        require(daysPerArticle in 1..3) { "daysPerArticle must be 1, 2, or 3" }
        require(studyWeekdays.isNotEmpty()) { "studyWeekdays must not be empty" }
    }

    val totalTasks: Int = totalArticles * tasksPerArticle

    /** 起点已完成的任务数（IMPORTED 数量） */
    val importedTaskCount: Int = completedBeforeStart * tasksPerArticle

    /**
     * 每日任务数。每篇 1 天 = 6 项/天；2 天 = 3 项/天；3 天 = 2 项/天（均分）。
     * R4 明确：每篇 3 天时按 2/2/2 拆分。
     */
    val tasksPerDay: Int = when (daysPerArticle) {
        1 -> 6
        2 -> 3
        3 -> 2
        else -> error("daysPerArticle=$daysPerArticle not supported")
    }

    /** 基础计划总天数 */
    val totalPlanDays: Int = (totalArticles - completedBeforeStart) * daysPerArticle

    /** 基础计划完成日（R18） */
    val baseCompletionDate: LocalDate
        get() {
            // 从 planStartDate 往后数 totalPlanDays - 1 天（第一天就是开始日那天）
            return planStartDate.plusDays((totalPlanDays - 1).toLong())
        }
}
