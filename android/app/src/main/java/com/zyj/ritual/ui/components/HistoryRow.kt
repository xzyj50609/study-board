package com.zyj.ritual.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zyj.ritual.domain.model.HistoryEventType
import com.zyj.ritual.domain.model.TimelineEntry
import com.zyj.ritual.ui.theme.RitualColors
import com.zyj.ritual.ui.theme.RitualRadius
import com.zyj.ritual.ui.theme.RitualSpace
import com.zyj.ritual.ui.theme.RitualTextStyles
import com.zyj.ritual.ui.theme.RitualTypography
import java.time.Instant
import java.time.ZoneId

/**
 * 历史流水的一行。原来是 ProgressScreen 里的 private 函数，
 * 历史挪到二级页之后两处都要用，提出来改 public。
 */
@Composable
fun HistoryRow(
    title: String,
    time: String,
    badge: String,
    badgeColor: Color = RitualColors.onBg,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(RitualRadius.card))
            .background(RitualColors.surfaceLow)
            .padding(RitualSpace.cardPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = RitualTypography.bodyMedium)
            // 没有时刻的记录这里是空串，Text 会塌成 0 高，不会留一行空白
            if (time.isNotEmpty()) {
                Text(time, style = RitualTextStyles.stamp)
            }
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(RitualColors.surfaceSel)
                .padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Text(
                badge,
                style = RitualTypography.bodySmall.copy(
                    fontWeight = FontWeight.Medium,
                    color = badgeColor,
                ),
            )
        }
    }
}

/** 事件类型 → 中文标签。用枚举而不是字符串，改名了编译器会拦住。 */
fun HistoryEventType.label(): String = when (this) {
    HistoryEventType.CHECKED -> "完成"
    HistoryEventType.BACKFILL -> "补记"
    HistoryEventType.ADVANCED -> "提前"
    HistoryEventType.UNDO -> "撤销"
    HistoryEventType.UNDO_ARTICLE -> "撤销整篇"
    HistoryEventType.RESCHEDULE -> "排期"
    HistoryEventType.IMPORT -> "导入"
    HistoryEventType.PAPER_SESSION -> "整套卷"
    HistoryEventType.PAPER_SESSION_UNDO -> "撤销整套卷"
}

/**
 * 时间戳 → "8.7 21:14"。
 *
 * ⚠️ null 返回空串，**不要返回 "1970.1.1 8:00" 也不要返回一个编出来的时刻**。
 * 没有时刻这件事本身要看得见（那一行就是没有第二行小字），
 * 编一个出来会让它跟正常记录长得一模一样。
 */
fun formatStamp(instant: Instant?): String {
    if (instant == null) return ""
    val zdt = instant.atZone(ZoneId.of("Asia/Shanghai"))
    return "${zdt.monthValue}.${zdt.dayOfMonth} ${zdt.hour}:${String.format("%02d", zdt.minute)}"
}

/** 一条时间线记录 → 标题文案 */
fun TimelineEntry.rowTitle(): String = when (this) {
    is TimelineEntry.Article -> {
        val e = event
        when {
            e.articleIndex != null && e.taskName != null -> "第 ${e.articleIndex} 篇 · ${e.taskName}"
            e.note != null -> e.note.orEmpty()
            else -> "学习记录"
        }
    }
    is TimelineEntry.Vocab ->
        if (isReview) "复习 ${record.words} 个" else "新背 ${record.words} 个"
}

/** 一条时间线记录 → 右侧徽章文案 */
fun TimelineEntry.rowBadge(): String = when (this) {
    is TimelineEntry.Article -> event.type.label()
    is TimelineEntry.Vocab -> if (isReview) "复习" else "新词"
}
