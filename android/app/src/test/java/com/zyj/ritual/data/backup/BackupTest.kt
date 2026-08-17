package com.zyj.ritual.data.backup

import com.zyj.ritual.data.repository.ExportData
import com.zyj.ritual.domain.model.HistoryEvent
import com.zyj.ritual.domain.model.HistoryEventType
import com.zyj.ritual.domain.model.Plan
import com.zyj.ritual.domain.model.PaperSession
import com.zyj.ritual.domain.model.RecordSource
import com.zyj.ritual.domain.model.TaskRecord
import com.zyj.ritual.domain.vocab.RateChange
import com.zyj.ritual.domain.vocab.VocabConfig
import com.zyj.ritual.domain.vocab.VocabRecord
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.*
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

class BackupTest {

    @Test
    fun `v3 backup encode and decode roundtrip with vocab data`() {
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
                VocabRecord("2026-07-27", 20, "new", createdAt = 1234),
                VocabRecord("2026-07-28", 40, "backlog")
            )
        )

        val jsonStr = BackupSerializer.encode(exportData)
        assertTrue(jsonStr.contains("\"formatVersion\": 3"))
        assertTrue(jsonStr.contains("\"vocabConfig\""))

        val decoded = BackupSerializer.decode(jsonStr)
        assertEquals(42, decoded.plan.totalArticles)
        assertNotNull(decoded.vocabConfig)
        assertEquals(1883, decoded.vocabConfig?.totalWords)
        assertEquals(2, decoded.vocabRecords?.size)
        assertEquals("2026-07-27", decoded.vocabRecords?.get(0)?.date)
        assertEquals(1234L, decoded.vocabRecords?.get(0)?.createdAt)
    }

    @Test
    fun `v3 encode rejects backup missing vocab data`() {
        val incomplete = sampleExportData(vocabConfig = null, vocabRecords = null)

        assertThrows(IllegalArgumentException::class.java) {
            BackupSerializer.encode(incomplete)
        }
    }

    @Test
    fun `v3 decode rejects backup missing vocab data`() {
        val raw = BackupSerializer.encode(sampleExportData())
            .let { withoutKeys(it, "vocabConfig", "vocabRecords") }

        assertThrows(IllegalArgumentException::class.java) {
            BackupSerializer.decode(raw)
        }
    }

    @Test
    fun `v2 backup missing vocab data remains importable`() {
        val raw = BackupSerializer.encode(sampleExportData())
            .replace("\"formatVersion\": 3", "\"formatVersion\": 2")
            .let { withoutKeys(it, "vocabConfig", "vocabRecords") }

        val decoded = BackupSerializer.decode(raw)

        assertNull(decoded.vocabConfig)
        assertNull(decoded.vocabRecords)
    }

    @Test
    fun `backup with only one vocab field is rejected`() {
        val raw = BackupSerializer.encode(sampleExportData())
            .replace("\"formatVersion\": 3", "\"formatVersion\": 2")
            .let { withoutKeys(it, "vocabRecords") }

        assertThrows(IllegalArgumentException::class.java) {
            BackupSerializer.decode(raw)
        }
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

    @Test
    fun `backup roundtrip preserves planStartDone and paper sessions`() {
        val data = sampleExportData(
            vocabConfig = VocabConfig(
                totalWords = 2416,
                initialDone = 380,
                dailyWords = 40,
                rateChanges = listOf(RateChange("2026-08-02", 40)),
                planStartDone = 420,
            ),
            vocabRecords = listOf(VocabRecord("2026-08-13", 40, "new")),
        ).copy(
            paperSessions = listOf(
                PaperSession(
                    name = "2016 年卷",
                    completedDate = LocalDate.parse("2026-08-13"),
                    partsCount = 7,
                    digestionDays = 4,
                    createdAt = Instant.parse("2026-08-13T14:00:00Z"),
                )
            ),
        )

        val decoded = BackupSerializer.decode(BackupSerializer.encode(data))

        assertEquals(420, decoded.vocabConfig?.planStartDone)
        assertEquals(listOf(RateChange("2026-08-02", 40)), decoded.vocabConfig?.rateChanges)
        assertEquals(1, decoded.paperSessions.size)
        assertEquals("2016 年卷", decoded.paperSessions[0].name)
        assertEquals(LocalDate.parse("2026-08-13"), decoded.paperSessions[0].completedDate)
        assertEquals(7, decoded.paperSessions[0].partsCount)
        assertEquals(4, decoded.paperSessions[0].digestionDays)
        assertEquals(Instant.parse("2026-08-13T14:00:00Z"), decoded.paperSessions[0].createdAt)
    }

    private fun sampleExportData(
        vocabConfig: VocabConfig? = VocabConfig(totalWords = 2416, initialDone = 380),
        vocabRecords: List<VocabRecord>? = listOf(VocabRecord("2026-08-13", 40, "new")),
    ): ExportData = ExportData(
        plan = Plan(
            totalArticles = 42,
            tasksPerArticle = 6,
            startArticle = 1,
            completedBeforeStart = 0,
            planStartDate = LocalDate.parse("2026-08-01"),
            daysPerArticle = 2,
            studyWeekdays = DayOfWeek.entries.toSet(),
            timezone = "Asia/Shanghai",
        ),
        records = emptyList(),
        history = emptyList(),
        exportedAt = Instant.parse("2026-08-13T14:36:00Z"),
        vocabConfig = vocabConfig,
        vocabRecords = vocabRecords,
    )

    private fun withoutKeys(raw: String, vararg keys: String): String {
        val objectValue = Json.parseToJsonElement(raw) as JsonObject
        return JsonObject(objectValue.filterKeys { it !in keys }).toString()
    }
}
