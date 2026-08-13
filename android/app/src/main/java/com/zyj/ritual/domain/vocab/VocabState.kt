package com.zyj.ritual.domain.vocab

/**
 * computeState 的返回值——核心状态快照。
 * 所有字段对应 JS computeState 返回的同名字段。
 */
data class VocabState(
    val notStarted: Boolean,
    /** 计划起算日 "YYYY-MM-DD" */
    val startDate: String,
    /** 已过去的天数（含今天；开始日在未来时为 0） */
    val elapsedDays: Int,
    /** 真实已背词数 = initialDone + 新词记录累计，夹到 totalWords */
    val doneWords: Int,
    /** 计划线 = initialDone + 从起算日到今天逐日累加额度，夹到 totalWords */
    val dueWords: Int,
    /** 今天计划背几个（分段日速下可变） */
    val todayQuota: Int,
    /** 提前天数（正=提前，负=落后） */
    val aheadDays: Int,
    /** 是否报警（落后 ≥ alarmLateDays 天） */
    val isAlarm: Boolean,
    /** 计划完成日 */
    val planFinishDate: String,
    /** 预计完成日 */
    val projFinishDate: String,
    /** 剩余未背词数 */
    val remainWords: Int,
    /** 是否已背完 */
    val finished: Boolean,
    /** 积压剩余（backlog 模式） */
    val backlogLeft: Int,
    // ── 复习 ──
    /** 累计复习总量 */
    val reviewTotal: Int,
    /** 今天复习了多少 */
    val todayReview: Int,
    /** 今天的复习目标（null = 今天没校准） */
    val reviewDueToday: Int?,
    /** 今天还差多少复习（null = 没校准） */
    val reviewLeftToday: Int?,
    /** 复习档长（洗过脏数据的） */
    val reviewStep: Int,
    /** 当前处于第几档 */
    val reviewLevel: Int,
    /** 当前档位内的进度 */
    val reviewIntoLevel: Int,
    /** 距下一档还差多少 */
    val reviewToNext: Int,
    /** 距考试还有多少天 */
    val daysToExam: Int,
    /** 今天背了多少新词 */
    val todayWords: Int,
)
