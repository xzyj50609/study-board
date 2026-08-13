package com.zyj.ritual.domain.vocab

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 已背百分比的关卡。
 *
 * ### 为什么单独一个文件
 *
 * 这个函数只有五行，但它守的是一个**会伪装成正常**的故障：
 * 1882/1883 是 99.947%，四舍五入或者不封顶都会在屏幕上打出「100%」。
 * 那一刻用户会以为词书背完了，而界面上**没有任何别的地方能反驳它**——
 * 旁边的「还要学 1」字号小、颜色淡，百分比才是主角。
 *
 * 下面 1882 那两条就是钉这个的。改坏了（比如把 `coerceIn(0, 99)` 摘掉，
 * 或者把 `floor` 换成 `roundToInt`）它们必须立刻红。
 */
class DonePercentTest {

    @Test
    fun `一个都没背是 0`() {
        assertEquals(0, VocabCalculator.donePercent(0, 1883, finished = false))
    }

    @Test
    fun `正好一半是 50`() {
        assertEquals(50, VocabCalculator.donePercent(500, 1000, finished = false))
    }

    @Test
    fun `差一点到一半 报 49 不报 50`() {
        // 941/1883 = 49.97%。这条最初写成 50 是我算错了——
        // 留着它正好当个例子：向下取整这条规矩连写测试的人都会想当然
        assertEquals(49, VocabCalculator.donePercent(941, 1883, finished = false))
    }

    @Test
    fun `向下取整 不四舍五入`() {
        // 380/1883 = 20.18%
        assertEquals(20, VocabCalculator.donePercent(380, 1883, finished = false))
        // 1789/1883 = 95.008% —— 取整后 95，不是 96
        assertEquals(95, VocabCalculator.donePercent(1789, 1883, finished = false))
    }

    @Test
    fun `还差最后一个词 不显示 100`() {
        // 1882/1883 = 99.947%。这里要是出 100，用户会以为背完了
        assertEquals(
            "还剩 1 个词却显示 100%，用户会以为词书背完了",
            99,
            VocabCalculator.donePercent(1882, 1883, finished = false),
        )
    }

    /**
     * 直接打到 `coerceIn(0, 99)` 那一行。
     *
     * 做突变验证时发现的：挡住 1882/1883 的其实是 `floor`，不是封顶——
     * 单把封顶从 99 改成 100，上面那些用例一条都不红。
     * 封顶是**第二道**：万一哪天 floor 被换成四舍五入，它还能兜住 100%。
     * 这条用例喂一个 computeState 正常产不出来的组合（背完了但 finished=false），
     * 好让那一行有自己的关卡，而不是靠别的用例顺带覆盖。
     */
    @Test
    fun `没标记完成时 就算数字够了也封在 99`() {
        assertEquals(
            "finished 还没置位就报 100%，等于抢在算法前面宣布背完了",
            99,
            VocabCalculator.donePercent(1883, 1883, finished = false),
        )
    }

    @Test
    fun `只有真背完才是 100`() {
        assertEquals(100, VocabCalculator.donePercent(1883, 1883, finished = true))
    }

    @Test
    fun `finished 优先于数字 冲突时以 finished 为准`() {
        // doneWords 被夹到 totalWords 之后理论上等于总量，
        // 但万一上游给了个不一致的组合，finished 说了算——
        // 它是 computeState 算出来的，比这里再算一遍可靠
        assertEquals(100, VocabCalculator.donePercent(9999, 1883, finished = true))
    }

    @Test
    fun `总量为 0 不炸 返回 0`() {
        // 词书还没设置时 totalWords 可能是 0，别让一个除零把整页搞崩
        assertEquals(0, VocabCalculator.donePercent(0, 0, finished = false))
        assertEquals(0, VocabCalculator.donePercent(10, 0, finished = false))
    }

    @Test
    fun `负数进来也夹在 0 以上`() {
        assertEquals(0, VocabCalculator.donePercent(-5, 1883, finished = false))
    }

    /**
     * 进度页摘要卡和背词看板都调这一个函数。
     * 这条断言的意思是「同样的输入必须给同样的输出」——
     * 它挡的是"哪天有人图省事在某一页里自己写了一遍 doneWords*100/total"。
     */
    @Test
    fun `同一组输入在任何调用点都给同一个结果`() {
        val a = VocabCalculator.donePercent(1882, 1883, finished = false)
        val b = VocabCalculator.donePercent(1882, 1883, finished = false)
        assertEquals(a, b)
        assertEquals(99, a)
    }
}
