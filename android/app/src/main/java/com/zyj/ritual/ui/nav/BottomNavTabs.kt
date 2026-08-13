package com.zyj.ritual.ui.nav

/**
 * 底部导航五个 tab 的路由集合。
 * 用来判断当前路由是不是 tab 页（要不要显示底栏）。
 *
 * ⚠️ 二级页（`article/{articleIndex}`、`history`）**不许加进来**——
 * 不在这个集合里，底栏才会自动隐藏。
 */
object BottomNavTabs {
    val all = setOf(
        Routes.TODAY,
        Routes.CALENDAR,
        Routes.PROGRESS,
        Routes.VOCAB,
        Routes.SETTINGS,
    )
}
