package com.zyj.ritual.ui.screens.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zyj.ritual.core.time.BeijingClock
import com.zyj.ritual.data.repository.StudyRepository
import com.zyj.ritual.data.repository.TodayState
import com.zyj.ritual.domain.calculator.CreditCalculator
import com.zyj.ritual.domain.calculator.RescheduleCalculator
import com.zyj.ritual.domain.model.RecordSource
import com.zyj.ritual.ui.state.RitualUiState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * 今日 / 月历 / 进度 / 文章详情 共享的 ViewModel。
 *
 * 为什么共享：四个页面展示的是同一组数据的不同切面。
 * 注意它必须挂在 **Activity** 的 ViewModelStore 上（见 RitualAppScaffold），
 * 不能每个 NavBackStackEntry 各建一份——那样会有四份互相独立的数据流同时跑。
 */
import com.zyj.ritual.data.repository.VocabRepository

class TodayViewModel(
    private val repository: StudyRepository,
    val vocabRepository: VocabRepository,
    private val clock: BeijingClock,
) : ViewModel() {

    /**
     * 页面状态。
     *
     * Loading / NotSetUp / Ready / Error 四种情况分得清清楚楚。
     * 上一版把它们全压成 `TodayState?`，导致真机上「设置没保存成功」被显示成「加载中」，
     * 白白烧掉一整轮排查。
     */
    val uiState: StateFlow<RitualUiState> = repository.todayStateFlow()
        .map<TodayState?, RitualUiState> { state ->
            if (state == null) RitualUiState.NotSetUp else RitualUiState.Ready(state)
        }
        .catch { error ->
            android.util.Log.e("RitualState", "TodayState 数据流异常", error)
            emit(
                RitualUiState.Error(
                    message = error.message ?: error::class.java.simpleName,
                    detail = error.stackTraceToString().lineSequence().take(12).joinToString("\n"),
                )
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = RitualUiState.Loading,
        )

    /** 当前已就绪的数据；没就绪时为 null。操作类函数都靠它取上下文。 */
    private val currentState: TodayState?
        get() = (uiState.value as? RitualUiState.Ready)?.state

    /**
     * 勾选一项。
     * 根据"今天"和任务的计划日期关系，自动判断来源（当天/提前/补打卡）。
     */
    fun checkTask(articleIndex: Int, taskIndex: Int) {
        viewModelScope.launch {
            val state = currentState ?: return@launch
            val calendar = state.calendar
            val today = state.today

            // 算出这项的计划日
            val planDayIndex = calendar.taskToPlanDayIndex(articleIndex, taskIndex)
            val planDate = calendar.planDayIndexToDate(planDayIndex) ?: today

            val source = when {
                planDate.isBefore(today) -> RecordSource.BACKFILL   // 过去的 = 补打卡
                planDate.isAfter(today) -> RecordSource.ADVANCED   // 未来的 = 提前
                else -> RecordSource.CHECKED                       // 当天的 = 正常
            }

            repository.checkTask(articleIndex, taskIndex, planDate, source)
        }
    }

    /**
     * 撤销一项。
     */
    fun undoTask(articleIndex: Int, taskIndex: Int) {
        viewModelScope.launch {
            repository.undoTask(articleIndex, taskIndex)
        }
    }

    /**
     * 优先补上：按最早缺口顺序，一次性补齐所有缺额。
     */
    fun backfillFirst() {
        viewModelScope.launch {
            val state = currentState ?: return@launch
            val deficit = state.credit.deficitTaskCount
            if (deficit <= 0) return@launch

            val tasks = CreditCalculator.findEarliestDeficitTasks(
                plan = state.plan,
                calendar = state.calendar,
                records = state.records,
                today = state.today,
                maxCount = deficit,
            )

            tasks.forEach { (art, task) ->
                val planDayIndex = state.calendar.taskToPlanDayIndex(art, task)
                val plannedDate = state.calendar.planDayIndexToDate(planDayIndex) ?: state.today
                repository.checkTask(
                    articleIndex = art,
                    taskIndex = task,
                    plannedDate = plannedDate,
                    source = RecordSource.BACKFILL,
                )
            }
        }
    }

    /**
     * 重新排期：把剩余计划挪到从今天开始。
     */
    fun reschedule() {
        viewModelScope.launch {
            val state = currentState ?: return@launch
            val newPlan = RescheduleCalculator.reschedule(
                currentPlan = state.plan,
                progress = state.progress,
                today = state.today,
            )
            repository.savePlan(newPlan, isReschedule = true)
        }
    }

    /**
     * 记录背单词打卡（新词 / 复习）。
     */
    fun addVocabRecord(words: Int, kind: String = "new") {
        viewModelScope.launch {
            vocabRepository.addRecord(words, kind)
        }
    }

    /** 一键登记整套卷（进入消化期）。 */
    fun registerPaperSession(name: String, completedDate: LocalDate) {
        viewModelScope.launch {
            repository.registerPaperSession(name, completedDate)
        }
    }

    /** 撤销整套卷登记。 */
    fun undoPaperSession(id: Long) {
        viewModelScope.launch {
            repository.undoPaperSession(id)
        }
    }
}
