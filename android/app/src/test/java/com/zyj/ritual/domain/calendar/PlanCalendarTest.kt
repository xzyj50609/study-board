package com.zyj.ritual.domain.calendar

import com.zyj.ritual.domain.model.Plan
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

/**
 * 计划日历测试（R1-R5）。
 *
 * 种子数据：起点第 13 篇、8 月 5 日开始、2 天/篇、42 篇总量。
 * 这是原型里用的那套数据，算出来的数字和原型一致才算对。
 */
class PlanCalendarTest {

    private val startDate = LocalDate.of(2026, 8, 5)  // 周三
    private val plan = Plan(
        totalArticles = 42,
        startArticle = 13,
        completedBeforeStart = 12,
        planStartDate = startDate,
        daysPerArticle = 2,
    )
    private val cal = PlanCalendar.create(plan)

    // ——— R1：第 k 个计划日映射 ———

    @Test
    fun `R1 第0个计划日=第13篇相位1`() {
        val day = cal.getPlanDay(0)!!
        assertEquals(13, day.articleIndex)
        assertEquals(1, day.phase)
        assertEquals(listOf(1, 2, 3), day.taskIndices)
        assertEquals(startDate, day.date)
    }

    @Test
    fun `R1 第1个计划日=第13篇相位2`() {
        val day = cal.getPlanDay(1)!!
        assertEquals(13, day.articleIndex)
        assertEquals(2, day.phase)
        assertEquals(listOf(4, 5, 6), day.taskIndices)
        assertEquals(startDate.plusDays(1), day.date)
    }

    @Test
    fun `R1 第2个计划日=第14篇相位1`() {
        val day = cal.getPlanDay(2)!!
        assertEquals(14, day.articleIndex)
        assertEquals(1, day.phase)
        assertEquals(startDate.plusDays(2), day.date)
    }

    // ——— R2：2 天/篇的任务拆分 ———

    @Test
    fun `R2 相位1对应任务123`() {
        val day = cal.getPlanDay(0)!!
        assertEquals(listOf(1, 2, 3), day.taskIndices)
    }

    @Test
    fun `R2 相位2对应任务456`() {
        val day = cal.getPlanDay(1)!!
        assertEquals(listOf(4, 5, 6), day.taskIndices)
    }

    // ——— R3：自然日 = 计划日（无休息日）———

    @Test
    fun `R3 开始日是第0个计划日`() {
        assertEquals(0, cal.dateToPlanDayIndex(startDate))
    }

    @Test
    fun `R3 连续自然日连续计划日`() {
        for (i in 0..20) {
            val date = startDate.plusDays(i.toLong())
            assertEquals(i, cal.dateToPlanDayIndex(date))
        }
    }

    @Test
    fun `R3 计划开始前返回null`() {
        assertNull(cal.dateToPlanDayIndex(startDate.minusDays(1)))
    }

    // ——— R4：每篇 1 天和 3 天的拆分 ———

    @Test
    fun `R4 每篇1天时6项全在一天`() {
        val p = plan.copy(daysPerArticle = 1)
        val c = PlanCalendar.create(p)
        val day = c.getPlanDay(0)!!
        assertEquals(listOf(1, 2, 3, 4, 5, 6), day.taskIndices)
    }

    @Test
    fun `R4 每篇3天时每天2项`() {
        val p = plan.copy(daysPerArticle = 3)
        val c = PlanCalendar.create(p)
        assertEquals(listOf(1, 2), c.getPlanDay(0)!!.taskIndices)
        assertEquals(listOf(3, 4), c.getPlanDay(1)!!.taskIndices)
        assertEquals(listOf(5, 6), c.getPlanDay(2)!!.taskIndices)
    }

    // ——— R5：超出第 42 篇返回 null ———

    @Test
    fun `R5 最后一篇的最后一个计划日`() {
        // 第 42 篇的第二个计划日是最后一个
        val totalPlanDays = (42 - 12) * 2  // = 60
        val last = cal.getPlanDay(totalPlanDays - 1)!!
        assertEquals(42, last.articleIndex)
        assertEquals(2, last.phase)
    }

    @Test
    fun `R5 超出最后一篇返回null`() {
        val totalPlanDays = (42 - 12) * 2
        assertNull(cal.getPlanDay(totalPlanDays))
    }

    // ——— 辅助方法 ———

    @Test
    fun `elapsedPlanDays 今天=开始日时为0`() {
        assertEquals(0, cal.elapsedPlanDays(startDate))
    }

    @Test
    fun `elapsedPlanDays 第二天已过去1天`() {
        assertEquals(1, cal.elapsedPlanDays(startDate.plusDays(1)))
    }

    @Test
    fun `elapsedPlanDays 计划开始前为0`() {
        assertEquals(0, cal.elapsedPlanDays(startDate.minusDays(10)))
    }

    @Test
    fun `taskToPlanDayIndex 第13篇任务1=第0天`() {
        assertEquals(0, cal.taskToPlanDayIndex(13, 1))
    }

    @Test
    fun `taskToPlanDayIndex 第13篇任务4=第1天`() {
        assertEquals(1, cal.taskToPlanDayIndex(13, 4))
    }

    @Test
    fun `taskToPlanDayIndex 第14篇任务1=第2天`() {
        assertEquals(2, cal.taskToPlanDayIndex(14, 1))
    }

    @Test
    fun `基础计划完成日 R18`() {
        // 42-12+1 = 31 篇（从第 13 篇到第 42 篇共 30 篇？不对）
        // 从第 13 篇到第 42 篇 = 30 篇（42 - 13 + 1 = 30）
        // 30 篇 × 2 天 = 60 个计划日
        // 从 8/5 开始，第 0 天 = 8/5，第 59 天 = 8/5+59 = 10/3
        // 基础计划完成日 = planStartDate + totalPlanDays - 1
        val expected = startDate.plusDays(59)  // 30 篇 × 2 天 - 1 = 59
        assertEquals(expected, plan.baseCompletionDate)
    }
}
