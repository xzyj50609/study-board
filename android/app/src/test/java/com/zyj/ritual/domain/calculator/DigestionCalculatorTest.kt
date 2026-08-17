package com.zyj.ritual.domain.calculator

import com.zyj.ritual.domain.model.PaperSession
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/**
 * 整套卷 → 消化期 的纯函数测试。
 *
 * 用户拍板的规则：完成一套卷（4 阅读 + 完形 + 新题型 + 翻译 = 7 部分）后，
 * 完成日当天剩余任务豁免，之后 4 个完整自然日为消化日。
 */
class DigestionCalculatorTest {

    private val completed = LocalDate.of(2026, 8, 13)
    private val session = PaperSession(
        id = 1,
        name = "2016 年卷",
        completedDate = completed,
        createdAt = Instant.parse("2026-08-13T14:00:00Z"),
    )

    @Test
    fun `pausedDates 包含完成日当天和之后4个完整消化日`() {
        val paused = DigestionCalculator.pausedDates(listOf(session))
        assertEquals(5, paused.size)
        assertEquals(completed, paused.min())
        assertEquals(completed.plusDays(4), paused.max())
        assertTrue(completed.plusDays(1) in paused)
        assertFalse(completed.minusDays(1) in paused)
        assertFalse(completed.plusDays(5) in paused)
    }

    @Test
    fun `isPaused 覆盖窗口边界`() {
        assertTrue(DigestionCalculator.isPaused(completed, listOf(session)))
        assertTrue(DigestionCalculator.isPaused(completed.plusDays(4), listOf(session)))
        assertFalse(DigestionCalculator.isPaused(completed.plusDays(5), listOf(session)))
        assertFalse(DigestionCalculator.isPaused(completed.minusDays(1), listOf(session)))
    }

    @Test
    fun `多套卷的暂停日取并集`() {
        val another = session.copy(
            id = 2,
            name = "2017 年卷",
            completedDate = completed.plusDays(2),
        )
        val paused = DigestionCalculator.pausedDates(listOf(session, another))
        // 2016 窗口 8/13..8/17，2017 窗口 8/15..8/19，并集 8/13..8/19
        assertEquals(7, paused.size)
        assertEquals(completed, paused.min())
        assertEquals(completed.plusDays(6), paused.max())
    }

    @Test
    fun `sessionCovering 返回覆盖那天的卷`() {
        assertEquals(session, DigestionCalculator.sessionCovering(completed, listOf(session)))
        assertEquals(session, DigestionCalculator.sessionCovering(completed.plusDays(3), listOf(session)))
        assertNull(DigestionCalculator.sessionCovering(completed.plusDays(5), listOf(session)))
    }

    @Test
    fun `digestionDayIndex 完成日当天为0`() {
        assertEquals(0, DigestionCalculator.digestionDayIndex(session, completed))
        assertEquals(2, DigestionCalculator.digestionDayIndex(session, completed.plusDays(2)))
        assertEquals(4, DigestionCalculator.digestionDayIndex(session, completed.plusDays(4)))
    }

    // ———————————— 恢复日 ————————————

    @Test
    fun `resumeDate 是窗口后第一个正常学习日`() {
        // 8/13 写完，8/13..8/17 都空着，8/18 接着做
        assertEquals(
            completed.plusDays(5),
            DigestionCalculator.resumeDate(completed, listOf(session)),
        )
        assertEquals(
            completed.plusDays(5),
            DigestionCalculator.resumeDate(completed.plusDays(3), listOf(session)),
        )
    }

    @Test
    fun `不在空档里时 resumeDate 就是当天`() {
        val normal = completed.plusDays(10)
        assertEquals(normal, DigestionCalculator.resumeDate(normal, listOf(session)))
        assertEquals(normal, DigestionCalculator.resumeDate(normal, emptyList()))
    }

    @Test
    fun `空档里又写一套卷 恢复日一路往后推`() {
        val second = session.copy(
            id = 2,
            name = "2017 年卷",
            completedDate = completed.plusDays(2),
        )
        // 8/13..8/17 与 8/15..8/19 连成一片 → 8/20 才恢复
        assertEquals(
            completed.plusDays(7),
            DigestionCalculator.resumeDate(completed, listOf(session, second)),
        )
    }
}
