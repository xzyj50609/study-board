package com.zyj.ritual.domain.calculator

import com.zyj.ritual.domain.model.Plan
import com.zyj.ritual.domain.model.RecordSource
import com.zyj.ritual.domain.model.TaskRecord
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/**
 * 进度计算测试（R6-R10）。
 */
class ProgressCalculatorTest {

    private val startDate = LocalDate.of(2026, 8, 5)
    private val plan = Plan(
        totalArticles = 42,
        startArticle = 13,
        completedBeforeStart = 12,
        planStartDate = startDate,
        daysPerArticle = 2,
    )

    // 生成起点 IMPORTED 记录（12 篇 × 6 项 = 72 条）
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

    // 第 13 篇的某条完成记录
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

    // ——— R6：整体进度 ———

    @Test
    fun `R6 起点12篇时进度约为28点57percent`() {
        val records = importedRecords()
        val result = ProgressCalculator.calculate(plan, records)
        assertEquals(72, result.totalCompleted)
        assertEquals(252, result.totalTasks)
        assertEquals("28.57", ProgressCalculator.formatOverallProgress(result.overallProgress))
    }

    @Test
    fun `R6 再做1项约为28点97percent`() {
        val records = importedRecords() + record(13, 1, startDate)
        val result = ProgressCalculator.calculate(plan, records)
        assertEquals(73, result.totalCompleted)
        assertEquals("28.97", ProgressCalculator.formatOverallProgress(result.overallProgress))
    }

    @Test
    fun `R6 做满252项为100percent`() {
        // 模拟全部完成
        val all = (1..42).flatMap { art ->
            (1..6).map { task ->
                if (art <= 12) record(art, task, startDate.minusDays(1), RecordSource.IMPORTED)
                    .copy(completedAt = null, plannedDate = null)  // IMPORTED
                else record(art, task, startDate.plusDays((art - 13) * 2L + task / 4))
            }
        }
        val result = ProgressCalculator.calculate(plan, all)
        assertEquals(252, result.totalCompleted)
        assertEquals("100.00", ProgressCalculator.formatOverallProgress(result.overallProgress))
        assertTrue(result.isAllDone)
    }

    // ——— R7：完整篇数 ———

    @Test
    fun `R7 起点12篇时完整篇数=12`() {
        val records = importedRecords()
        val result = ProgressCalculator.calculate(plan, records)
        assertEquals(12, result.completeArticles)
    }

    @Test
    fun `R7 第13篇做满6项完整篇数=13`() {
        val records = importedRecords() + (1..6).map { record(13, it, startDate) }
        val result = ProgressCalculator.calculate(plan, records)
        assertEquals(13, result.completeArticles)
    }

    @Test
    fun `R7 第13篇做5项完整篇数还是12`() {
        val records = importedRecords() + (1..5).map { record(13, it, startDate) }
        val result = ProgressCalculator.calculate(plan, records)
        assertEquals(12, result.completeArticles)
    }

    @Test
    fun `R7 撤销第6项完整篇数-1`() {
        val records = importedRecords() + (1..5).map { record(13, it, startDate) }
        val result = ProgressCalculator.calculate(plan, records)
        assertEquals(12, result.completeArticles)
    }

    // ——— R8：当前文章进度 ———

    @Test
    fun `R8 第13篇0项时当前进度=0`() {
        val records = importedRecords()
        val result = ProgressCalculator.calculate(plan, records)
        assertEquals(0, result.currentArticleCompleted)
        assertEquals(0.0, result.currentArticleProgress, 0.001)
    }

    @Test
    fun `R8 第13篇3项时当前进度为0点5`() {
        val records = importedRecords() + (1..3).map { record(13, it, startDate) }
        val result = ProgressCalculator.calculate(plan, records)
        assertEquals(3, result.currentArticleCompleted)
        assertEquals(0.5, result.currentArticleProgress, 0.001)
    }

    // ——— R9：当前篇 ———

    @Test
    fun `R9 起点时当前篇=13`() {
        val records = importedRecords()
        val result = ProgressCalculator.calculate(plan, records)
        assertEquals(13, result.currentArticle)
    }

    @Test
    fun `R9 第13篇做完后当前篇=14`() {
        val records = importedRecords() + (1..6).map { record(13, it, startDate) }
        val result = ProgressCalculator.calculate(plan, records)
        assertEquals(14, result.currentArticle)
    }

    @Test
    fun `R9 全部做完时当前篇=42`() {
        // 全部 42 篇都满
        val all = (1..42).flatMap { art ->
            (1..6).map { record(art, task = it, startDate) }
        }
        val result = ProgressCalculator.calculate(plan, all)
        assertEquals(42, result.currentArticle)
    }

    // ——— R10：撤销会回退 ———

    @Test
    fun `R10 撤销第13篇第6项整体进度回退`() {
        val with6 = importedRecords() + (1..6).map { record(13, it, startDate) }
        val with5 = importedRecords() + (1..5).map { record(13, it, startDate) }
        val r6 = ProgressCalculator.calculate(plan, with6)
        val r5 = ProgressCalculator.calculate(plan, with5)
        assertTrue(r6.overallProgress > r5.overallProgress)
        assertEquals(78, r6.totalCompleted)
        assertEquals(77, r5.totalCompleted)
    }

    // ——— 越界防护 ———
    // 用户把总篇数调小之后，旧记录的篇号会超出范围。
    // 这里以前是 IntArray 直接下标自增 → ArrayIndexOutOfBounds，
    // 异常被上层 catch 吞掉，界面就永远停在"加载中"。

    @Test
    fun `记录篇号超出总篇数时忽略而不是崩溃`() {
        val shrunk = plan.copy(totalArticles = 20)
        val records = importedRecords() + record(40, 1, startDate)

        val result = ProgressCalculator.calculate(shrunk, records)

        assertEquals(20 * 6, result.totalTasks)
        assertEquals("越界那条记录不计入", 72, result.totalCompleted)
        assertEquals(12, result.completeArticles)
    }

    @Test
    fun `篇号为零或负数的脏记录同样被忽略`() {
        val dirty = TaskRecord(
            id = TaskRecord.makeId(0, 1),
            articleIndex = 0,
            taskIndex = 1,
            completedAt = null,
            plannedDate = null,
            source = RecordSource.IMPORTED,
        )
        val result = ProgressCalculator.calculate(plan, importedRecords() + dirty)
        assertEquals(72, result.totalCompleted)
    }
}
