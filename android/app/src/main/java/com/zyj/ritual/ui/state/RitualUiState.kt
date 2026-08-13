package com.zyj.ritual.ui.state

import com.zyj.ritual.data.repository.TodayState

/**
 * 四个 tab 共用的页面状态。
 *
 * 为什么不继续用 `TodayState?`：
 * 上一版把「还没读完」「还没设置过」「上游抛异常」三件事全压成 null，
 * 于是真机上出问题时页面只会显示「加载中…」，看不出卡在哪一步——
 * 2026-08-06 那次「三个页面永久加载中」就是这么被掩盖的。
 *
 * 现在三种情况各有各的分支，UI 必须显式处理，问题会自己说出来。
 */
sealed interface RitualUiState {

    /** 首帧，数据还没到。正常情况下只闪一下。 */
    data object Loading : RitualUiState

    /** DataStore 里没有 Plan——用户还没做过首次设置，或者设置没保存成功。 */
    data object NotSetUp : RitualUiState

    /** 正常态。 */
    data class Ready(val state: TodayState) : RitualUiState

    /** 上游 Flow 抛了异常。message 给人看，detail 给排查用。 */
    data class Error(val message: String, val detail: String?) : RitualUiState
}
