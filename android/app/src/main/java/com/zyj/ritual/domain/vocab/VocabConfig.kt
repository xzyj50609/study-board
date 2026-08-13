package com.zyj.ritual.domain.vocab

/**
 * 背单词设置（对应 JS model.js 的 DEFAULTS + settings）。
 * 纯数据，不依赖 Android。
 */
data class VocabConfig(
    val bookName: String = "2027考研真题核心词汇",
    /** 显式开始日期。"" = 用最早一条记录反推 */
    val startDate: String = "",
    val totalWords: Int = 1883,
    /** 起点存量 = 用本看板之前已背的词数 */
    val initialDone: Int = 380,
    val dailyWords: Int = 20,
    val examDate: String = "2026-12-19",
    /** 分段日速：从某天起每天改背 N 个。过去的天保留老口径 */
    val rateChanges: List<RateChange> = emptyList(),
    val alarmLateDays: Int = 3,
    // ── 复习 ──
    val reviewMode: String = "daily",
    /** 今天要复习多少（照 APP 首页那个数填） */
    val reviewDue: Int = 0,
    /** reviewDue 是哪天填的。不等于今天 = 今天还没校准 */
    val reviewDueDate: String = "",
    /** 累计复习每多少个算一档 */
    val reviewStep: Int = 500,
    /** 历史积压复习词总量（老模式） */
    val backlogTotal: Int = 288,
)

data class RateChange(
    /** 生效日期 "YYYY-MM-DD"（含当天） */
    val from: String,
    val dailyWords: Int,
)
