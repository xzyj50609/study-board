package com.zyj.ritual.ui.theme

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 间距、圆角、尺寸。
 * 全部从 design-tokens.json 生成。
 */
object RitualSpace {
    val unit = 4.dp
    val screenPadding = 24.dp
    val cardPadding = 17.dp
    val sectionGap = 22.dp
    val listGap = 10.dp
    val calendarGap = 4.dp
    val statusBarPad = 30.dp
    val navBarHeight = 76.dp
}

object RitualRadius {
    val card = 14.dp
    val cardLg = 16.dp
    val sheet = 22.dp
    val dialog = 20.dp
    val calendarCell = 12.dp
    val pill = 24.dp
    val button = 30.dp
    val dot = 999.dp
}

object RitualSize {
    val tapMin = 48.dp
    val checkDot = 22.dp
    val checkDotSm = 18.dp
    val calendarCell = 54.dp
    val calendarCellNarrow = 48.dp  // 360dp 宽机型
    val ring = 132.dp
    val ringStroke = 13.dp
    val progressBar = 8.dp
    val articleBar = 5.dp
    val iconButton = 34.dp
    val primaryButtonH = 52.dp
}

/**
 * 字号（sp）。
 * 中文字重走系统字体的 300/400/500；
 * 数字/标签用 Manrope，时间戳/编号用 IBM Plex Mono。
 */
object RitualTypeSize {
    val display = 60.sp     // 进度页百分比
    val h1 = 26.sp          // 首次设置标题
    val h2 = 22.sp          // 首页 headline
    val h3 = 21.sp          // 月份标题
    val title = 16.sp       // 面板/弹窗标题
    val body = 14.sp        // 任务名
    val bodySm = 13.sp      // 面板任务名 / 设置项
    val caption = 11.5.sp   // 说明文字
    val label = 10.5.sp     // 分组标签（英文大写）
    val stamp = 10.sp       // 时间戳（mono）
    val calendarDay = 12.5.sp
    val calendarTag = 8.sp  // 日历内 篇·第几天
}
