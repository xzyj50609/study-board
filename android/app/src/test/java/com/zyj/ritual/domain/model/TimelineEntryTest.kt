package com.zyj.ritual.domain.model

import com.zyj.ritual.domain.vocab.VocabRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 历史页两条流水合并排序的关卡。
 *
 * 这里守的「会伪装成正常」的故障是第 6 条：
 * 背词记录在 2026-08-08 加时间戳之前写的、以及从备份 JSON 导入的，`createdAt` 都是 0。
 * 直接当时间戳格式化会显示「1.1 8:00」——这个**混在正常记录里根本看不出来**，
 * 只会让人觉得历史顺序有点怪。所以 `at` 必须显式是 null，让 UI 有机会不渲染时刻。
 */
class TimelineEntryTest {

    private val beijing = ZoneId.of("Asia/Shanghai")

    private fun at(date: String, hour: Int, minute: Int = 0): Instant =
        LocalDate.parse(date).atTime(hour, minute).atZone(beijing).toInstant()

    private fun event(date: String, hour: Int, name: String) = HistoryEvent(
        at = at(date, hour),
        type = HistoryEventType.CHECKED,
        articleIndex = 3,
        taskIndex = 1,
        taskName = name,
    )

    private fun vocab(date: String, words: Int, kind: String = "new", createdAt: Long = 0) =
        VocabRecord(date = date, words = words, kind = kind, createdAt = createdAt)

    @Test
    fun `两条流水按日期倒序 最近的在最上面`() {
        val merged = TimelineEntry.merge(
            history = listOf(event("2026-08-05", 21, "精读")),
            vocabRecords = listOf(vocab("2026-08-07", 20, createdAt = at("2026-08-07", 19).toEpochMilli())),
        )
        assertEquals(2, merged.size)
        assertEquals(LocalDate.parse("2026-08-07"), merged[0].date)
        assertEquals(LocalDate.parse("2026-08-05"), merged[1].date)
    }

    @Test
    fun `同一天内按时刻倒序`() {
        val merged = TimelineEntry.merge(
            history = listOf(event("2026-08-07", 21, "精读")),
            vocabRecords = listOf(
                vocab("2026-08-07", 20, createdAt = at("2026-08-07", 19, 55).toEpochMilli()),
                vocab("2026-08-07", 45, "backlog", createdAt = at("2026-08-07", 20, 2).toEpochMilli()),
            ),
        )
        assertEquals(3, merged.size)
        // 21:00 精读 → 20:02 复习 → 19:55 新词
        assertEquals(21, merged[0].at!!.atZone(beijing).hour)
        assertEquals(20, merged[1].at!!.atZone(beijing).hour)
        assertEquals(19, merged[2].at!!.atZone(beijing).hour)
    }

    @Test
    fun `没有时刻的记录 at 是 null 不是 1970`() {
        val entry = TimelineEntry.Vocab(vocab("2026-08-01", 20, createdAt = 0))
        assertNull(
            "createdAt 是 0 时 at 必须是 null。给一个 Instant 的话，" +
                "历史页会打出 1970 年，或者更糟——只显示月日，混在正常记录里看不出来",
            entry.at,
        )
        // 日期照样是对的，这条记录不是没用，只是不知道几点
        assertEquals(LocalDate.parse("2026-08-01"), entry.date)
    }

    @Test
    fun `同一天里 没有时刻的沉到最底下`() {
        val merged = TimelineEntry.merge(
            history = listOf(event("2026-08-07", 9, "泛读")),
            vocabRecords = listOf(
                vocab("2026-08-07", 20, createdAt = 0),                                    // 老记录
                vocab("2026-08-07", 45, "backlog", createdAt = at("2026-08-07", 20).toEpochMilli()),
            ),
        )
        assertEquals(3, merged.size)
        // 20:00 复习 → 09:00 泛读 → 无时刻的新词
        assertEquals(20, merged[0].at!!.atZone(beijing).hour)
        assertEquals(9, merged[1].at!!.atZone(beijing).hour)
        assertNull("没有时刻的那条应该沉到当天最底下", merged[2].at)
    }

    @Test
    fun `导入事件不进历史`() {
        val merged = TimelineEntry.merge(
            history = listOf(
                HistoryEvent(at = at("2026-08-07", 10), type = HistoryEventType.IMPORT, note = "首次设置"),
                event("2026-08-07", 9, "泛读"),
            ),
            vocabRecords = emptyList(),
        )
        assertEquals("IMPORT 是首次设置灌数据，不是用户干的事，不该出现在历史里", 1, merged.size)
        assertTrue(merged[0] is TimelineEntry.Article)
    }

    @Test
    fun `新词和复习分得开`() {
        val merged = TimelineEntry.merge(
            history = emptyList(),
            vocabRecords = listOf(
                vocab("2026-08-07", 20, "new", createdAt = at("2026-08-07", 19).toEpochMilli()),
                vocab("2026-08-07", 45, "backlog", createdAt = at("2026-08-07", 20).toEpochMilli()),
            ),
        )
        val review = merged[0] as TimelineEntry.Vocab
        val new = merged[1] as TimelineEntry.Vocab
        assertTrue("kind=backlog 就是复习", review.isReview)
        assertTrue("kind=new 不是复习", !new.isReview)
    }

    @Test
    fun `两边都空的时候返回空表 不炸`() {
        assertEquals(0, TimelineEntry.merge(emptyList(), emptyList()).size)
    }

    @Test
    fun `同一天多条背词记录一条都不能少`() {
        // 真实备份里 8-01 有 6 条。历史页不许去重或者合并
        val records = (1..6).map { vocab("2026-08-01", it * 5, createdAt = 0) }
        val merged = TimelineEntry.merge(emptyList(), records)
        assertEquals("同一天 6 条被吃掉了", 6, merged.size)
    }
}
