package com.zyj.ritual.ui.screens.vocab

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zyj.ritual.data.repository.VocabAggregateState
import com.zyj.ritual.data.repository.VocabRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface VocabUiState {
    data object Loading : VocabUiState
    data class Ready(val aggregate: VocabAggregateState) : VocabUiState
    data class Error(val message: String, val detail: String?) : VocabUiState
}

class VocabViewModel(
    private val vocabRepository: VocabRepository,
) : ViewModel() {

    val uiState: StateFlow<VocabUiState> = vocabRepository.aggregateStateFlow()
        .map<VocabAggregateState, VocabUiState> { aggregate ->
            VocabUiState.Ready(aggregate)
        }
        .catch { e ->
            emit(VocabUiState.Error("加载背单词看板失败", e.message ?: e.toString()))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = VocabUiState.Loading
        )

    fun addRecord(words: Int, kind: String = "new") {
        viewModelScope.launch {
            vocabRepository.addRecord(words, kind)
        }
    }

    /** 存「今天要复习多少」。日期由 Repository 用北京时钟盖，UI 不许自己算今天 */
    fun saveReviewDue(due: Int) {
        viewModelScope.launch {
            vocabRepository.saveReviewDue(due)
        }
    }

    /** 保存背词设置；计划起算日非空时自动把计划线校准到那天。 */
    fun saveSetup(
        bookName: String,
        totalWords: Int,
        initialDone: Int,
        dailyWords: Int,
        examDate: String,
        planStartDate: String?,
    ) {
        viewModelScope.launch {
            vocabRepository.saveSetup(
                bookName = bookName,
                totalWords = totalWords,
                initialDone = initialDone,
                dailyWords = dailyWords,
                examDate = examDate,
                planStartDate = planStartDate,
            )
        }
    }
}
