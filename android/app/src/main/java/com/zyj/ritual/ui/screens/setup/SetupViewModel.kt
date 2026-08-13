package com.zyj.ritual.ui.screens.setup

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zyj.ritual.data.repository.StudyRepository
import com.zyj.ritual.domain.model.Plan
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/**
 * 首次设置向导 ViewModel。
 *
 * 字段（按 Q3 裁决）：
 * - totalArticles：总篇数，默认 42，可改
 * - completedArticles：已学完篇数，0 到 totalArticles-1
 * - daysPerArticle：每篇几天（1/2/3），默认 2
 * - startDate：计划开始日期，默认今天
 *
 * 下一篇编号 = 已完成 + 1（派生，不解耦）
 */
class SetupViewModel(
    private val repository: StudyRepository,
) : ViewModel() {

    /** 保存的四态。UI 靠它决定按钮能不能点、要不要报错、要不要显示"进入今日"。 */
    sealed interface SaveState {
        data object Idle : SaveState
        data object Saving : SaveState
        /** 已经写进存储了。此时页面停在本页显示确认信息，等用户主动点进主页。 */
        data class Saved(val plan: Plan) : SaveState
        data class Failed(val message: String) : SaveState
    }

    var saveState: SaveState by mutableStateOf(SaveState.Idle)
        private set

    private val _totalArticles = mutableIntStateOf(DEFAULT_TOTAL_ARTICLES)
    var totalArticles: Int
        get() = _totalArticles.intValue
        set(value) {
            if (value > 0) {
                _totalArticles.intValue = value
                if (completedArticles >= value) {
                    completedArticles = value - 1
                }
            }
        }

    private val _completedArticles = mutableIntStateOf(DEFAULT_COMPLETED_ARTICLES)
    var completedArticles: Int
        get() = _completedArticles.intValue
        set(value) {
            if (value in 0 until totalArticles) {
                _completedArticles.intValue = value
            }
        }

    private val _daysPerArticle = mutableIntStateOf(DEFAULT_DAYS_PER_ARTICLE)
    var daysPerArticle: Int
        get() = _daysPerArticle.intValue
        set(value) {
            if (value in 1..3) _daysPerArticle.intValue = value
        }

    private val _startDate = mutableStateOf(todayBeijing())
    var startDate: LocalDate
        get() = _startDate.value
        set(value) { _startDate.value = value }

    val nextArticle: Int get() = completedArticles + 1

    init {
        // 从设置页「调整起点与总篇数」再进来时，表单要显示当前计划而不是回到出厂默认值。
        viewModelScope.launch {
            val existing = runCatching { repository.getPlan() }.getOrNull() ?: return@launch
            totalArticles = existing.totalArticles
            completedArticles = existing.completedBeforeStart
            daysPerArticle = existing.daysPerArticle
            startDate = existing.planStartDate
        }
    }

    // ——— 预览 ———

    val remainingArticles: Int get() = totalArticles - completedArticles
    val totalPlanDays: Int get() = remainingArticles * daysPerArticle
    val expectedCompletionDate: LocalDate
        get() = startDate.plusDays((totalPlanDays - 1).toLong())

    // ——— 步进器操作 ———

    fun incrementCompleted() {
        if (completedArticles < totalArticles - 1) completedArticles++
    }

    fun decrementCompleted() {
        if (completedArticles > 0) completedArticles--
    }

    /**
     * 保存计划：写入 Plan + IMPORTED 记录。
     *
     * **不做页面跳转**。写成功后停在本页、进入 Saved 态显示确认信息，
     * 由用户自己点「进入今日」再走（见 SetupScreen）。
     *
     * 为什么不自动跳：上一版按钮直接调导航、连这个函数都没调，
     * 结果 Plan 从没落盘，用户看到页面跳走了就以为设置成功了，
     * 实际上首页三个 tab 永远停在「加载中」。
     * 保存和跳转分成两步，"存住了"这件事就有了肉眼可见的凭据。
     */
    fun savePlan() {
        if (saveState is SaveState.Saving) return  // 防连点写两次

        viewModelScope.launch {
            saveState = SaveState.Saving
            val result = runCatching {
                val plan = Plan(
                    totalArticles = totalArticles,
                    startArticle = nextArticle,
                    completedBeforeStart = completedArticles,
                    planStartDate = startDate,
                    daysPerArticle = daysPerArticle,
                )
                repository.initialSetup(plan)
                // 回读一次，确认真的落盘了才算成功——不靠"没抛异常"推断
                repository.getPlan() ?: error("计划写入后回读为空")
            }
            saveState = result.fold(
                onSuccess = { SaveState.Saved(it) },
                onFailure = { SaveState.Failed(it.message ?: it::class.java.simpleName) },
            )
        }
    }

    companion object {
        const val DEFAULT_TOTAL_ARTICLES = 42
        const val DEFAULT_COMPLETED_ARTICLES = 12
        const val DEFAULT_DAYS_PER_ARTICLE = 2

        private fun todayBeijing(): LocalDate =
            LocalDate.now(ZoneId.of("Asia/Shanghai"))
    }
}
