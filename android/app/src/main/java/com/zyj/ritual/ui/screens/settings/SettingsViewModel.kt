package com.zyj.ritual.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zyj.ritual.core.time.BeijingClock
import com.zyj.ritual.data.repository.BackupRepository
import com.zyj.ritual.data.repository.ExportData
import com.zyj.ritual.data.repository.StudyRepository
import com.zyj.ritual.domain.calculator.ProgressCalculator
import com.zyj.ritual.domain.calculator.RescheduleCalculator
import com.zyj.ritual.domain.calendar.PlanCalendar
import com.zyj.ritual.domain.calculator.CreditCalculator
import com.zyj.ritual.domain.model.Plan
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * 设置页 ViewModel。
 */
class SettingsViewModel(
    private val repository: StudyRepository,
    private val backupRepository: BackupRepository,
    private val clock: BeijingClock,
) : ViewModel() {

    data class SettingsState(
        val plan: Plan? = null,
        val completeArticles: Int = 0,
        val currentArticle: Int = 0,
        val reschedulePreview: ReschedulePreview? = null,
    )

    data class ReschedulePreview(
        val wasAhead: Boolean,
        val newStartArticle: Int,
        val newCompletionDate: LocalDate,
    )

    val state: StateFlow<SettingsState> = combine(
        repository.planFlow(),
        repository.recordsFlow(),
    ) { plan, records ->
        if (plan == null) return@combine SettingsState()

        val progress = ProgressCalculator.calculate(plan, records)
        val calendar = PlanCalendar.create(plan)
        val credit = CreditCalculator.calculate(plan, calendar, records, clock.today())
        val preview = RescheduleCalculator.preview(plan, progress, credit, clock.today())

        SettingsState(
            plan = plan,
            completeArticles = progress.completeArticles,
            currentArticle = progress.currentArticle,
            reschedulePreview = ReschedulePreview(
                wasAhead = preview.wasAhead,
                newStartArticle = preview.newStartArticle,
                newCompletionDate = preview.newBaseCompletionDate,
            ),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsState(),
    )

    // ——— 操作 ———

    fun setDaysPerArticle(days: Int) {
        viewModelScope.launch {
            val plan = repository.planFlow().first() ?: return@launch
            repository.savePlan(plan.copy(daysPerArticle = days))
        }
    }

    fun reschedule(onDone: () -> Unit = {}) {
        viewModelScope.launch {
            val plan = repository.planFlow().first() ?: return@launch
            val records = repository.recordsFlow().first()
            val progress = ProgressCalculator.calculate(plan, records)
            val newPlan = RescheduleCalculator.reschedule(plan, progress, clock.today())
            repository.savePlan(newPlan, isReschedule = true)
            onDone()
        }
    }

    fun undoLastArticle(onDone: () -> Unit = {}) {
        viewModelScope.launch {
            val plan = repository.planFlow().first() ?: return@launch
            val records = repository.recordsFlow().first()
            val progress = ProgressCalculator.calculate(plan, records)

            if (progress.completeArticles <= plan.completedBeforeStart) {
                return@launch
            }

            repository.undoArticle(progress.completeArticles)
            onDone()
        }
    }

    suspend fun exportData(): ExportData? {
        return try {
            backupRepository.exportAll()
        } catch (e: Exception) {
            null
        }
    }

    suspend fun importData(data: ExportData): Boolean {
        return try {
            backupRepository.importAll(data)
            true
        } catch (e: Exception) {
            false
        }
    }
}
