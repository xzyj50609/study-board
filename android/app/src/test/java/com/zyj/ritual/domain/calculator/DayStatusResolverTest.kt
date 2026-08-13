package com.zyj.ritual.domain.calculator

import com.zyj.ritual.domain.calendar.PlanCalendar
import com.zyj.ritual.domain.model.CalendarCellStatus
import com.zyj.ritual.domain.model.CreditState
import com.zyj.ritual.domain.model.Plan
import com.zyj.ritual.domain.model.RecordSource
import com.zyj.ritual.domain.model.TaskRecord
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/**
 * 日历日格状态测试（R26-R30）。
 */
class DayStatusResolverTest {

    private val startDate = LocalDate.of(2026, 8, 5)  // 周三
    private val plan = Plan(
        totalArticles = 42,
        startArticle = 13,
        completedBeforeStart = 12,
        planStartDate = startDate,
        daysPerArticle = 2,
    )
    private val cal = PlanCalendar.create(plan)

    private val today = startDate.plusDays(3)  // 8/8 周六

    private fun record(art: Int, task: Int, date: LocalDate, source: RecordSource = RecordSource.CHECKED): TaskRecord {
        val instant = Instant.parse("${date}T10:00:00Z")
        return TaskRecord(
            id = TaskRecord.makeId(art, task),
            articleIndex = art,
            taskIndex = task,
            completedAt = instant,
            plannedDate = date,
            source = source,
        )
    }

    private fun resolve(date: LocalDate, records: List<TaskRecord>, creditState: CreditState = CreditState.EVEN): CalendarCellStatus {
        return DayStatusResolver.resolve(plan, cal, records, date, today, creditState)
    }

    // ——— R26：计划开始前 ———

    @Test
    fun `R26 计划开始前=BEFORE_START`() {
        assertEquals(CalendarCellStatus.BEFORE_START, resolve(startDate.minusDays(1), emptyList()))
    }

    // ——— R28：今天 ———

    @Test
    fun `R28 今天0项=TODAY`() {
        // today = 8/8，计划第 3 天 = 第 14 篇相位 1
        assertEquals(CalendarCellStatus.TODAY, resolve(today, emptyList()))
    }

    @Test
    fun `R28 今天2项=TODAY_PARTIAL`() {
        // 8/8 = 计划日 3 = 第 14 篇 phase 2 = 任务 4,5,6
        val records = (4..5).map { record(14, it, today) }
        assertEquals(CalendarCellStatus.TODAY_PARTIAL, resolve(today, records))
    }

    @Test
    fun `R28 今天3项=TODAY_COMPLETE`() {
        val records = (4..6).map { record(14, it, today) }
        assertEquals(CalendarCellStatus.TODAY_COMPLETE, resolve(today, records))
    }

    // ——— R29：过去的日子 ———

    @Test
    fun `R29 过去按计划完成=PAST_ON_PLAN`() {
        // 8/5 的 3 项在 8/5 当天完成
        val records = (1..3).map { record(13, it, startDate) }
        assertEquals(CalendarCellStatus.PAST_ON_PLAN, resolve(startDate, records))
    }

    @Test
    fun `R29 过去部分完成+额度足够=PAST_COVERED`() {
        // 8/5 只做了 1 项，但额度 >= 0（被别处提前量覆盖）
        val records = listOf(record(13, 1, startDate))
        assertEquals(CalendarCellStatus.PAST_COVERED, resolve(startDate, records, CreditState.AHEAD))
    }

    @Test
    fun `R29 过去部分完成+缺额=PAST_DEFICIT`() {
        val records = listOf(record(13, 1, startDate))
        assertEquals(CalendarCellStatus.PAST_DEFICIT, resolve(startDate, records, CreditState.DEFICIT))
    }

    @Test
    fun `R29 过去后续补完成=PAST_BACKFILL`() {
        // 8/5 的任务在 8/7 才完成（补打卡）
        val records = (1..3).map { record(13, it, startDate.plusDays(2), RecordSource.BACKFILL) }
        // 注意：plannedDate 是补打卡时的计划日期快照，应该是 8/5
        // 但 completedAt 是 8/7
        val backfillRecords = records.map {
            it.copy(plannedDate = startDate, completedAt = Instant.parse("2026-08-07T10:00:00Z"))
        }
        assertEquals(CalendarCellStatus.PAST_BACKFILL, resolve(startDate, backfillRecords))
    }

    @Test
    fun `R29 过去提前完成=PAST_ADVANCED`() {
        // 8/5 的任务在 8/4 就完成了（提前）
        val records = (1..3).map {
            record(13, it, startDate.minusDays(1), RecordSource.ADVANCED)
                .copy(plannedDate = startDate)
        }
        val status = resolve(startDate, records)
        assertEquals(CalendarCellStatus.PAST_ADVANCED, status)
    }

    // ——— R30：未来的日子 ———

    @Test
    fun `R30 未来未完成=FUTURE_PLANNED`() {
        val future = today.plusDays(5)
        assertEquals(CalendarCellStatus.FUTURE_PLANNED, resolve(future, emptyList()))
    }

    @Test
    fun `R30 未来已提前完成=FUTURE_ADVANCED`() {
        val future = today.plusDays(5)
        val dayPlan = cal.getDayPlan(future)
        val records = dayPlan.taskIndices.map {
            TaskRecord(
                id = TaskRecord.makeId(requireNotNull(dayPlan.articleIndex), it),
                articleIndex = requireNotNull(dayPlan.articleIndex),
                taskIndex = it,
                completedAt = Instant.parse("${today}T10:00:00Z"),
                plannedDate = future,
                source = RecordSource.ADVANCED,
            )
        }
        assertEquals(CalendarCellStatus.FUTURE_ADVANCED, resolve(future, records))
    }
}
