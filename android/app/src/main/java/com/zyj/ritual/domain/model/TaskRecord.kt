package com.zyj.ritual.domain.model

import java.time.Instant
import java.time.LocalDate

/**
 * 一条完成记录 = 一个小任务的一次完成事件。
 *
 * id 规则："13-4" = 第 13 篇第 4 项，天然唯一。
 * 同一项可以被撤销后再勾，每次勾都会重新生成一条记录（新时间戳）。
 *
 * source 区分来源，是判定"是否进历史列表""是否参与额度计算"的依据。
 * IMPORTED 是首次设置时批量写入的起点篇数（没有真实完成时间），
 * 只计入整体进度和完整篇数，不进历史、不算额度（R15）。
 */
data class TaskRecord(
    val id: String,
    val articleIndex: Int,      // 1-based
    val taskIndex: Int,         // 1-based, 1..6
    val completedAt: Instant?,  // 实际完成时刻；IMPORTED 为 null
    val plannedDate: LocalDate?, // 完成那一刻这一项的计划日期快照
    val source: RecordSource,
) {
    init {
        require(id == "${articleIndex}-${taskIndex}") {
            "Record id must match article-task format, got $id (expected ${articleIndex}-${taskIndex})"
        }
        require(taskIndex in 1..6) { "taskIndex must be 1..6, got $taskIndex" }
    }

    companion object {
        fun makeId(articleIndex: Int, taskIndex: Int) = "$articleIndex-$taskIndex"
    }
}

enum class RecordSource {
    CHECKED,    // 当天勾的
    BACKFILL,   // 补打卡（过去某天的）
    IMPORTED,   // 首次设置带入的起点篇
    ADVANCED,   // 提前完成（未来日期的）
}
