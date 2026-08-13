package com.zyj.ritual.domain.vocab

/**
 * 日历格子（对应 JS computeCalendar 返回的 cells 数组元素）。
 * 三种类型：月份标签、前导空位、日格。
 */
sealed interface VocabCalendarCell {
    /** 月份标签行（如 "7月"） */
    data class Month(val label: String) : VocabCalendarCell

    /** 周一对齐的前导空位 */
    data object Pad : VocabCalendarCell

    /** 日格 */
    data class Day(
        val date: String,
        val dayNum: Int,
        /** 总填充比 = (ownWords + aheadWords) / quota ∈ [0,1] */
        val fill: Double,
        /** 自己那段的比例 = ownWords / quota */
        val own: Double,
        /** 当天自己记的词数 */
        val ownWords: Int,
        /** 更早日子提前做掉的词数 */
        val aheadWords: Int,
        /** 当天的额度（分段日速下每天不同） */
        val quota: Int,
        /** 当天复习了多少 */
        val reviewWords: Int,
        /** 是否是今天 */
        val isToday: Boolean,
        /** 是否是计划完成日 */
        val isPlanFinish: Boolean,
        /** 是否是预计完成日（已完成时为 false） */
        val isProjFinish: Boolean,
        /**
         * 缺口：严格早于今天、没填满、且那天什么都没干。
         * 只复习没背新词的那天不算漏（reviewOnly）。
         */
        val gap: Boolean,
        /**
         * 复习日：早于今天、新词没填满，但复习过。
         * 和 gap 互斥——一格不能既红边又蓝底。
         */
        val reviewOnly: Boolean,
        /** 该格有 aheadWords——来自更早日子的提前做掉的量 */
        val extra: Boolean,
    ) : VocabCalendarCell
}

/** computeCalendar 的返回值 */
data class VocabCalendarResult(val cells: List<VocabCalendarCell>)
