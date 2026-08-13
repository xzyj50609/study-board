package com.zyj.ritual.domain.calendar

import com.zyj.ritual.domain.model.DayPlan
import com.zyj.ritual.domain.model.Plan
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * 计划日历：日期 ↔ 计划日 ↔ 篇号+相位+任务 之间的映射。
 *
 * R1: 第 k 个计划日（k 从 0 开始）→ 篇号 = 起始篇 + floor(k / 每篇天数)，相位 = k mod 每篇天数 + 1
 * R2: 相位 1 → 任务 1,2,3；相位 2 → 任务 4,5,6（每篇 2 天默认）
 * R3: 第一版无休息日，计划日 = 自然日（studyWeekdays 保留但默认全选七天）
 * R4: 每篇 1 天时 6 项全排一天；每篇 3 天时按 2/2/2 均分
 * R5: 计划只算到第 totalArticles 篇的最后一项，之后不再排
 *
 * 为什么做成接口 + 实现：studyWeekdays 目前永远是全选七天，但字段留着。
 * 将来加休息日时只改实现，上层调用不动。
 */
interface PlanCalendar {

    /**
     * 第 k 个计划日（k >= 0）对应的计划内容。
     * 超出最后一篇则返回 null（R5）。
     */
    fun getPlanDay(planDayIndex: Int): DayPlan?

    /**
     * 某个自然日的计划内容。
     * 不是计划日（计划开始前 / 休息日 / 已结束）返回 isPlanDay=false 的 DayPlan。
     */
    fun getDayPlan(date: LocalDate): DayPlan

    /**
     * 从自然日到计划日序号的映射（从 0 开始）。
     * 不是计划日返回 null。
     */
    fun dateToPlanDayIndex(date: LocalDate): Int?

    /**
     * 第 k 个计划日对应的自然日。
     */
    fun planDayIndexToDate(planDayIndex: Int): LocalDate?

    /**
     * 从计划开始日到今天（不含今天）之间有多少个已过去的计划日。
     * 用于额度计算的"应完成项数"（R11）。
     *
     * 注意：R11 明确"不含今天"——今天的任务当天不算欠。
     */
    fun elapsedPlanDays(today: LocalDate): Int

    /**
     * 给定某篇 + 某相位，返回对应的计划日序号（从 0 开始）。
     */
    fun articlePhaseToPlanDayIndex(articleIndex: Int, phase: Int): Int

    /**
     * 给定某一项任务（篇号 + 任务号），它属于哪个计划日。
     * 任务号按相位分组：相位 1 = 前 tasksPerDay 项，相位 2 = 后 tasksPerDay 项……
     */
    fun taskToPlanDayIndex(articleIndex: Int, taskIndex: Int): Int

    companion object {
        fun create(plan: Plan): PlanCalendar = PlanCalendarImpl(plan)
    }
}

private class PlanCalendarImpl(private val plan: Plan) : PlanCalendar {

    override fun getPlanDay(planDayIndex: Int): DayPlan? {
        if (planDayIndex < 0) return null

        val articleOffset = planDayIndex / plan.daysPerArticle
        val phase = planDayIndex % plan.daysPerArticle + 1
        val articleIndex = plan.startArticle + articleOffset

        if (articleIndex > plan.totalArticles) return null  // R5

        val date = planDayIndexToDate(planDayIndex) ?: return null
        val taskIndices = taskIndicesForPhase(phase)

        return DayPlan(
            date = date,
            planDayIndex = planDayIndex,
            articleIndex = articleIndex,
            phase = phase,
            taskIndices = taskIndices,
        )
    }

    override fun getDayPlan(date: LocalDate): DayPlan {
        val planDayIndex = dateToPlanDayIndex(date)
        if (planDayIndex == null) {
            return DayPlan(date = date, planDayIndex = null, articleIndex = null, phase = null, taskIndices = emptyList())
        }
        return getPlanDay(planDayIndex)
            ?: DayPlan(date = date, planDayIndex = null, articleIndex = null, phase = null, taskIndices = emptyList())
    }

    override fun dateToPlanDayIndex(date: LocalDate): Int? {
        if (date < plan.planStartDate) return null

        // 第一版：studyWeekdays 永远是全选七天，计划日 = 自然日
        // 但为了以后加休息日，这里统一按 studyWeekdays 数
        val days = countStudyDaysBetween(plan.planStartDate, date)
        val index = days - 1  // 开始日那天是第 0 个计划日

        if (index < 0) return null

        // 验证：planDayIndex 对应的 article 是否还在范围内
        val articleOffset = index / plan.daysPerArticle
        if (plan.startArticle + articleOffset > plan.totalArticles) return null

        return index
    }

    override fun planDayIndexToDate(planDayIndex: Int): LocalDate? {
        if (planDayIndex < 0) return null

        // 第一版：自然日一一对应
        // 有休息日后这里要从 planStartDate 往后数 planDayIndex 个学习日
        var date = plan.planStartDate
        var count = 0
        while (count < planDayIndex) {
            date = date.plusDays(1)
            if (date.dayOfWeek in plan.studyWeekdays) {
                count++
            }
            // 防无限循环（理论上不会）
            if (count > 10_000) return null
        }
        return date
    }

    override fun elapsedPlanDays(today: LocalDate): Int {
        if (today <= plan.planStartDate) return 0
        // R11: 不含今天
        val yesterday = today.minusDays(1)
        return countStudyDaysBetween(plan.planStartDate, yesterday)
    }

    override fun articlePhaseToPlanDayIndex(articleIndex: Int, phase: Int): Int {
        require(phase in 1..plan.daysPerArticle) {
            "phase must be 1..${plan.daysPerArticle}, got $phase"
        }
        val articleOffset = articleIndex - plan.startArticle
        return articleOffset * plan.daysPerArticle + (phase - 1)
    }

    override fun taskToPlanDayIndex(articleIndex: Int, taskIndex: Int): Int {
        require(taskIndex in 1..plan.tasksPerArticle) {
            "taskIndex must be 1..${plan.tasksPerArticle}, got $taskIndex"
        }
        val phase = (taskIndex - 1) / plan.tasksPerDay + 1
        return articlePhaseToPlanDayIndex(articleIndex, phase)
    }

    // ———————————— 内部 ————————————

    /**
     * 计算 [start, end] 闭区间内的学习日数量。
     */
    private fun countStudyDaysBetween(start: LocalDate, end: LocalDate): Int {
        if (end < start) return 0

        var count = 0
        var date = start
        while (!date.isAfter(end)) {
            if (date.dayOfWeek in plan.studyWeekdays) {
                count++
            }
            date = date.plusDays(1)
        }
        return count
    }

    /**
     * 某相位对应的任务编号列表。
     * 每篇 2 天：phase 1 = [1,2,3]，phase 2 = [4,5,6]
     * 每篇 1 天：phase 1 = [1..6]
     * 每篇 3 天：phase 1 = [1,2], phase 2 = [3,4], phase 3 = [5,6]
     */
    private fun taskIndicesForPhase(phase: Int): List<Int> {
        val perDay = plan.tasksPerDay
        val startTask = (phase - 1) * perDay + 1
        val endTask = (phase * perDay).coerceAtMost(plan.tasksPerArticle)
        return (startTask..endTask).toList()
    }
}
