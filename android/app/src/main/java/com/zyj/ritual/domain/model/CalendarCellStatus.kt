package com.zyj.ritual.domain.model

/**
 * 日历日格的 9 种状态。
 * 判定规则见 R26-R30。
 *
 * 注意：状态只是外观语义，不含"能不能点""能不能勾"的交互判断。
 * 交互判断在 DayStatusResolver 的另一个方法里。
 */
enum class CalendarCellStatus {
    BEFORE_START,       // 计划开始前（灰蓝点，不可勾）R26
    TODAY,              // 今天（外环 + 当日 0 项完成）R28
    TODAY_PARTIAL,      // 今天（部分完成，1-2 项）R28
    TODAY_COMPLETE,     // 今天（3 项全完成）R28
    PAST_ON_PLAN,       // 过去按计划完成 R29
    PAST_PARTIAL,       // 过去部分完成 R29
    PAST_BACKFILL,      // 过去后续补完成（空心蓝环）R29
    PAST_ADVANCED,      // 过去提前完成（金环）R29
    PAST_COVERED,       // 过去缺额但被额度覆盖（淡金）R29
    PAST_DEFICIT,       // 过去缺额待补（暗铜 + 格底）R29
    FUTURE_PLANNED,     // 未来计划中 R30
    FUTURE_ADVANCED,    // 未来已提前完成（金环）R30
    DIGESTION,          // 消化日：整套卷换来的休整日，不排计划、不算欠账
}
