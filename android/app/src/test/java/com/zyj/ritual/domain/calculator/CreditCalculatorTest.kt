package com.zyj.ritual.domain.calculator

import com.zyj.ritual.domain.calendar.PlanCalendar
import com.zyj.ritual.domain.model.CreditState
import com.zyj.ritual.domain.model.Plan
import com.zyj.ritual.domain.model.RecordSource
import com.zyj.ritual.domain.model.TaskRecord
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/**
 * 额度与缺额测试（R11-R18, R23）。
 *
 * 核心公式：额度 = 计划开始后新完成项数 − 已过去计划日 × 每日项数
 * IMPORTED 不参与额度计算（R15）。
 */
class CreditCalculatorTest {

    private val startDate = LocalDate.of(2026, 8, 5)  // 周三
    private val plan = Plan(
        totalArticles = 42,
        startArticle = 13,
        completedBeforeStart = 12,
        planStartDate = startDate,
        daysPerArticle = 2,
    )
    private val cal = PlanCalendar.create(plan)

    // IMPORTED 记录（不参与额度计算）
    private fun importedRecords(): List<TaskRecord> =
        (1..12).flatMap { art ->
            (1..6).map { task ->
                TaskRecord(
                    id = TaskRecord.makeId(art, task),
                    articleIndex = art,
                    taskIndex = task,
                    completedAt = null,
                    plannedDate = null,
                    source = RecordSource.IMPORTED,
                )
            }
        }

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

    // ——— R11：应完成项数 = 已过去计划日 × 每日项数（不含今天）———

    @Test
    fun `R11 计划开始当天应完成项数=0（今天不算欠）`() {
        val today = startDate
        val records = importedRecords()  // 没有新完成
        val credit = CreditCalculator.calculate(plan, cal, records, today)
        assertEquals(0, credit.creditTasks)
        assertEquals(CreditState.EVEN, credit.state)
    }

    @Test
    fun `R11 计划开始第2天 做了3项 额度=0`() {
        // 8/6 是第 2 天，已过去 1 天（8/5），应完成 3 项
        val today = startDate.plusDays(1)  // 8/6
        val records = importedRecords() + (1..3).map { record(13, it, startDate) }  // 8/5 做了 3 项
        val credit = CreditCalculator.calculate(plan, cal, records, today)
        assertEquals(0, credit.creditTasks)
        assertEquals(CreditState.EVEN, credit.state)
    }

    // ——— R12：额度 = 新完成 − 应完成 ———

    @Test
    fun `R12 第一天做了6项 额度=+3`() {
        // 今天 = 8/5（第 0 天），已过去 0 天，应完成 0
        // 新完成 6 项 → 额度 = 6 - 0 = +6（项）= +2 天
        val today = startDate
        val records = importedRecords() + (1..6).map { record(13, it, today) }
        val credit = CreditCalculator.calculate(plan, cal, records, today)
        assertEquals(6, credit.creditTasks)
        assertEquals(CreditState.AHEAD, credit.state)
    }

    @Test
    fun `R12 空过一天缺3项`() {
        // 8/6 已是第 2 天，应完成 3 项（8/5 的）
        // 但一项都没做 → 额度 = 0 - 3 = -3
        val today = startDate.plusDays(1)
        val records = importedRecords()  // 一项都没新做
        val credit = CreditCalculator.calculate(plan, cal, records, today)
        assertEquals(-3, credit.creditTasks)
        assertEquals(CreditState.DEFICIT, credit.state)
        assertEquals(3, credit.deficitTaskCount)
    }

    // ——— R13：额度天数格式化 ———

    @Test
    fun `R13 整除时不显示小数`() {
        assertEquals("+2", CreditCalculator.formatCreditDays(2.0))
        assertEquals("−1", CreditCalculator.formatCreditDays(-1.0))
        assertEquals("0", CreditCalculator.formatCreditDays(0.0))
    }

    @Test
    fun `R13 非整除保留一位小数`() {
        assertEquals("+1.5", CreditCalculator.formatCreditDays(1.5))
        assertEquals("−0.5", CreditCalculator.formatCreditDays(-0.5))
    }

    // ——— R15：IMPORTED 不参与额度 ———

    @Test
    fun `R15 起点72项不算额度`() {
        val today = startDate
        val records = importedRecords()  // 72 条 IMPORTED
        val credit = CreditCalculator.calculate(plan, cal, records, today)
        assertEquals(0, credit.creditTasks)
        assertEquals(CreditState.EVEN, credit.state)
    }

    // ——— R16：最早缺口日期 ———

    @Test
    fun `R16 空过2天 最早缺口=8月5日`() {
        val today = startDate.plusDays(2)  // 8/7，已过去 2 天（8/5, 8/6），应完成 6 项
        val records = importedRecords()  // 一项都没新做
        val credit = CreditCalculator.calculate(plan, cal, records, today)
        assertEquals(startDate, credit.earliestDeficitDate)
        assertEquals(-6, credit.creditTasks)
    }

    @Test
    fun `R16 第一天做了第二天没做 最早缺口=8月6日`() {
        val today = startDate.plusDays(2)  // 8/7
        val records = importedRecords() + (1..3).map { record(13, it, startDate) }  // 8/5 做了 3 项
        val credit = CreditCalculator.calculate(plan, cal, records, today)
        assertEquals(startDate.plusDays(1), credit.earliestDeficitDate)  // 8/6
        assertEquals(-3, credit.creditTasks)
    }

    @Test
    fun `R16 齐平时最早缺口为null`() {
        val today = startDate.plusDays(1)
        val records = importedRecords() + (1..3).map { record(13, it, startDate) }
        val credit = CreditCalculator.calculate(plan, cal, records, today)
        assertNull(credit.earliestDeficitDate)
    }

    // ——— R17：预计完成日 ———

    @Test
    fun `R17 起点时预计完成日=今天加剩余项除以3向上取整`() {
        val today = startDate
        val records = importedRecords()
        val credit = CreditCalculator.calculate(plan, cal, records, today)
        // 剩余 = 252 - 72 = 180 项，180 / 3 = 60 天
        // 今天 + 60 天
        assertEquals(today.plusDays(60), credit.expectedCompletionDate)
    }

    @Test
    fun `R17 做了1项后预计完成日减少`() {
        val today = startDate
        val records = importedRecords() + record(13, 1, today)
        val credit = CreditCalculator.calculate(plan, cal, records, today)
        // 剩余 179 项，ceil(179/3) = 60 天
        assertEquals(today.plusDays(60), credit.expectedCompletionDate)
    }

    // ——— R18：基础计划完成日 ———

    @Test
    fun `R18 基础计划完成日`() {
        // 30 篇 × 2 天 = 60 个计划日，从 8/5 起，第 59 天后 = 10/3
        val expected = startDate.plusDays(59)
        assertEquals(expected, plan.baseCompletionDate)
    }

    // ——— R23：优先补上 ———

    @Test
    fun `R23 缺3项时优先补上返回最早3项`() {
        val today = startDate.plusDays(2)  // 8/7
        val records = importedRecords()  // 一项都没做，缺 6 项
        val tasks = CreditCalculator.findEarliestDeficitTasks(plan, cal, records, today, 3)
        assertEquals(3, tasks.size)
        // 最早缺口是 8/5（第 13 篇相位 1）的 1, 2, 3 项
        assertEquals(13 to 1, tasks[0])
        assertEquals(13 to 2, tasks[1])
        assertEquals(13 to 3, tasks[2])
    }

    @Test
    fun `R23 第一天已完成 最早缺口从第二天开始`() {
        val today = startDate.plusDays(2)  // 8/7
        val records = importedRecords() + (1..3).map { record(13, it, startDate) }
        val tasks = CreditCalculator.findEarliestDeficitTasks(plan, cal, records, today, 6)
        // 缺 3 项（8/6 的）
        assertTrue(tasks.size <= 3)
        assertEquals(13 to 4, tasks[0])
    }

    // ——— 计划还没开始 ———

    @Test
    fun `计划开始前状态为NOT_STARTED`() {
        val today = startDate.minusDays(5)
        val records = importedRecords()
        val credit = CreditCalculator.calculate(plan, cal, records, today)
        assertEquals(CreditState.NOT_STARTED, credit.state)
    }
}
