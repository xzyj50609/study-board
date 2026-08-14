package com.zyj.ritual.screenshot

import com.zyj.ritual.data.repository.TodayState
import com.zyj.ritual.data.repository.VocabAggregateState
import com.zyj.ritual.domain.calculator.CreditCalculator
import com.zyj.ritual.domain.calculator.DigestionCalculator
import com.zyj.ritual.domain.calculator.ProgressCalculator
import com.zyj.ritual.domain.calculator.TodayCopyResolver
import com.zyj.ritual.domain.calendar.PlanCalendar
import com.zyj.ritual.domain.model.HistoryEvent
import com.zyj.ritual.domain.model.HistoryEventType
import com.zyj.ritual.domain.model.PaperSession
import com.zyj.ritual.domain.model.Plan
import com.zyj.ritual.domain.model.RecordSource
import com.zyj.ritual.domain.model.TaskRecord
import com.zyj.ritual.domain.vocab.VocabCalculator
import com.zyj.ritual.domain.vocab.VocabConfig
import com.zyj.ritual.domain.vocab.VocabRecord
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * 截图测试共用的假数据。
 *
 * ⚠️ 这里的日期全部写死。绝对不许改成 LocalDate.now() / Instant.now()——
 * 那样每天渲染出来的图都不一样，比对必然天天失败，这道关卡就废了。
 */

internal val TODAY: LocalDate = LocalDate.of(2026, 8, 6)

/** 8/1 开始，42 篇，每篇 2 天 → 每天 3 项。到 8/6 应完成 15 项。 */
internal val PLAN = Plan(planStartDate = LocalDate.of(2026, 8, 1))

/**
 * 造 doneTasks 条完成记录，按 1-1、1-2 … 的顺序铺，计划日期按每天 3 项推。
 *
 * 注意这里走的是真的计算器（ProgressCalculator / CreditCalculator / TodayCopyResolver），
 * 不是手填结果——所以截出来的图里那些数字和文案是真算出来的，
 * 算错了图上就看得见。
 *
 * @param withHistory 进度页要靠历史记录才有内容可看；今日页用不上，默认不造。
 */
internal fun buildState(doneTasks: Int, withHistory: Boolean = false): TodayState {
    val plan = PLAN
    val records = (0 until doneTasks).map { i ->
        val article = i / plan.tasksPerArticle + 1
        val task = i % plan.tasksPerArticle + 1
        val plannedDate = plan.planStartDate.plusDays((i / plan.tasksPerDay).toLong())
        TaskRecord(
            id = TaskRecord.makeId(article, task),
            articleIndex = article,
            taskIndex = task,
            // 固定 21:00 北京时间，别用 Instant.now()（同上：图会天天变）
            completedAt = plannedDate.atTime(21, 0).toInstant(ZoneOffset.ofHours(8)),
            plannedDate = plannedDate,
            source = RecordSource.CHECKED,
        )
    }
    val history = if (withHistory) buildHistory(records) else emptyList()
    val calendar = PlanCalendar.create(plan)
    val progress = ProgressCalculator.calculate(plan, records)
    val credit = CreditCalculator.calculate(plan, calendar, records, TODAY)
    val copy = TodayCopyResolver.resolve(plan, calendar, records, progress, credit, TODAY)
    return TodayState(
        plan = plan,
        records = records,
        history = history,
        calendar = calendar,
        progress = progress,
        credit = credit,
        copy = copy,
        today = TODAY,
        pausedDates = emptySet(),
        paperSessions = emptyList(),
    )
}

/**
 * 写过一套整卷、今天正处在空档里的今日页。
 *
 * 这一屏是 v1.1 最要紧的验收对象：装上 v1.0 之后用户看到的是一张
 * 暗铜色的"缺 N 项"，而他明明超额干完了一整套卷。所以这张图要能一眼确认：
 * 顶上是金色的空档说明（含哪天接着做），不是红/铜色的欠账警告。
 *
 * @param paperCompletedDate 卷子完成日，默认设成"今天的前两天"，
 * 于是今天正好落在空档中间。
 */
internal fun buildStateWithPaper(
    doneTasks: Int,
    paperCompletedDate: LocalDate = TODAY.minusDays(2),
): TodayState {
    val plan = PLAN
    val records = (0 until doneTasks).map { i ->
        val article = i / plan.tasksPerArticle + 1
        val task = i % plan.tasksPerArticle + 1
        val plannedDate = plan.planStartDate.plusDays((i / plan.tasksPerDay).toLong())
        TaskRecord(
            id = TaskRecord.makeId(article, task),
            articleIndex = article,
            taskIndex = task,
            completedAt = plannedDate.atTime(21, 0).toInstant(ZoneOffset.ofHours(8)),
            plannedDate = plannedDate,
            source = RecordSource.CHECKED,
        )
    }
    val sessions = listOf(
        PaperSession(
            id = 1,
            name = "2016 年卷",
            completedDate = paperCompletedDate,
            createdAt = paperCompletedDate.atTime(22, 30).toInstant(ZoneOffset.ofHours(8)),
        )
    )
    val paused = DigestionCalculator.pausedDates(sessions)
    val calendar = PlanCalendar.create(plan, paused)
    val progress = ProgressCalculator.calculate(plan, records)
    val credit = CreditCalculator.calculate(plan, calendar, records, TODAY, paused)
    val todayPaper = DigestionCalculator.sessionCovering(TODAY, sessions)
    val resumeDate = todayPaper?.let { DigestionCalculator.resumeDate(TODAY, sessions) }
    val copy = TodayCopyResolver.resolve(
        plan, calendar, records, progress, credit, TODAY, todayPaper, resumeDate
    )
    return TodayState(
        plan = plan,
        records = records,
        history = emptyList(),
        calendar = calendar,
        progress = progress,
        credit = credit,
        copy = copy,
        today = TODAY,
        pausedDates = paused,
        paperSessions = sessions,
        resumeDate = resumeDate,
    )
}

// ════════════════════════════════════════════════════════════════
//  背词假数据（进度页摘要卡、月历第二个点、历史页混排都要用）
// ════════════════════════════════════════════════════════════════

internal val VOCAB_CONFIG = VocabConfig(
    totalWords = 1883,
    initialDone = 380,
    dailyWords = 20,
    startDate = "2026-07-27",
    examDate = "2026-12-19",
    reviewDue = 45,
    reviewDueDate = "2026-08-06",
)

/**
 * 7/27 起匀速背 11 天 + 累计复习 526 + 今天复习 38。
 * 这组数正好落在「档中间」，摘要卡上能同时看到百分比和「第 2 档」。
 */
internal fun buildVocabRecords(): List<VocabRecord> =
    (0..10).map { i ->
        VocabRecord(LocalDate.of(2026, 7, 27).plusDays(i.toLong()).toString(), 20, "new")
    } + listOf(
        VocabRecord("2026-08-05", 526, "backlog"),
        VocabRecord("2026-08-06", 38, "backlog"),
    )

/**
 * 月历专用的一组。月历页初始停在**今天所在的月**（8 月），
 * 所以三种点的样本必须全部落在 8 月里——第一版把只复习的日子放在 7/29、7/30，
 * 基准图上根本拍不到，突变验证时才发现这张关卡是空的。
 *
 * - 8/1、8/2、8/6：背了新词  → 实心紫
 * - 8/3、8/4：**只复习没背新词** → 空心紫（这两天是这张图存在的理由）
 * - 8/5：一条记录都没有 → 空心红（缺口）
 */
internal fun buildCalendarVocabAggregate(): VocabAggregateState {
    val records = listOf(
        VocabRecord("2026-07-27", 20, "new"),
        VocabRecord("2026-07-28", 20, "new"),
        VocabRecord("2026-08-01", 20, "new"),
        VocabRecord("2026-08-02", 20, "new"),
        VocabRecord("2026-08-03", 40, "backlog"),   // 只复习
        VocabRecord("2026-08-04", 50, "backlog"),   // 只复习
        // 8/5 一条都没有 → gap
        VocabRecord("2026-08-06", 20, "new"),
    )
    val todayStr = TODAY.toString()
    return VocabAggregateState(
        config = VOCAB_CONFIG,
        records = records,
        state = VocabCalculator.computeState(records, VOCAB_CONFIG, todayStr),
        calendar = VocabCalculator.computeCalendar(records, VOCAB_CONFIG, todayStr),
        today = TODAY,
    )
}

internal fun buildProgressVocabAggregate(): VocabAggregateState {
    val records = buildVocabRecords()
    val todayStr = TODAY.toString()
    return VocabAggregateState(
        config = VOCAB_CONFIG,
        records = records,
        state = VocabCalculator.computeState(records, VOCAB_CONFIG, todayStr),
        calendar = VocabCalculator.computeCalendar(records, VOCAB_CONFIG, todayStr),
        today = TODAY,
    )
}

/**
 * 最近 8 条历史，掺一条撤销和一条补记——
 * 进度页那排状态标签（"完成 / 撤销 / 补记"）只有掺进不同类型才看得见，
 * 而标签底色正是亮色下最容易糊掉的地方之一。
 */
private fun buildHistory(records: List<TaskRecord>): List<HistoryEvent> {
    // completedAt 是可空的（IMPORTED 那种没有真实完成时刻），这里只取有时间戳的
    val tail = records.mapNotNull { r -> r.completedAt?.let { r to it } }.takeLast(8)
    return tail.mapIndexed { i, (r, at) ->
        HistoryEvent(
            id = (i + 1).toLong(),
            at = at,
            type = when (i % 4) {
                1 -> HistoryEventType.BACKFILL
                3 -> HistoryEventType.UNDO
                else -> HistoryEventType.CHECKED
            },
            articleIndex = r.articleIndex,
            taskIndex = r.taskIndex,
            taskName = TodayCopyResolver.taskName(r.taskIndex),
        )
    }
}
