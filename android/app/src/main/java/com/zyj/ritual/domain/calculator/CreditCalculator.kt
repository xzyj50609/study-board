package com.zyj.ritual.domain.calculator

import com.zyj.ritual.domain.calendar.PlanCalendar
import com.zyj.ritual.domain.model.*
import java.time.LocalDate

/**
 * 额度与缺额计算（R11-R18, R23）。
 *
 * 核心公式：
 * 额度（项）= 计划开始后新完成的项数 − 应完成项数
 * 应完成项数 = 已过去的计划日数 × 每日项数（R11）
 *
 * R15: 起点的 IMPORTED 记录不参与额度计算，只进整体进度。
 */
object CreditCalculator {

    fun calculate(
        plan: Plan,
        calendar: PlanCalendar,
        records: List<TaskRecord>,
        today: LocalDate,
    ): CreditResult {
        if (today < plan.planStartDate) {
            // 计划还没开始
            val remaining = plan.totalTasks - records.size
            val eta = today.plusDays(((remaining + plan.tasksPerDay - 1) / plan.tasksPerDay).toLong())
            return CreditResult(
                creditTasks = 0,
                creditDays = 0.0,
                state = CreditState.NOT_STARTED,
                expectedCompletionDate = eta,
                baseCompletionDate = plan.baseCompletionDate,
                earliestDeficitDate = null,
                deficitTaskCount = 0,
            )
        }

        // 计划开始后产生的新完成项数（排除 IMPORTED）
        val newCompleted = records.count { it.source != RecordSource.IMPORTED }

        // 已过去的计划日数（不含今天） × 每日项数 = 应完成项数（R11）
        val elapsedDays = calendar.elapsedPlanDays(today)
        val expectedCompleted = elapsedDays * plan.tasksPerDay

        val creditTasks = newCompleted - expectedCompleted

        val creditDays = creditTasks.toDouble() / plan.tasksPerDay

        val state = when {
            creditTasks > 0 -> CreditState.AHEAD
            creditTasks < 0 -> CreditState.DEFICIT
            else -> CreditState.EVEN
        }

        // 剩余项数
        val totalTasks = plan.totalTasks
        val remaining = (totalTasks - records.size).coerceAtLeast(0)

        // 预计完成日 = 今天 + ceil(剩余 / 每日)（R17）
        val etaDays = if (remaining == 0) 0 else (remaining + plan.tasksPerDay - 1) / plan.tasksPerDay
        val expectedCompletionDate = today.plusDays(etaDays.toLong())

        // 最早缺口日期（R16）：从计划开始日正向扫描，第一个没做完的过去计划日
        val earliestDeficitDate = if (state == CreditState.DEFICIT) {
            findEarliestDeficitDate(plan, calendar, records, today)
        } else null

        val deficitTaskCount = if (state == CreditState.DEFICIT) -creditTasks else 0

        return CreditResult(
            creditTasks = creditTasks,
            creditDays = creditDays,
            state = state,
            expectedCompletionDate = expectedCompletionDate,
            baseCompletionDate = plan.baseCompletionDate,
            earliestDeficitDate = earliestDeficitDate,
            deficitTaskCount = deficitTaskCount,
        )
    }

    /**
     * 格式化额度天数字符串（R13）。
     * 正数带 + 号，整除不显示小数，保留一位小数。
     */
    fun formatCreditDays(creditDays: Double): String {
        if (creditDays == 0.0) return "0"
        val sign = if (creditDays > 0) "+" else "−"
        val abs = kotlin.math.abs(creditDays)
        // 整除时不显示小数
        return if (abs == abs.toInt().toDouble()) {
            "$sign${abs.toInt()}"
        } else {
            "$sign${"%.1f".format(abs)}"
        }
    }

    /**
     * R23: 优先补上 — 按最早缺口顺序，找到缺额对应的最早未完成任务。
     * 返回这些任务的（articleIndex, taskIndex）列表，数量 = 当前缺额项数。
     */
    fun findEarliestDeficitTasks(
        plan: Plan,
        calendar: PlanCalendar,
        records: List<TaskRecord>,
        today: LocalDate,
        maxCount: Int,
    ): List<Pair<Int, Int>> {
        if (maxCount <= 0) return emptyList()

        val completedIds = records.map { it.id }.toSet()
        val result = mutableListOf<Pair<Int, Int>>()

        var planDayIndex = 0
        while (result.size < maxCount) {
            val dayPlan = calendar.getPlanDay(planDayIndex)
                ?: break  // 超出所有计划
            val date = dayPlan.date
            if (date >= today) break  // 只补过去的缺口

            for (taskIdx in dayPlan.taskIndices) {
                val id = TaskRecord.makeId(dayPlan.articleIndex!!, taskIdx)
                if (id !in completedIds) {
                    result.add(dayPlan.articleIndex to taskIdx)
                    if (result.size >= maxCount) return result
                }
            }
            planDayIndex++
        }
        return result
    }

    // ———————————— 内部 ————————————

    /**
     * 从计划开始日正向扫描，找第一个"当日计划的项没有全部完成"的过去计划日。
     * 过去 = < today（今天的不算欠）。
     */
    private fun findEarliestDeficitDate(
        plan: Plan,
        calendar: PlanCalendar,
        records: List<TaskRecord>,
        today: LocalDate,
    ): LocalDate? {
        val completedIds = records.map { it.id }.toSet()

        var planDayIndex = 0
        while (true) {
            val dayPlan = calendar.getPlanDay(planDayIndex) ?: return null
            val date = dayPlan.date
            if (date >= today) return null  // 扫描到今天为止，今天不算欠

            val allDone = dayPlan.taskIndices.all { taskIdx ->
                val id = TaskRecord.makeId(dayPlan.articleIndex!!, taskIdx)
                id in completedIds
            }
            if (!allDone) return date

            planDayIndex++
        }
    }
}
