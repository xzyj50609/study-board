package com.zyj.ritual.data.backup

import com.zyj.ritual.domain.vocab.VocabRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 指南灯备份导入的关卡。
 *
 * ### 为什么要专门有这个文件
 *
 * 合并计划「哪些故障会伪装成正常」第 1 条写得很清楚：
 * **「迁移少了几天，但你看不出来」** —— 导入后日历满屏格子，少 3 天不会一眼发现，
 * 等到复盘的时候才知道成绩被吃了。所以要求是「旧 JSON 每一条都能在新库里找到，
 * **总数相等**，逐格断言」。
 *
 * 但 2026-08-07 审计发现这条根本没做：`BackupTest` 里导入只有 1 个测试，
 * 用的是手写的 2 条假记录，而 HANDOFF 却写着「覆盖了 Web 端已知的所有 23 种测试例」。
 *
 * ⚠️ **这个文件仍然不是终点。** 这里的数据是照着真实形态编的（连续背 + 断更 +
 * 只复习 + 中途改日速），**不是用户 7/27 起的那份真数据**。
 * 拿到真实导出的 JSON 后要再加一条用它跑的用例 —— 编的数据永远盖不住真数据里的怪东西。
 */
class BeiciBackupImporterTest {

    /**
     * 一份贴近真实形态的 v2 备份：
     * 7/27 开始，中间断更 3 天，有只复习不背新词的日子，8/1 起把日速从 20 改成 30。
     */
    private val realisticV2 = """
        {
          "version": 2,
          "settings": {
            "bookName": "2027考研真题核心词汇",
            "startDate": "2026-07-27",
            "totalWords": 1883,
            "initialDone": 380,
            "dailyWords": 20,
            "examDate": "2026-12-19",
            "reviewStep": 3,
            "reviewMode": "auto",
            "rateChanges": [
              { "from": "2026-08-01", "dailyWords": 30 }
            ]
          },
          "records": [
            { "date": "2026-07-27", "words": 20, "kind": "new" },
            { "date": "2026-07-28", "words": 20, "kind": "new" },
            { "date": "2026-07-29", "words": 60, "kind": "new" },
            { "date": "2026-07-30", "words": 40, "kind": "backlog" },
            { "date": "2026-08-02", "words": 30, "kind": "new" },
            { "date": "2026-08-03", "words": 30, "kind": "new" },
            { "date": "2026-08-04", "words": 15, "kind": "backlog" },
            { "date": "2026-08-05", "words": 30, "kind": "new" },
            { "date": "2026-08-06", "words": 30, "kind": "new" }
          ]
        }
    """.trimIndent()

    /** 这是本文件最重要的一条：**一条都不许丢**。 */
    @Test
    fun `v2 导入后记录总数与原文件相等`() {
        val (_, records) = BeiciBackupImporter.parseBeiciJson(realisticV2)
        assertEquals("导入后条数和原 JSON 对不上，有记录被静默吃掉了", 9, records.size)
    }

    /** 逐条对：日期、词数、kind 一个字段都不许错位。 */
    @Test
    fun `v2 导入后每一条记录逐字段对得上`() {
        val (_, records) = BeiciBackupImporter.parseBeiciJson(realisticV2)
        val expected = listOf(
            VocabRecord("2026-07-27", 20, "new"),
            VocabRecord("2026-07-28", 20, "new"),
            VocabRecord("2026-07-29", 60, "new"),
            VocabRecord("2026-07-30", 40, "backlog"),
            VocabRecord("2026-08-02", 30, "new"),
            VocabRecord("2026-08-03", 30, "new"),
            VocabRecord("2026-08-04", 15, "backlog"),
            VocabRecord("2026-08-05", 30, "new"),
            VocabRecord("2026-08-06", 30, "new"),
        )
        expected.forEachIndexed { i, want ->
            assertEquals("第 ${i + 1} 条日期不对", want.date, records[i].date)
            assertEquals("${want.date} 的词数不对", want.words, records[i].words)
            assertEquals("${want.date} 的 kind 不对", want.kind, records[i].kind)
        }
    }

    /** 断更的 7/31 和 8/1 不该被凭空补出来。 */
    @Test
    fun `断更的日子不会被凭空补出记录`() {
        val (_, records) = BeiciBackupImporter.parseBeiciJson(realisticV2)
        val dates = records.map { it.date }
        assertTrue("7/31 原文件里没有，不该出现", "2026-07-31" !in dates)
        assertTrue("8/1 原文件里没有，不该出现", "2026-08-01" !in dates)
    }

    /**
     * **分段日速必须原样带过来。**
     * 合并计划 2.1 点名这是「三个绝不能翻错的地方」之一：
     * 日速是分段的，丢了 `rateChanges` 就等于用今天的日速追溯改写过去的成绩。
     */
    @Test
    fun `分段日速 rateChanges 不会在导入时丢掉`() {
        val (config, _) = BeiciBackupImporter.parseBeiciJson(realisticV2)
        assertEquals("rateChanges 条数不对", 1, config.rateChanges.size)
        assertEquals("2026-08-01", config.rateChanges[0].from)
        assertEquals(30, config.rateChanges[0].dailyWords)
    }

    /** 设置项整体不许缩水。 */
    @Test
    fun `v2 设置项全部带过来`() {
        val (config, _) = BeiciBackupImporter.parseBeiciJson(realisticV2)
        assertEquals("2027考研真题核心词汇", config.bookName)
        assertEquals("2026-07-27", config.startDate)
        assertEquals(1883, config.totalWords)
        assertEquals(380, config.initialDone)
        assertEquals(20, config.dailyWords)
        assertEquals("2026-12-19", config.examDate)
        assertEquals("复习档长 reviewStep 丢了", 3, config.reviewStep)
    }

    /**
     * v1 老格式也要吃得下。
     * 之前**这条分支一个测试都没有**——而它正是最老的那批数据会走的路。
     */
    @Test
    fun `v1 老格式能被吃下并转成新结构`() {
        val v1 = """
            {
              "version": 1,
              "settings": { "bookName": "老词书", "totalWords": 1500, "examDate": "2026-12-19" },
              "records": [
                { "date": "2026-07-27", "groups": 1 },
                { "date": "2026-07-28", "groups": 2 }
              ]
            }
        """.trimIndent()
        val (config, records) = BeiciBackupImporter.parseBeiciJson(v1)
        assertNotNull(config)
        assertEquals("v1 的两条记录应当都转过来", 2, records.size)
        assertEquals("2026-07-27", records[0].date)
        assertEquals("2026-07-28", records[1].date)
    }

    /**
     * 单条畸形记录只丢它自己，不能把整份备份带崩。
     * 但**丢了就是丢了**——这里顺便钉死「好的那几条必须全在」，
     * 免得以后有人为了兜异常把整段 records 一起 catch 掉。
     */
    @Test
    fun `个别畸形记录不会连累其余记录`() {
        val dirty = """
            {
              "version": 2,
              "settings": { "totalWords": 100 },
              "records": [
                { "date": "2026-07-27", "words": 20, "kind": "new" },
                { "date": "2026-07-28" },
                { "words": 30, "kind": "new" },
                { "date": "2026-07-29", "words": 25, "kind": "new" }
              ]
            }
        """.trimIndent()
        val (_, records) = BeiciBackupImporter.parseBeiciJson(dirty)
        assertEquals("两条完好的记录都该在", 2, records.size)
        assertEquals("2026-07-27", records[0].date)
        assertEquals("2026-07-29", records[1].date)
    }

    /** 缺 version 字段时按 v1 走，不能直接抛异常把导入整个搞崩。 */
    @Test
    fun `没有 version 字段时按 v1 处理而不是崩掉`() {
        val noVersion = """
            { "settings": { "totalWords": 100 }, "records": [ { "date": "2026-07-27", "groups": 1 } ] }
        """.trimIndent()
        val (config, records) = BeiciBackupImporter.parseBeiciJson(noVersion)
        assertNotNull(config)
        assertEquals(1, records.size)
    }
}
