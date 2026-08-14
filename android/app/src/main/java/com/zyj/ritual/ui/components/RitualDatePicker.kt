package com.zyj.ritual.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zyj.ritual.ui.theme.RitualColors
import com.zyj.ritual.ui.theme.RitualFontFamilies
import com.zyj.ritual.ui.theme.RitualRadius
import com.zyj.ritual.ui.theme.RitualTypography
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * 日期换算：LocalDate ↔ DatePicker 用的毫秒。
 *
 * ⚠️ 必须走 UTC，不能用系统默认时区。
 * Material 的 DatePicker 内部一律按 UTC 把毫秒解释成"哪一天"；
 * 这边要是用本机时区换算，本机在西八区时会整体差一天——
 * 选 8 月 13 号存进去变成 8 月 12 号。而屏幕上什么都不会报错，
 * 只是那条记录"莫名其妙早了一天"，是最难自己发现的一类错。
 */
object DatePickerMillis {
    fun toMillis(date: LocalDate): Long =
        date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    fun toLocalDate(millis: Long): LocalDate =
        Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
}

/** "2026-08-13" → "2026 年 8 月 13 日" */
fun formatDateCn(date: LocalDate): String =
    "${date.year} 年 ${date.monthValue} 月 ${date.dayOfMonth} 日"

/**
 * 一行「点了弹日历」的日期字段。
 *
 * 存在的理由：上一版让用户拿键盘手打 "2026-08-02"，还得自己敲横线。
 * 日期是这个 App 里最不该用键盘输入的东西——打错一个字符，
 * 整条计划线就悄悄错位，而屏幕上看着完全正常。
 */
@Composable
fun DateFieldRow(
    label: String,
    date: LocalDate,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(RitualRadius.card))
            .background(RitualColors.surfaceLow)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = RitualTypography.bodyMedium.copy(color = RitualColors.onBgMuted),
        )
        Spacer(Modifier.weight(1f))
        Text(
            formatDateCn(date),
            style = RitualTypography.bodyLarge.copy(
                color = RitualColors.onBg,
                fontFamily = RitualFontFamilies.num,
                fontWeight = FontWeight.Medium,
            ),
        )
        Text(
            " ▾",
            style = RitualTypography.bodyMedium.copy(color = RitualColors.accentInk),
        )
    }
    if (hint != null) {
        Text(
            hint,
            style = RitualTypography.bodySmall.copy(color = RitualColors.onBgMuted),
            modifier = Modifier.padding(top = 6.dp, start = 4.dp),
        )
    }
}

/**
 * 日历弹窗。用 Material 的日历骨架，配色全部换成本 App 的，
 * 免得在一片米白纸感里突然冒出一块紫色。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RitualDatePickerDialog(
    initialDate: LocalDate,
    title: String,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = DatePickerMillis.toMillis(initialDate)
    )

    val colors = DatePickerDefaults.colors(
        containerColor = RitualColors.surface,
        titleContentColor = RitualColors.onBg,
        headlineContentColor = RitualColors.onBg,
        weekdayContentColor = RitualColors.onBgMuted,
        subheadContentColor = RitualColors.onBgMuted,
        navigationContentColor = RitualColors.onBg,
        yearContentColor = RitualColors.onBg,
        currentYearContentColor = RitualColors.accentInk,
        selectedYearContentColor = RitualColors.onAccent,
        selectedYearContainerColor = RitualColors.accentInk,
        dayContentColor = RitualColors.onBg,
        selectedDayContentColor = RitualColors.onAccent,
        selectedDayContainerColor = RitualColors.accentInk,
        todayContentColor = RitualColors.accentInk,
        todayDateBorderColor = RitualColors.accentInk,
        dividerColor = RitualColors.divider,
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        colors = colors,
        shape = RoundedCornerShape(RitualRadius.dialog),
        confirmButton = {
            TextButton(
                onClick = {
                    val millis = state.selectedDateMillis
                    if (millis != null) onConfirm(DatePickerMillis.toLocalDate(millis))
                    else onDismiss()
                }
            ) {
                Text(
                    "选好了",
                    style = RitualTypography.bodyMedium.copy(
                        color = RitualColors.accentInk,
                        fontWeight = FontWeight.Medium,
                    ),
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    "取消",
                    style = RitualTypography.bodyMedium.copy(color = RitualColors.onBgMuted),
                )
            }
        },
    ) {
        DatePicker(
            state = state,
            title = {
                Text(
                    title,
                    style = RitualTypography.titleMedium.copy(color = RitualColors.onBg),
                    modifier = Modifier.padding(start = 24.dp, top = 16.dp),
                )
            },
            colors = colors,
        )
        Spacer(Modifier.height(4.dp))
    }
}
