package com.zyj.ritual.domain.calculator

import com.zyj.ritual.domain.calendar.PlanCalendar
import com.zyj.ritual.domain.model.Plan
import com.zyj.ritual.domain.model.ProgressResult
import java.time.LocalDate

/**
 * 重新排期计算（R39-R42）。
 *
 * R39: 确认后 planStartDate = 今天、startArticle = 当前篇。
 *      已完成记录和它们的时间戳、计划日期快照一律不变。
 * R40: 重排后额度归零。
 * R41: 区分领先/缺额两种文案。
 * R42: 不自动重排，只有用户主动点才排。
 */
object RescheduleCalculator {

    /**
     * 计算重排后的新 Plan。
     * 已完成记录不动，只改计划参数。
     */
    fun reschedule(
        currentPlan: Plan,
        progress: ProgressResult,
        today: LocalDate,
    ): Plan {
        return currentPlan.copy(
            planStartDate = today,
            startArticle = progress.currentArticle,
            // completedBeforeStart 不变：已完成的篇数仍然是已完成的
            // 只是计划轴从今天重新画
        )
    }

    /**
     * 预测重排后的预计完成日（给确认弹窗外文案用，R41）。
     */
    fun projectedCompletionDate(
        plan: Plan,
        progress: ProgressResult,
        today: LocalDate,
    ): LocalDate {
        val remainingArticles = plan.totalArticles - progress.currentArticle + 1
        val planDays = remainingArticles * plan.daysPerArticle
        // 第一天就是今天
        return today.plusDays((planDays - 1).toLong())
    }

    /**
     * 重排前后的状态差异，给确认弹窗写文案用。
     */
    data class ReschedulePreview(
        val newStartDate: LocalDate,
        val newStartArticle: Int,
        val newBaseCompletionDate: LocalDate,
        val wasAhead: Boolean,  // 之前是领先还是缺额
    )

    fun preview(
        plan: Plan,
        progress: ProgressResult,
        credit: com.zyj.ritual.domain.model.CreditResult,
        today: LocalDate,
    ): ReschedulePreview {
        val newPlan = reschedule(plan, progress, today)
        val newCompletion = newPlan.baseCompletionDate
        return ReschedulePreview(
            newStartDate = today,
            newStartArticle = progress.currentArticle,
            newBaseCompletionDate = newCompletion,
            wasAhead = credit.state == com.zyj.ritual.domain.model.CreditState.AHEAD,
        )
    }
}
