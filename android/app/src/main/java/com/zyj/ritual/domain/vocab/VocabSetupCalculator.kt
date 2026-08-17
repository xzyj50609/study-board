package com.zyj.ritual.domain.vocab

/**
 * 背词设置的「锚点」换算。
 *
 * 用户脑子里的模型只有一句话：
 * **「8 月 2 号那天我已经背了 420 个，从那天起每天 40 个，什么时候背完？」**
 *
 * 而算法层要的是另外两个量：
 * - `initialDone`：用本看板**之前**就背会的存量（一个跟日期无关的历史常数）；
 * - `planStartDone`：计划起算日那天的累计已背量。
 *
 * 上一版把 `initialDone` 直接摆到设置页上，标签叫「起点存量（以前已背）」，
 * 让用户自己去猜这个内部概念是什么意思——猜错一次，整条计划线就错。
 * 这里改成：用户只给锚点，两个内部量由这个对象反推。
 *
 * 纯函数，不碰 Android，也不碰存储。
 */
object VocabSetupCalculator {

    /** 计划起算日之前的新词记录累计（不含 backlog，不含起算日当天） */
    fun newWordsBefore(records: List<VocabRecord>, planStartDate: String): Int =
        records
            .filter { it.kind != "backlog" && it.date < planStartDate }
            .sumOf { it.words }

    /**
     * 把锚点摊回 VocabConfig。
     *
     * initialDone = 那天已背 − 那天之前的记录累计。
     * 这一步保证了一件事：**真实已背量（initialDone + 全部记录）算出来还是「那天已背 + 那天以后的记录」**，
     * 也就是用户填的锚点不会把他今天的进度改掉。
     *
     * @throws IllegalArgumentException 锚点跟已有记录矛盾时（见 [validate]）。校验请先走 [validate]，
     * 别指望这里抛的异常能被用户看见——它只是最后一道防线。
     */
    fun applyAnchor(
        current: VocabConfig,
        records: List<VocabRecord>,
        bookName: String,
        totalWords: Int,
        planStartDate: String,
        planStartDone: Int,
        dailyWords: Int,
        examDate: String,
    ): VocabConfig {
        val error = validate(records, totalWords, planStartDate, planStartDone, dailyWords)
        require(error == null) { error ?: "" }

        val before = newWordsBefore(records, planStartDate)
        return current.copy(
            bookName = bookName.ifBlank { current.bookName },
            totalWords = totalWords,
            initialDone = planStartDone - before,
            planStartDone = planStartDone,
            dailyWords = dailyWords,
            examDate = examDate.ifBlank { current.examDate },
            rateChanges = listOf(RateChange(from = planStartDate, dailyWords = dailyWords)),
        )
    }

    /**
     * 校验锚点。返回 null 表示没问题，否则是给用户看的大白话原因。
     *
     * 这些都是「填得下去、但算出来是胡说」的组合，必须在保存之前拦住：
     * 屏幕上不会有任何地方提示"你的计划线是负的"，只会显示一个荒唐的完成日。
     */
    fun validate(
        records: List<VocabRecord>,
        totalWords: Int,
        planStartDate: String,
        planStartDone: Int,
        dailyWords: Int,
    ): String? {
        if (!planStartDate.matches(DATE_RE)) return "起算日格式不对"
        if (totalWords <= 0) return "词书总量要大于 0"
        if (dailyWords <= 0) return "每天要背的数量要大于 0"
        if (planStartDone < 0) return "那天已背的数量不能是负数"
        if (planStartDone > totalWords) {
            return "那天已背 $planStartDone 个，比整本词书（$totalWords 个）还多"
        }

        val before = newWordsBefore(records, planStartDate)
        if (planStartDone < before) {
            return "起算日之前你在这个 App 里已经记了 $before 个，" +
                "「那天已背」不能少于这个数"
        }

        val allNew = VocabCalculator.sumNewWords(records)
        val doneNow = planStartDone - before + allNew
        if (doneNow > totalWords) {
            return "照这个填法，现在已背会变成 $doneNow 个，超过词书总量 $totalWords 个"
        }
        return null
    }

    /**
     * 打开设置页时，把 config 反推成锚点表单的初值。
     *
     * 优先级：已经存过的计划起算日 > 最早一条新词记录 > 今天。
     */
    fun anchorFrom(
        config: VocabConfig,
        records: List<VocabRecord>,
        todayStr: String,
    ): Anchor {
        val storedStart = VocabCalculator.normalizeRateChanges(config).firstOrNull()?.from
        val earliestRecord = records.filter { it.kind != "backlog" }.minOfOrNull { it.date }
        val planStartDate = storedStart ?: earliestRecord ?: todayStr

        val planStartDone = config.planStartDone
            ?: (config.initialDone + newWordsBefore(records, planStartDate))

        return Anchor(
            planStartDate = planStartDate,
            planStartDone = planStartDone,
            dailyWords = VocabCalculator.dailyQuotaOn(planStartDate, config),
        )
    }

    data class Anchor(
        val planStartDate: String,
        val planStartDone: Int,
        val dailyWords: Int,
    )

    private val DATE_RE = Regex("""\d{4}-\d{2}-\d{2}""")
}
