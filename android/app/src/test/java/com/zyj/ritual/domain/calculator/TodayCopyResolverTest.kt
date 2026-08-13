package com.zyj.ritual.domain.calculator

import com.zyj.ritual.domain.calendar.PlanCalendar
import com.zyj.ritual.domain.model.Plan
import com.zyj.ritual.domain.model.PaperSession
import com.zyj.ritual.domain.model.PrimaryButtonTarget
import com.zyj.ritual.domain.model.RecordSource
import com.zyj.ritual.domain.model.TaskRecord
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/**
 * 今日文案测试（R31-R38）。
 */
class TodayCopyResolverTest {

    private val startDate = LocalDate.of(2026, 8, 5)
    private val plan = Plan(
        totalArticles = 42,
        startArticle = 13,
        completedBeforeStart = 12,
        planStartDate = startDate,
        daysPerArticle = 2,
    )
    private val cal = PlanCalendar.create(plan)

    private fun imported(): List<TaskRecord> =
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

    private fun record(art: Int, task: Int, date: LocalDate): TaskRecord {
        val instant = Instant.parse("${date}T10:00:00Z")
        return TaskRecord(
            id = TaskRecord.makeId(art, task),
            articleIndex = art,
            taskIndex = task,
            completedAt = instant,
            plannedDate = date,
            source = RecordSource.CHECKED,
        )
    }

    private fun resolve(records: List<TaskRecord>, today: LocalDate = startDate): com.zyj.ritual.domain.model.TodayCopyResult {
        val progress = ProgressCalculator.calculate(plan, records)
        val credit = CreditCalculator.calculate(plan, cal, records, today)
        return TodayCopyResolver.resolve(plan, cal, records, progress, credit, today)
    }

    // ——— R31-R34：标题 ———

    @Test
    fun `R32 当前篇0项=今天开始第N篇`() {
        val result = resolve(imported())
        assertEquals("今天开始第 13 篇", result.headline)
    }

    @Test
    fun `R33 当前篇3项=第N篇还剩M项`() {
        val records = imported() + (1..3).map { record(13, it, startDate) }
        val result = resolve(records)
        assertEquals("第 13 篇还剩 3 项", result.headline)
    }

    @Test
    fun `R32 第13篇完成后当前篇推进标题=今天开始第14篇`() {
        val records = imported() + (1..6).map { record(13, it, startDate) }
        val result = resolve(records)
        // 第 13 篇完成后，当前篇 = 14，0 项完成 → R32 触发
        assertEquals("今天开始第 14 篇", result.headline)
    }

    // ——— R35-R38：主按钮 ———

    @Test
    fun `R38 做了1项后主按钮指向第二项`() {
        val records = imported() + record(13, 1, startDate)
        val result = resolve(records)
        // 完成 1 项后，主按钮应指向下一项
        assertEquals("完成「完成课后习题」", result.primaryButtonText)
        assertTrue(result.primaryButtonTarget is PrimaryButtonTarget.CheckTask)
        val target = result.primaryButtonTarget as PrimaryButtonTarget.CheckTask
        assertEquals(13, target.articleIndex)
        assertEquals(2, target.taskIndex)
    }

    @Test
    fun `R36 当前篇0项时主按钮=开始第N篇`() {
        // 当前篇 13，0 项完成 → 主按钮「开始第 13 篇」
        val result = resolve(imported())
        assertEquals("开始第 13 篇", result.primaryButtonText)
        assertTrue(result.primaryButtonTarget is PrimaryButtonTarget.StartNextArticle)
    }

    @Test
    fun `R36 第13篇完成后主按钮=开始第14篇`() {
        val records = imported() + (1..6).map { record(13, it, startDate) }
        val result = resolve(records)
        // 当前篇推进到 14，0 项完成 → 「开始第 14 篇」
        assertEquals("开始第 14 篇", result.primaryButtonText)
        assertTrue(result.primaryButtonTarget is PrimaryButtonTarget.StartNextArticle)
    }

    @Test
    fun `R35 全部完成=查看完成页`() {
        val all = (1..42).flatMap { art ->
            (1..6).map { task ->
                if (art <= 12) TaskRecord(
                    id = TaskRecord.makeId(art, task),
                    articleIndex = art,
                    taskIndex = task,
                    completedAt = null,
                    plannedDate = null,
                    source = RecordSource.IMPORTED,
                )
                else record(art, task, startDate.plusDays((art - 13) * 2L + task / 4))
            }
        }
        val result = resolve(all)
        assertEquals("查看完成页", result.primaryButtonText)
        assertTrue(result.primaryButtonTarget is PrimaryButtonTarget.GoToDonePage)
    }

    @Test
    fun `R37 有缺额时主按钮指向最早缺口第一项`() {
        val today = startDate.plusDays(2)  // 8/7，应完成 6 项
        val records = imported()  // 一项都没做
        val result = resolve(records, today)
        // 应该指向 8/5（最早缺口）的第一项
        assertTrue(result.primaryButtonText.contains("通读全文"))
        assertTrue(result.primaryButtonTarget is PrimaryButtonTarget.CheckTask)
        val target = result.primaryButtonTarget as PrimaryButtonTarget.CheckTask
        assertEquals(13, target.articleIndex)
        assertEquals(1, target.taskIndex)
    }

    @Test
    fun `消化日标题与提示认账 不催进度`() {
        val paper = PaperSession(
            name = "2016 年卷",
            completedDate = startDate,
            createdAt = Instant.parse("2026-08-05T10:00:00Z"),
        )
        val records = imported()
        val progress = ProgressCalculator.calculate(plan, records)
        val credit = CreditCalculator.calculate(plan, cal, records, startDate)
        val result = TodayCopyResolver.resolve(plan, cal, records, progress, credit, startDate, paper)

        assertEquals("消化日 · 2016 年卷", result.headline)
        assertTrue(result.digestionNote!!.contains("剩余阅读任务豁免"))
    }
}
