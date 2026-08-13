package com.zyj.ritual.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 夜读 / Ritual 配色。
 *
 * 一套角色名（bg / onBg / accentInk），两份取值（深色 / 亮色）。
 * 命名约定：颜色角色名，不是色值名（不叫 darkBlue / gold）。
 * 禁止在组件文件里写裸色值。
 *
 * 组件里的写法不变，仍然是 `RitualColors.bg`——它现在读的是
 * [LocalRitualColors]，具体哪一份由 [RitualTheme] 决定。
 */
@Immutable
data class RitualColorScheme(
    val bg: Color,
    val surface: Color,
    val surfaceLow: Color,
    val surfaceSel: Color,
    val divider: Color,
    val outline: Color,
    val onBg: Color,
    val onBgMuted: Color,
    val onBgFaint: Color,
    val accentInk: Color,
    /**
     * 语义是「你赚到的」：提前做掉的、剩余额度、领先的天数。
     *
     * ⚠️ **名字里的 gold 已经名不副实**——2026-08-07 第三轮起亮色版这里是松绿 `#0E7C66`，
     * 不是金色。改成绿是因为原来的橙金和「缺口」的红色相只差 28°，
     * 一个是夸你一个是骂你却长得像一族。深色版仍是暖金。
     * 暂时没改 token 名（全工程 26 处引用），要改就单独一轮统一改，别混在配色里。
     */
    val accentGold: Color,
    /**
     * 复习。**目前还没有任何页面用它**——背单词看板（Phase 2）才会用到。
     * 提前放进来是因为 2026-08-07 定的语义色表要求「全平台一套，新看板不许自创」，
     * 写进 token 才拦得住。
     */
    val accentReview: Color,
    val warn: Color,
    val warnText: Color,
    val onAccent: Color,
    /** 亮色版为 true。系统栏图标颜色、以及以后要按明暗分叉的地方读它。 */
    val isLight: Boolean,
)

/** 深色（原版，色值一个都没动）。 */
val DarkRitualColors = RitualColorScheme(
    bg = Color(0xFF14161C),
    surface = Color(0xFF1C1F27),

    // 半透明叠加层（统一在 bg 上计算，方便以后换底）
    surfaceLow = Color(0x0CE9E3D6),      // 3.5% 暖砂 = 卡片填充
    surfaceSel = Color(0x0FE9E3D6),      // 6% 暖砂 = 选中/状态标签底
    divider = Color(0x17E9E3D6),         // 9% 暖砂 = 分割线
    outline = Color(0x24E9E3D6),         // 14% 暖砂 = 描边

    onBg = Color(0xFFE9E3D6),            // 主文字 暖砂
    onBgMuted = Color(0x80E9E3D6),       // 次要文字 50%
    onBgFaint = Color(0x4DE9E3D6),       // 三级文字 30%

    accentInk = Color(0xFF7FB4C9),       // 月光蓝 — 已完成 / 进度弧 / 当前任务
    accentGold = Color(0xFFD3A46C),      // 暖金 — 提前完成 / 额度 / 今天标记
    accentReview = Color(0xFFA79CD2),    // 闷紫 — 复习（Phase 2 才用）

    warn = Color(0xFFC47446),            // 暗铜 — 缺额边框
    warnText = Color(0xFFE9B489),        // 缺额文字与按钮底

    onAccent = Color(0xFF14161C),        // 落在浅色按钮/实心点上的文字
    isLight = false,
)

/**
 * 亮色（出货）。
 *
 * ### 2026-08-07 第三轮重做，先看这段再改色
 *
 * 前两版都被用户判「丑」，第二版的原话是「颜色太深了…蓝色和金色都感觉很重，
 * 是不是因为背景是暖黄？」——**这个猜测基本是对的**，机制值得记下来：
 *
 * 暖纸底 `#EFEBE2` 本身很亮。要让强调色压在它上面达到 4.5 : 1，强调色就只能往深里压，
 * 于是每个颜色都又深又闷。但那条规矩**只对当小字用的颜色成立**。
 * 上一版的错在于：日历格子是整块实心填充再压白字，那是全屏面积最大的东西，
 * 却被按「文字色」的标准做成了饱和深色 —— 重量全是这么来的，跟色相没关系。
 *
 * 所以这一版换了做法：
 * 1. **底换成近白** `#F7F7F6`。淡色块压在暖黄上会发浑，压在近白上才干净。
 * 2. **大面积一律淡色块 + 深墨字**，不再有「实心饱和块 + 白字」。
 *    见 `VocabCalendarCellView`：格子填的是 `accentInk` 的 22% 淡淡一层，字仍是 `onBg`。
 * 3. **饱和色只留给小面积**：圆点、细条、进度环、描边。面积小就不压秤。
 *
 * ⚠️ 因此 `onAccent`（压在实心强调色上的白字）现在几乎没有用武之地了。
 * 它还留着是给进度环那种小面积实心块用的。**别因为它存在就又去画实心大色块。**
 *
 * ### 仍然成立的老规矩
 *
 * - **次级/三级文字不用透明度，给实色。** 深色下 30% 的白字很好看，
 *   30% 的黑字压在浅底上会糊。这里 muted / faint 都是实色，对比度 16 : 6.3 : 4.6 三档。
 * - **亮色不是深色取反。** `onAccent`、`warnText`、`surfaceSel` 三处方向相反，机械取反必错。
 * - **用 alpha 做淡色块是安全的，用 alpha 做禁用态是危险的**（深浅两套皮下方向相反，
 *   2026-08-07 已经踩过一次：禁用态白字压浅底直接看不见）。这里 alpha 只用于填充，
 *   压在上面的字一律是深墨，不跟着 alpha 走。
 *
 * 所有色值都过了 `scratchpad/contrast.py`：当文字用对底 ≥ 4.5 : 1，
 * 「提前」和「缺口」色相角差 137°（上一版只差 28°，好坏两色分不开）。
 */
val LightRitualColors = RitualColorScheme(
    bg = Color(0xFFF7F7F6),              // 近白，微微带一点暖，不是刺眼的纯白
    surface = Color(0xFFFFFFFF),         // 卡片 / 面板 / 今天格

    surfaceLow = Color(0xFFFCFCFB),      // 卡片：比底再浅一档
    surfaceSel = Color(0xFFEFEFED),      // 标签底：亮色下靠变暗（方向与深色相反）
    divider = Color(0xFFE8E8E5),
    outline = Color(0xFFDCDCD8),

    onBg = Color(0xFF191C20),            // 主文字   16.0 : 1
    onBgMuted = Color(0xFF565C66),       // 次要文字  6.3 : 1
    onBgFaint = Color(0xFF6B7079),       // 三级文字  4.6 : 1

    accentInk = Color(0xFF2563A8),       // 干净的蓝 5.7 : 1 — 按计划完成 / 当天做完
    accentGold = Color(0xFF0E7C66),      // 松绿 4.8 : 1 — 提前做掉 / 你赚到的
    accentReview = Color(0xFF7E4A96),    // 紫 5.9 : 1 — 复习（离蓝 55°，不会看混）

    warn = Color(0xFFC0392B),            // 红 5.1 : 1 — 缺口
    warnText = Color(0xFFA32A1E),        //     6.7 : 1 — 缺口的文字

    onAccent = Color(0xFFFFFFFF),        // 只给小面积实心块用，别拿它画大色块
    isLight = true,
)

val LocalRitualColors = staticCompositionLocalOf { DarkRitualColors }

/**
 * 组件里用的取色入口。写法和以前一样：`RitualColors.bg`。
 * 只能在 @Composable 里读——颜色现在跟主题走，不再是编译期常量。
 */
object RitualColors {
    val bg: Color @Composable @ReadOnlyComposable get() = LocalRitualColors.current.bg
    val surface: Color @Composable @ReadOnlyComposable get() = LocalRitualColors.current.surface
    val surfaceLow: Color @Composable @ReadOnlyComposable get() = LocalRitualColors.current.surfaceLow
    val surfaceSel: Color @Composable @ReadOnlyComposable get() = LocalRitualColors.current.surfaceSel
    val divider: Color @Composable @ReadOnlyComposable get() = LocalRitualColors.current.divider
    val outline: Color @Composable @ReadOnlyComposable get() = LocalRitualColors.current.outline
    val onBg: Color @Composable @ReadOnlyComposable get() = LocalRitualColors.current.onBg
    val onBgMuted: Color @Composable @ReadOnlyComposable get() = LocalRitualColors.current.onBgMuted
    val onBgFaint: Color @Composable @ReadOnlyComposable get() = LocalRitualColors.current.onBgFaint
    val accentInk: Color @Composable @ReadOnlyComposable get() = LocalRitualColors.current.accentInk
    val accentGold: Color @Composable @ReadOnlyComposable get() = LocalRitualColors.current.accentGold
    val accentReview: Color @Composable @ReadOnlyComposable get() = LocalRitualColors.current.accentReview
    val warn: Color @Composable @ReadOnlyComposable get() = LocalRitualColors.current.warn
    val warnText: Color @Composable @ReadOnlyComposable get() = LocalRitualColors.current.warnText
    val onAccent: Color @Composable @ReadOnlyComposable get() = LocalRitualColors.current.onAccent
    val isLight: Boolean @Composable @ReadOnlyComposable get() = LocalRitualColors.current.isLight
}
