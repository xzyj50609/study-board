package com.zyj.ritual.domain.model

import java.time.Instant

/**
 * 历史事件流。
 *
 * 每一次"勾上""撤销""补打卡""重排"都产生一条。
 * 记录被删除了，但"我撤销过"这件事要留在历史里（7.3 节设计要求）。
 */
data class HistoryEvent(
    val id: Long = 0,
    val at: Instant,
    val type: HistoryEventType,
    val articleIndex: Int? = null,
    val taskIndex: Int? = null,
    val taskName: String? = null,
    val note: String? = null,
)

enum class HistoryEventType {
    CHECKED,       // 当天勾选
    ADVANCED,      // 提前完成
    BACKFILL,      // 补打卡
    UNDO,          // 撤销单项
    UNDO_ARTICLE,  // 撤销整篇
    RESCHEDULE,    // 重新排期
    IMPORT,        // 首次设置导入
    PAPER_SESSION,       // 登记整套卷（进入消化期）
    PAPER_SESSION_UNDO,  // 撤销整套卷登记
}
