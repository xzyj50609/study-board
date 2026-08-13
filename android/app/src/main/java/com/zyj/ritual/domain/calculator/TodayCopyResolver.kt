package com.zyj.ritual.domain.calculator

import com.zyj.ritual.domain.calendar.PlanCalendar
import com.zyj.ritual.domain.model.*
import java.time.LocalDate

/**
 * 首页标题（R31-R34）和底部主按钮（R35-R38）的文案与目标计算。
 */
object TodayCopyResolver {

    private val TASK_NAMES = listOf(
        "通读全文",
        "完成课后习题",
        "核对答案，标出错题和不确定的题",
        "弄懂错题和不确定的题",
        "弄懂标出的单词和短语",
        "弄懂标出的长难句",
    )

    fun taskName(taskIndex: Int): String = TASK_NAMES.getOrElse(taskIndex - 1) { "任务 $taskIndex" }

    fun resolve(
        plan: Plan,
        calendar: PlanCalendar,
        records: List<TaskRecord>,
        progress: ProgressResult,
        credit: CreditResult,
        today: LocalDate,
        todayPaper: PaperSession? = null,
    ): TodayCopyResult {
        // 消化日：整卷换来的休整，标题直接认账，不用"第 N 篇还剩 X 项"催人
        if (todayPaper != null && !progress.isAllDone) {
            val dayIndex = DigestionCalculator.digestionDayIndex(todayPaper, today)
            val note = if (dayIndex == 0) {
                "今天登记整套卷 · 剩余阅读任务豁免，想提前学随时可以"
            } else {
                "消化日 $dayIndex/${todayPaper.digestionDays} · 休整不计欠账，想提前学随时可以"
            }
            return TodayCopyResult(
                headline = "消化日 · ${todayPaper.name}",
                primaryButtonText = "继续学习",
                primaryButtonTarget = PrimaryButtonTarget.StartNextArticle,
                digestionNote = note,
            )
        }

        val headline = resolveHeadline(plan, progress)
        val (buttonText, target) = resolvePrimaryButton(plan, calendar, records, progress, credit, today)

        return TodayCopyResult(
            headline = headline,
            primaryButtonText = buttonText,
            primaryButtonTarget = target,
        )
    }

    // ——— 标题 R31-R34 ———

    private fun resolveHeadline(plan: Plan, progress: ProgressResult): String {
        return when {
            progress.isAllDone -> "全部读完了"  // R31
            progress.currentArticleCompleted == 0 -> "今天开始第 ${progress.currentArticle} 篇"  // R32
            progress.currentArticleCompleted < plan.tasksPerArticle ->
                "第 ${progress.currentArticle} 篇还剩 ${plan.tasksPerArticle - progress.currentArticleCompleted} 项"  // R33
            else -> "第 ${progress.currentArticle} 篇已完成"  // R34
        }
    }

    // ——— 主按钮 R35-R38 ———

    private fun resolvePrimaryButton(
        plan: Plan,
        calendar: PlanCalendar,
        records: List<TaskRecord>,
        progress: ProgressResult,
        credit: CreditResult,
        today: LocalDate,
    ): Pair<String, PrimaryButtonTarget> {
        val completedIds = records.map { it.id }.toSet()

        // R35: 全部完成 → 查看完成页
        if (progress.isAllDone) {
            return "查看完成页" to PrimaryButtonTarget.GoToDonePage
        }

        // R37: 有缺额时 → 优先补上，指向最早缺口的第一项
        if (credit.state == CreditState.DEFICIT && credit.earliestDeficitDate != null) {
            val deficitTasks = CreditCalculator.findEarliestDeficitTasks(
                plan, calendar, records, today, maxCount = 1
            )
            if (deficitTasks.isNotEmpty()) {
                val (art, task) = deficitTasks.first()
                val dateStr = formatDateShort(credit.earliestDeficitDate)
                return "补上 $dateStr 的「${taskName(task)}」" to
                    PrimaryButtonTarget.CheckTask(art, task)
            }
        }

        // R36: 当前篇 0 项完成（刚开启新一篇）→ 「开始第 N 篇」
        // 比直接说"完成「通读全文」"更有仪式感
        if (progress.currentArticleCompleted == 0 && !progress.isAllDone) {
            return "开始第 ${progress.currentArticle} 篇" to PrimaryButtonTarget.StartNextArticle
        }

        // R38: 其他 → 完成「最早未完成项的任务名」
        // 找当前篇的第一个未完成项
        val nextTask = findNextTaskInArticle(completedIds, progress.currentArticle, plan.tasksPerArticle)
        if (nextTask != null) {
            return "完成「${taskName(nextTask)}」" to
                PrimaryButtonTarget.CheckTask(progress.currentArticle, nextTask)
        }

        // 兜底（理论上不会走到这里）
        return "继续学习" to PrimaryButtonTarget.StartNextArticle
    }

    private fun findNextTaskInArticle(
        completedIds: Set<String>,
        articleIndex: Int,
        tasksPerArticle: Int,
    ): Int? {
        for (i in 1..tasksPerArticle) {
            val id = TaskRecord.makeId(articleIndex, i)
            if (id !in completedIds) return i
        }
        return null
    }

    private fun formatDateShort(date: LocalDate): String {
        return "${date.monthValue}月${date.dayOfMonth}日"
    }
}
