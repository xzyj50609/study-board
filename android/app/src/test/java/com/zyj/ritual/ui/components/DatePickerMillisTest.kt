package com.zyj.ritual.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.util.TimeZone

/**
 * 日历弹窗的日期换算。
 *
 * 为什么值得单独立一个关卡：Material 的 DatePicker 内部一律按 UTC 把毫秒
 * 解释成"哪一天"。这边只要用本机时区换算，在西八区（本机开发环境就是）
 * 会整体差一天——选 8 月 13 号，存进去变成 8 月 12 号。
 *
 * 这正是"故障伪装成正常"：屏幕上不会报任何错，日历上也照样画得出来，
 * 只是那套卷的空档从错的一天开始，用户得自己去数才发现。
 */
class DatePickerMillisTest {

    @Test
    fun `来回换算不掉一天`() {
        val dates = listOf(
            LocalDate.of(2026, 8, 13),
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 12, 31),
            LocalDate.of(2027, 2, 28),
        )
        dates.forEach { d ->
            assertEquals(d, DatePickerMillis.toLocalDate(DatePickerMillis.toMillis(d)))
        }
    }

    @Test
    fun `本机时区换到西八区 结果照样不变`() {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
            val d = LocalDate.of(2026, 8, 13)
            assertEquals(
                "本机时区一换日期就差一天，说明换算没走 UTC",
                d,
                DatePickerMillis.toLocalDate(DatePickerMillis.toMillis(d)),
            )

            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Kiritimati"))  // UTC+14
            assertEquals(d, DatePickerMillis.toLocalDate(DatePickerMillis.toMillis(d)))
        } finally {
            TimeZone.setDefault(original)
        }
    }

    @Test
    fun `换算出来的毫秒是当天 UTC 零点`() {
        // 2026-08-13T00:00:00Z
        assertEquals(
            1786579200000L,
            DatePickerMillis.toMillis(LocalDate.of(2026, 8, 13)),
        )
    }
}
