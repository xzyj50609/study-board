package com.zyj.ritual.domain.model

import com.zyj.ritual.data.repository.TodayState
import com.zyj.ritual.data.repository.VocabAggregateState
import java.time.LocalDate

/**
 * 跨科目今日聚合页状态。
 *
 * 聚合：
 * 1. articleState: 读文章看板状态（包含 Plan, Progress, Credit, Copy, Records 等）
 * 2. vocabState: 背单词看板状态（包含 VocabConfig, VocabState, VocabCalendarResult 等）
 * 3. today: 当前日期 (UTC+8)
 */
data class CombinedTodayState(
    val articleState: TodayState?,
    val vocabState: VocabAggregateState?,
    val today: LocalDate,
)
