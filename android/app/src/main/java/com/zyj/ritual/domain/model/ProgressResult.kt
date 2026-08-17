package com.zyj.ritual.domain.model

import java.time.LocalDate

/**
 * 进度计算结果。
 * 全部是派生量，不入库，每次重新算。
 */
data class ProgressResult(
    val totalCompleted: Int,          // 已完成总项数（含 IMPORTED）
    val totalTasks: Int,              // 总任务数（分母）
    val overallProgress: Double,      // 整体进度 0..1，R6
    val completeArticles: Int,        // 完整篇数，R7
    val currentArticle: Int,          // 当前篇（第一个未满 6 项的篇），R9
    val currentArticleProgress: Double, // 当前文章进度 0..1，R8
    val currentArticleCompleted: Int,   // 当前篇已完成项数
    val isAllDone: Boolean,             // 全部 252 项完成
)

/**
 * 额度计算结果。
 */
data class CreditResult(
    val creditTasks: Int,        // 额度（项），正数领先，负数缺额，R12
    val creditDays: Double,      // 额度天数 = creditTasks / 3，正数保留一位小数，R13
    val state: CreditState,      // 领先 / 齐平 / 缺额，R14
    val expectedCompletionDate: LocalDate,  // 预计完成日，R17
    val baseCompletionDate: LocalDate,      // 基础计划完成日，R18
    val earliestDeficitDate: LocalDate?,    // 最早缺口日期，R16（缺额时非空）
    val deficitTaskCount: Int,              // 缺额项数（= |creditTasks| when state=DEFICIT）
)

enum class CreditState {
    AHEAD,   // 领先（金）
    EVEN,    // 齐平（中性）
    DEFICIT, // 缺额（暗铜）
    NOT_STARTED, // 计划还没开始（今天 < planStartDate）
}

/**
 * 今日首页标题和主按钮文案的计算结果（R31-R38）。
 */
data class TodayCopyResult(
    val headline: String,         // 首页大标题
    val primaryButtonText: String,// 底部主按钮文字
    val primaryButtonTarget: PrimaryButtonTarget, // 主按钮点击后做什么
    /**
     * 今天若处于整套卷的消化窗口，这里是非空的说明文案
     * （如「消化日 2/4 · 2016 年卷」）；为 null 表示普通学习日。
     * 消化日不催进度，但允许主动提前学。
     */
    val digestionNote: String? = null,
)

sealed interface PrimaryButtonTarget {
    data object GoToDonePage : PrimaryButtonTarget
    data object StartNextArticle : PrimaryButtonTarget
    data class CheckTask(val articleIndex: Int, val taskIndex: Int) : PrimaryButtonTarget
    data class BackfillEarliestDeficit(val date: LocalDate) : PrimaryButtonTarget
}
