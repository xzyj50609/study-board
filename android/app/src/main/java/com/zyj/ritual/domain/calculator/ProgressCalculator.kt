package com.zyj.ritual.domain.calculator

import com.zyj.ritual.domain.model.Plan
import com.zyj.ritual.domain.model.ProgressResult
import com.zyj.ritual.domain.model.TaskRecord

/**
 * 进度计算（R6-R10）。
 *
 * 输入：Plan + 所有完成记录（List<TaskRecord>）。
 * 输出：ProgressResult（整体进度、完整篇数、当前篇、当前文章进度）。
 *
 * 约定：所有计算纯函数、无副作用。同一个输入永远得到同一个输出。
 */
object ProgressCalculator {

    fun calculate(plan: Plan, records: List<TaskRecord>): ProgressResult {
        val totalTasks = plan.totalTasks

        // 只认落在当前总篇数范围内的记录。
        // 用户把总篇数调小之后，旧记录的篇号会超出数组长度——
        // 上一版直接 perArticleCount[it.articleIndex]++ 会数组越界，
        // 异常又被上层 catch 吞掉，界面就变成永远的"加载中"。宁可少算，不能崩。
        val validRecords = records.filter { it.articleIndex in 1..plan.totalArticles }
        val totalCompleted = validRecords.size.coerceAtMost(totalTasks)

        // 每篇完成项数
        val perArticleCount = IntArray(plan.totalArticles + 1) { 0 }  // 1-based
        validRecords.forEach { perArticleCount[it.articleIndex]++ }

        // 完整篇数（R7）
        var completeArticles = 0
        for (i in 1..plan.totalArticles) {
            if (perArticleCount[i] >= plan.tasksPerArticle) completeArticles++
        }

        // 当前篇 = 从 startArticle 往后第一个不满的篇（R9）
        var currentArticle = plan.totalArticles  // 默认最后一篇
        for (i in plan.startArticle..plan.totalArticles) {
            if (perArticleCount[i] < plan.tasksPerArticle) {
                currentArticle = i
                break
            }
        }

        val currentArticleCompleted = perArticleCount[currentArticle].coerceAtMost(plan.tasksPerArticle)
        val currentArticleProgress = currentArticleCompleted.toDouble() / plan.tasksPerArticle

        // 整体进度（R6），两位小数精度，但内部用完整 double
        val overallProgress = totalCompleted.toDouble() / totalTasks

        val isAllDone = totalCompleted >= totalTasks

        return ProgressResult(
            totalCompleted = totalCompleted,
            totalTasks = totalTasks,
            overallProgress = overallProgress,
            completeArticles = completeArticles,
            currentArticle = currentArticle,
            currentArticleProgress = currentArticleProgress,
            currentArticleCompleted = currentArticleCompleted,
            isAllDone = isAllDone,
        )
    }

    /**
     * 格式化整体进度百分比，保留两位小数（R6）。
     * 例如 28.57%。
     */
    fun formatOverallProgress(progress: Double): String {
        val pct = (progress * 100).coerceIn(0.0, 100.0)
        return "%.2f".format(pct)
    }
}
