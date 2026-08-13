package com.zyj.ritual.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

/**
 * 字体体系。
 *
 * 中文字体：走系统默认（国内机型系统中文字体与思源黑体同源，观感接近）。
 * 数字 / 英文标签：系统 sans-serif（设计稿是 Manrope，后续有条件再替换成字体文件）
 * 时间戳 / 编号：系统 monospace（设计稿是 IBM Plex Mono，同上）
 *
 * 为什么不直接打包字体文件：
 * - 第一版优先保证功能完整和编译通过
 * - 系统 sans-serif 的数字在一加手机上和 Manrope 差异不大
 * - 等要做 release 包时再决定是否子集化（子集化中文字体有个坑：改文案必须重跑子集，
 *   否则手机上静默缺字，见 DESIGN_AUDIT.md 第 8 节）
 *
 * 用法：
 * - 中文正文直接用 RitualTypography.body / bodySm 等
 * - 纯数字（进度百分比、篇数）用 copy(fontFamily = RitualFontFamilies.num)
 * - 时间戳、编号用 copy(fontFamily = RitualFontFamilies.mono)
 */

object RitualFontFamilies {
    // 占位：以后替换成 R.font.manrope_light / manrope_medium
    val num: FontFamily = FontFamily.SansSerif

    // 占位：以后替换成 R.font.ibm_plex_mono_regular / ibm_plex_mono_medium
    val mono: FontFamily = FontFamily.Monospace
}

/**
 * 主排版尺度。
 * 中文字重：Light=300、Normal=400、Medium=500，对应设计的 300/400/500。
 * 系统字体不一定有精确的 300 weight，但语义保持一致。
 *
 * 为什么是函数不是常量：这些 TextStyle 里烤着文字颜色，
 * 而文字颜色跟主题走（深色 = 暖砂，亮色 = 深墨）。
 */
fun ritualTypography(colors: RitualColorScheme) = Typography(
    displayLarge = TextStyle(
        fontSize = RitualTypeSize.display,
        fontWeight = FontWeight.Light,
        lineHeight = RitualTypeSize.display,
        fontFamily = RitualFontFamilies.num,
        color = colors.onBg,
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.None,
        ),
    ),
    headlineLarge = TextStyle(
        fontSize = RitualTypeSize.h1,
        fontWeight = FontWeight.Light,
        lineHeight = 38.sp,
        color = colors.onBg,
    ),
    headlineMedium = TextStyle(
        fontSize = RitualTypeSize.h2,
        fontWeight = FontWeight.Light,
        lineHeight = 31.sp,
        color = colors.onBg,
    ),
    headlineSmall = TextStyle(
        fontSize = RitualTypeSize.h3,
        fontWeight = FontWeight.Medium,
        lineHeight = 28.sp,
        color = colors.onBg,
    ),
    titleMedium = TextStyle(
        fontSize = RitualTypeSize.title,
        fontWeight = FontWeight.Medium,
        lineHeight = 24.sp,
        color = colors.onBg,
    ),
    bodyLarge = TextStyle(
        fontSize = RitualTypeSize.body,
        fontWeight = FontWeight.Normal,
        lineHeight = 20.sp,
        color = colors.onBg,
    ),
    bodyMedium = TextStyle(
        fontSize = RitualTypeSize.bodySm,
        fontWeight = FontWeight.Light,
        lineHeight = 20.sp,
        color = colors.onBg,
    ),
    bodySmall = TextStyle(
        fontSize = RitualTypeSize.caption,
        fontWeight = FontWeight.Light,
        lineHeight = 19.sp,
        color = colors.onBgMuted,
    ),
    labelSmall = TextStyle(
        fontSize = RitualTypeSize.label,
        fontWeight = FontWeight.Light,
        letterSpacing = 0.08.sp,
        color = colors.onBgMuted,
        fontFamily = RitualFontFamilies.num,
    ),
)

val LocalRitualTypography = staticCompositionLocalOf { ritualTypography(DarkRitualColors) }

/**
 * 组件里用的排版入口。写法和以前一样：`RitualTypography.bodyLarge`。
 * 只能在 @Composable 里读——里面烤着的文字颜色跟主题走。
 */
val RitualTypography: Typography
    @Composable @ReadOnlyComposable get() = LocalRitualTypography.current

// 给 Typography 补几个语义化别名，组件里写 .title 比 .titleMedium 好读
val androidx.compose.material3.Typography.title
    get() = titleMedium

/**
 * 不在 Material Typography 命名体系里的、跨组件复用的样式。
 */
object RitualTextStyles {

    /** 时间戳：10sp / Light / Mono / 三级文字色 */
    val stamp: TextStyle
        @Composable @ReadOnlyComposable get() = TextStyle(
            fontSize = RitualTypeSize.stamp,
            fontWeight = FontWeight.Light,
            fontFamily = RitualFontFamilies.mono,
            color = RitualColors.onBgFaint,
        )

    /** 日历日期数字：12.5sp / Normal */
    val calendarDay: TextStyle
        @Composable @ReadOnlyComposable get() = TextStyle(
            fontSize = RitualTypeSize.calendarDay,
            fontWeight = FontWeight.Normal,
            color = RitualColors.onBg,
        )

    /** 日历内「篇·第几天」小字：8sp / Light / Mono */
    val calendarTag: TextStyle
        @Composable @ReadOnlyComposable get() = TextStyle(
            fontSize = RitualTypeSize.calendarTag,
            fontWeight = FontWeight.Light,
            fontFamily = RitualFontFamilies.mono,
            color = RitualColors.onBgFaint,
        )

    /** 60sp 进度百分比 */
    val progressPercent: TextStyle
        @Composable @ReadOnlyComposable get() = RitualTypography.displayLarge
}
