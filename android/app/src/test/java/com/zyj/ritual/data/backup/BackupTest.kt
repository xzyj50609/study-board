package com.zyj.ritual.data.backup

import com.zyj.ritual.data.repository.ExportData
import com.zyj.ritual.domain.model.HistoryEvent
import com.zyj.ritual.domain.model.HistoryEventType
import com.zyj.ritual.domain.model.Plan
import com.zyj.ritual.domain.model.RecordSource
import com.zyj.ritual.domain.model.TaskRecord
import com.zyj.ritual.domain.vocab.VocabConfig
import com.zyj.ritual.domain.vocab.VocabRecord
import org.junit.Assert.*
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

class BackupTest {

    @Test
    fun `v2 backup encode and decode roundtrip with vocab data`() {
        val samplePlan = Plan(
            totalArticles = 42,
            tasksPerArticle = 6,
            startArticle = 1,
            completedBeforeStart = 0,
            planStartDate = LocalDate.parse("2026-08-01"),
            daysPerArticle = 1,
            studyWeekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY),
            timezone = "Asia/Shanghai",
        )
        val exportData = ExportData(
            plan = samplePlan,
            records = listOf(
                TaskRecord("1-1", 1, 1, Instant.now(), LocalDate.parse("2026-08-01"), RecordSource.CHECKED)
            ),
            history = listOf(
                HistoryEvent(
                    id = 1L,
                    at = Instant.now(),
                    type = HistoryEventType.CHECKED,
                    articleIndex = 1,
                    taskIndex = 1,
                    taskName = "task 1",
                )
            ),
            exportedAt = Instant.now(),
            vocabConfig = VocabConfig(totalWords = 1883, initialDone = 380),
            vocabRecords = listOf(
                VocabRecord("2026-07-27", 20, "new"),
                VocabRecord("2026-07-28", 40, "backlog")
            )
        )

        val jsonStr = BackupSerializer.encode(exportData)
        assertTrue(jsonStr.contains("\"formatVersion\": 2"))
        assertTrue(jsonStr.contains("\"vocabConfig\""))

        val decoded = BackupSerializer.decode(jsonStr)
        assertEquals(42, decoded.plan.totalArticles)
        assertNotNull(decoded.vocabConfig)
        assertEquals(1883, decoded.vocabConfig?.totalWords)
        assertEquals(2, decoded.vocabRecords?.size)
        assertEquals("2026-07-27", decoded.vocabRecords?.get(0)?.date)
    }

    @Test
    fun `parse beici v2 json`() {
        val rawJson = """
            {
              "version": 2,
              "settings": {
                "bookName": "2027考研真题核心词汇",
                "startDate": "2026-07-27",
                "totalWords": 1996,
                "initialDone": 380,
                "dailyWords": 20,
                "examDate": "2026-12-19"
              },
              "records": [
                { "date": "2026-07-27", "words": 20, "kind": "new" },
                { "date": "2026-07-28", "words": 40, "kind": "backlog" }
              ]
            }
        """.trimIndent()

        val (config, records) = BeiciBackupImporter.parseBeiciJson(rawJson)
        assertEquals("2027考研真题核心词汇", config.bookName)
        assertEquals(1996, config.totalWords)
        assertEquals(380, config.initialDone)
        assertEquals(20, config.dailyWords)
        assertEquals(2, records.size)
        assertEquals("2026-07-27", records[0].date)
        assertEquals(20, records[0].words)
        assertEquals("new", records[0].kind)
        assertEquals("backlog", records[1].kind)
    }
}
