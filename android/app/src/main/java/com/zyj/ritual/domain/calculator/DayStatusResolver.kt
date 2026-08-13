package com.zyj.ritual.domain.calculator

import com.zyj.ritual.domain.calendar.PlanCalendar
import com.zyj.ritual.domain.model.CalendarCellStatus
import com.zyj.ritual.domain.model.CreditState
import com.zyj.ritual.domain.model.Plan
import com.zyj.ritual.domain.model.TaskRecord
import java.time.LocalDate

/**
 * 日历日格状态判定（R26-R30）。
 *
 * 设 D 为某个日期，P(D) 为该日的计划（篇号 + 相位 + 任务列表），今天为 T：
 *
 * R26: D < 计划开始日 → BEFORE_START
 * R28: D == T → TODAY / TODAY_PARTIAL / TODAY_COMPLETE
 * R29: D < T → PAST_ON_PLAN / PAST_PARTIAL / PAST_BACKFILL / PAST_ADVANCED / PAST_COVERED / PAST_DEFICIT
 * R30: D > T → FUTURE_PLANNED / FUTURE_ADVANCED
 *
 * 过去日的细分逻辑（R29 完整版）：
 * - 当日完成全部 3 项：
 *     - 完成时都是在计划日当天 or 之前 → PAST_ON_PLAN
 *     - 有在 D 之后完成的（补上来的）→ PAST_BACKFILL
 *     - 全部都在 D 之前完成 → PAST_ADVANCED（其实和 on plan 差不多，按最早完成时间判断）
 *   简化：只要完成日期 < 计划日期 → PAST_ADVANCED；完成日期 == 计划日期 → PAST_ON_PLAN；完成日期 > 计划日期 → PAST_BACKFILL
 * - 当日未完成全部 3 项：
 *     - 当前额度 >= 0 → PAST_COVERED
 *     - 当前额度 < 0 → PAST_DEFICIT
 *
 * 设计系统把"后续补完成"和"提前完成"分开成两个态，我按记录时间戳与计划日期比较得出。
 */
object DayStatusResolver {

    fun resolve(
        plan: Plan,
        calendar: PlanCalendar,
        records: List<TaskRecord>,
        date: LocalDate,
        today: LocalDate,
        creditState: CreditState,
    ): CalendarCellStatus {
        val completedIds = records.associate { it.id to it }

        // R26：计划开始前
        if (date < plan.planStartDate) {
            return CalendarCellStatus.BEFORE_START
        }

        val dayPlan = calendar.getDayPlan(date)
        if (!dayPlan.isPlanDay) {
            // 不是计划日（比如超出最后一篇之后的日子）
            // 按"计划开始前"的同类处理——灰蓝点
            return CalendarCellStatus.BEFORE_START
        }

        val taskIds = dayPlan.taskIndices.map { TaskRecord.makeId(dayPlan.articleIndex!!, it) }
        val doneCount = taskIds.count { it in completedIds }
        val total = taskIds.size

        return when {
            date == today -> resolveToday(doneCount, total)
            date < today -> resolvePast(plan, calendar, records, completedIds, taskIds, doneCount, total, date, dayPlan, creditState)
            else -> resolveFuture(doneCount, total)
        }
    }

    /**
     * 判断某天是否可交互（能不能在面板里勾/取消）。
     * - 计划开始前 → 不可勾
     * - 非计划日 → 不可勾
     * - 其他 → 可勾（今天、过去、未来都可以操作）
     */
    fun isInteractive(
        plan: Plan,
        calendar: PlanCalendar,
        date: LocalDate,
    ): Boolean {
        if (date < plan.planStartDate) return false
        val dayPlan = calendar.getDayPlan(date)
        return dayPlan.isPlanDay
    }

    // ———————————— 内部 ————————————

    private fun resolveToday(doneCount: Int, total: Int): CalendarCellStatus {
        return when {
            doneCount >= total -> CalendarCellStatus.TODAY_COMPLETE
            doneCount > 0 -> CalendarCellStatus.TODAY_PARTIAL
            else -> CalendarCellStatus.TODAY
        }
    }

    private fun resolvePast(
        plan: Plan,
        calendar: PlanCalendar,
        records: List<TaskRecord>,
        completedIds: Map<String, TaskRecord>,
        taskIds: List<String>,
        doneCount: Int,
        total: Int,
        date: LocalDate,
        dayPlan: com.zyj.ritual.domain.model.DayPlan,
        creditState: CreditState,
    ): CalendarCellStatus {
        if (doneCount >= total) {
            // 全部完成：判断是按计划完成 / 提前完成 / 后续补完成
            val plannedDate = dayPlan.date  // 该日的计划日期
            val zone = java.time.ZoneId.of("Asia/Shanghai")

            // IMPORTED 记录没有 completedAt，按"按计划完成"算
            val hasImported = taskIds.any { completedIds[it]?.source == com.zyj.ritual.domain.model.RecordSource.IMPORTED }
            if (hasImported) {
                return CalendarCellStatus.PAST_ON_PLAN
            }

            // 找出最早完成时间和最晚完成时间
            val completionDates = taskIds.mapNotNull { id ->
                completedIds[id]?.completedAt?.atZone(zone)?.toLocalDate()
            }
            if (completionDates.isEmpty()) {
                return CalendarCellStatus.PAST_ON_PLAN
            }
            val earliestDate = completionDates.min()
            val latestDate = completionDates.max()

            return when {
                // 全部都在计划日之前完成 → 提前完成（金环）
                latestDate.isBefore(plannedDate) -> CalendarCellStatus.PAST_ADVANCED
                // 全部都在计划日当天或之前 → 按计划完成
                !latestDate.isAfter(plannedDate) -> CalendarCellStatus.PAST_ON_PLAN
                // 有在计划日之后完成的 → 后续补完成（空心蓝环）
                else -> CalendarCellStatus.PAST_BACKFILL
            }
        } else {
            // 未完成全部：缺额 or 额度覆盖
            return if (creditState == CreditState.DEFICIT) {
                CalendarCellStatus.PAST_DEFICIT
            } else {
                CalendarCellStatus.PAST_COVERED
            }
        }
    }

    private fun resolveFuture(doneCount: Int, total: Int): CalendarCellStatus {
        return if (doneCount >= total) {
            CalendarCellStatus.FUTURE_ADVANCED
        } else {
            CalendarCellStatus.FUTURE_PLANNED
        }
    }
}
