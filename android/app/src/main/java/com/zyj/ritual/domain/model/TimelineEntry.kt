package com.zyj.ritual.domain.model

import com.zyj.ritual.domain.vocab.VocabRecord
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 历史页的一条记录。把「读文章」和「背单词」两条互不相干的流水合成一条时间线。
 *
 * 为什么要一个中间类型：两边的时间精度不一样。读文章的 `HistoryEvent` 有精确到
 * 分钟的 `at`，背词的 `VocabRecord` 只有日期（2026-08-08 之前写的记录、
 * 以及从备份 JSON 导入的记录，`createdAt` 都是 0）。直接把 0 当时间戳格式化
 * 会显示 1970 年——那还算好的，一眼就看出来坏了；真正危险的是只渲染「01-01」
 * 那种，它会安安静静混在正常记录里，谁也发现不了。
 *
 * 所以这里把「没有时刻」显式表达成 `at == null`，UI 层照着它决定渲不渲染时间。
 */
sealed interface TimelineEntry {
    /** 精确时刻。null = 只知道是哪天，不知道几点 */
    val at: Instant?

    /** 这条记录归到哪一天（北京时区） */
    val date: LocalDate

    data class Article(val event: HistoryEvent) : TimelineEntry {
        override val at: Instant get() = event.at
        override val date: LocalDate
            get() = event.at.atZone(BEIJING).toLocalDate()
    }

    data class Vocab(val record: VocabRecord) : TimelineEntry {
        override val at: Instant?
            get() = if (record.createdAt > 0) Instant.ofEpochMilli(record.createdAt) else null
        override val date: LocalDate get() = LocalDate.parse(record.date)

        /** true = 复习，false = 新词 */
        val isReview: Boolean get() = record.kind == "backlog"
    }

    companion object {
        val BEIJING: ZoneId = ZoneId.of("Asia/Shanghai")

        /**
         * 合并两条流水，按时间倒序。
         *
         * 排序规则（纯函数，有单测钉着）：
         * 1. 先按日期倒序——最近的在最上面。
         * 2. 同一天内，**有时刻的排在无时刻的前面**，各自再按时刻倒序。
         *    无时刻的沉到当天底部，而不是被硬塞进某个位置假装它知道自己几点。
         * 3. IMPORT 事件不进历史（跟原来进度页底部那块的口径一致，
         *    那是首次设置灌数据，不是用户干的事）。
         */
        fun merge(
            history: List<HistoryEvent>,
            vocabRecords: List<VocabRecord>,
        ): List<TimelineEntry> {
            val entries: List<TimelineEntry> =
                history.asSequence()
                    .filter { it.type != HistoryEventType.IMPORT }
                    .map { Article(it) }
                    .toList() +
                    vocabRecords.map { Vocab(it) }

            return entries.sortedWith(
                compareByDescending<TimelineEntry> { it.date }
                    // 有时刻的优先（false < true，所以用 "at == null" 升序）
                    .thenBy { it.at == null }
                    .thenByDescending { it.at ?: Instant.EPOCH }
            )
        }
    }
}
