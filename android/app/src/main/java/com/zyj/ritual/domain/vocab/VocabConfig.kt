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
    /**
     * 新计划的起点已背量（可空）。
     *
     * 非空时，「计划线 / 计划完成日 / 早晚天数」不再从 initialDone + 最早记录日
     * 起算，而是从**第一条 rateChange 的生效日**起、以 planStartDone 为起点量起算。
     * 用途：把「历史总进度」和「当前计划」解耦——
     * 例：7 月底用 20/天零星背过一阵（历史事实，doneWords 照旧全算），
     * 8 月 2 日起换成 40/天的新计划（rateChanges = [8/2→40]），
     * 那 8/2 前的日子不该再产生欠账或提前量，计划完成日也应从 8/2 起算。
     *
     * 取值 = initialDone + 新计划生效日之前的新词记录总和，由外部算好传入，
     * 算法层不回推（回推会跟 rateChanges 的语义搅在一起）。
     *
     * null = 旧算法（initialDone + 最早记录日），老备份/老数据行为不变。
     */
    val planStartDone: Int? = null,
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
