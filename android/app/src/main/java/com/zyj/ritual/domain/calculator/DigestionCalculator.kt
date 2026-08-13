package com.zyj.ritual.domain.calculator

import com.zyj.ritual.domain.model.PaperSession
import java.time.LocalDate

/**
 * 消化期排程：整套卷 → 阅读计划暂停日集合。
 *
 * 规则（用户拍板，2026-08-13）：
 * - 完成日当天：剩余阅读任务豁免，也算暂停日；
 * - 之后 digestionDays 个完整自然日为消化日；
 * - 消化期内又完成一套卷：那套卷自己的窗口照常并入集合，
 *   并集天然实现"额外顺延 4 个完整自然日"；
 * - 暂停日不排计划、不产生欠账；但仍允许从文章详情主动提前学习
 *   （勾选不受影响，只是这天没有"必须做"的计划任务）。
 *
 * 纯函数：同一组 sessions 无论算多少次结果一致。
 */
object DigestionCalculator {

    /** 所有卷子占用的暂停日集合（完成日当天 + 之后 digestionDays 天） */
    fun pausedDates(sessions: List<PaperSession>): Set<LocalDate> =
        sessions.flatMap { s ->
            (0L..s.digestionDays.toLong()).map { s.completedDate.plusDays(it) }
        }.toSet()

    fun isPaused(date: LocalDate, sessions: List<PaperSession>): Boolean =
        sessions.any { covers(it, date) }

    /** 某天正被哪套卷的消化窗口覆盖（用于 UI 显示"消化日 · 2016 年卷"）；多套取最早完成的 */
    fun sessionCovering(date: LocalDate, sessions: List<PaperSession>): PaperSession? =
        sessions.filter { covers(it, date) }.minByOrNull { it.completedDate }

    /** 这套卷的消化窗口里，date 是第几天（完成日当天 = 0，首个完整消化日 = 1） */
    fun digestionDayIndex(session: PaperSession, date: LocalDate): Int =
        (date.toEpochDay() - session.completedDate.toEpochDay()).toInt()

    private fun covers(session: PaperSession, date: LocalDate): Boolean {
        val start = session.completedDate.toEpochDay()
        val end = start + session.digestionDays
        val d = date.toEpochDay()
        return d in start..end
    }
}
