package com.zyj.ritual.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * 夜读 Ritual 主题。
 *
 * 一套形状两身皮：亮色（出货）和深色（原版，保留但不出货）。
 * 2026-08-07 用户看过深浅样张后选了亮色，所以默认从这天起是 [LightRitualColors]。
 * 亮色不是深色取反算出来的，三处方向相反的地方见 [LightRitualColors] 的注释。
 *
 * 要换回深色只改这里的默认值一处；`DarkRitualColors` 一直在，没删。
 *
 * Material3 的 ColorScheme 我们只填最少的必要字段，组件里直接用 RitualColors。
 * 原因：Material 那套 primary/secondary/tertiary 的语义和我们"月光蓝/暖金/暗铜"
 * 的三强调体系对不上，强行映射会让代码语义变乱。
 *
 * ⚠️ 不跟随系统深浅色。用哪一身由这里的 [colors] 参数定，
 * 系统切夜间模式不该把用户挑好的配色掀翻。
 *
 * 约定：
 * - 颜色：全部用 RitualColors.*
 * - 字号：全部用 RitualTypography.* 或 RitualTextStyles.*
 * - 间距/圆角/尺寸：全部用 RitualSpace / RitualRadius / RitualSize
 * - 动效：全部用 RitualMotion
 */
@Composable
fun RitualTheme(
    colors: RitualColorScheme = LightRitualColors,
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // edge-to-edge：两栏透明由 MainActivity 的 enableEdgeToEdge() 负责，
            // 这里只管图标颜色。statusBarColor / navigationBarColor 在 API 35 起已废弃，
            // 手动再设一遍既没效果也会报 deprecation。
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val insetsController = WindowCompat.getInsetsController(window, view)
            // 亮色底要深色图标，否则状态栏那排图标在白底上看不见。
            insetsController.isAppearanceLightStatusBars = colors.isLight
            insetsController.isAppearanceLightNavigationBars = colors.isLight
        }
    }

    val materialScheme = if (colors.isLight) {
        lightColorScheme(
            background = colors.bg,
            surface = colors.surface,
            onBackground = colors.onBg,
            onSurface = colors.onBg,
            primary = colors.accentInk,
            onPrimary = colors.onAccent,
            secondary = colors.accentGold,
            tertiary = colors.warn,
            surfaceVariant = colors.surfaceLow,
            outline = colors.outline,
        )
    } else {
        darkColorScheme(
            background = colors.bg,
            surface = colors.surface,
            onBackground = colors.onBg,
            onSurface = colors.onBg,
            primary = colors.accentInk,
            onPrimary = colors.onAccent,
            secondary = colors.accentGold,
            tertiary = colors.warn,
            surfaceVariant = colors.surfaceLow,
            outline = colors.outline,
        )
    }

    CompositionLocalProvider(
        LocalRitualColors provides colors,
        LocalRitualTypography provides ritualTypography(colors),
    ) {
        MaterialTheme(
            colorScheme = materialScheme,
            typography = ritualTypography(colors),
            content = content,
        )
    }
}
