package com.zyj.ritual.data.backup

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.zyj.ritual.core.time.BeijingClock
import com.zyj.ritual.data.local.AppDatabase
import com.zyj.ritual.data.local.entity.TaskRecordEntity
import com.zyj.ritual.data.local.entity.VocabRecordEntity
import com.zyj.ritual.data.repository.BackupRepository
import com.zyj.ritual.data.repository.ExportData
import com.zyj.ritual.data.store.PlanStore
import com.zyj.ritual.data.store.VocabConfigStore
import com.zyj.ritual.domain.model.Plan
import com.zyj.ritual.domain.vocab.VocabConfig
import com.zyj.ritual.domain.vocab.VocabRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupRepositoryTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val dbFile = File.createTempFile("backup-repository", ".db").also { it.delete() }
    private val planFile = File.createTempFile("backup-plan", ".preferences_pb").also { it.delete() }
    private val vocabFile = File.createTempFile("backup-vocab", ".preferences_pb").also { it.delete() }
    private val storeScope = CoroutineScope(Dispatchers.IO + Job())
    private val planStore = PlanStore(PreferenceDataStoreFactory.create(scope = storeScope) { planFile })
    private val vocabStore = VocabConfigStore(PreferenceDataStoreFactory.create(scope = storeScope) { vocabFile })
    private var db: AppDatabase? = null

    private val clock = object : BeijingClock {
        override val zone: ZoneId = ZoneId.of("Asia/Shanghai")
        override fun now(): Instant = Instant.parse("2026-08-13T14:36:00Z")
        override fun dateFlow(): Flow<LocalDate> = flowOf(LocalDate.parse("2026-08-13"))
    }

    private fun openDb(): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, dbFile.absolutePath)
            .addMigrations(*AppDatabase.ALL_MIGRATIONS)
            .build()
            .also { db = it }

    private fun repository(database: AppDatabase) =
        BackupRepository(database, planStore, vocabStore, clock)

    @After
    fun tearDown() {
        db?.close()
        storeScope.cancel()
        dbFile.delete()
        planFile.delete()
        vocabFile.delete()
    }

    @Test
    fun `export always includes vocab config records and timestamps`() = runTest {
        val database = openDb()
        planStore.savePlan(samplePlan())
        vocabStore.saveConfig(VocabConfig(totalWords = 2416, dailyWords = 40))
        database.vocabRecordDao().insert(
            VocabRecordEntity(
                date = "2026-08-13",
                words = 40,
                kind = "new",
                createdAt = 1_723_559_760_000,
            )
        )

        val exported = repository(database).exportAll()

        assertNotNull(exported.vocabConfig)
        assertEquals(2416, exported.vocabConfig?.totalWords)
        assertEquals(1, exported.vocabRecords?.size)
        assertEquals(1_723_559_760_000, exported.vocabRecords?.single()?.createdAt)
    }

    @Test
    fun `v3 import replaces article and vocab data and survives database reopen`() = runTest {
        val first = openDb()
        planStore.savePlan(samplePlan())
        vocabStore.saveConfig(VocabConfig(totalWords = 1000))
        first.taskRecordDao().insert(
            TaskRecordEntity("old", 1, 1, null, null, "IMPORTED")
        )
        first.vocabRecordDao().insert(VocabRecordEntity(date = "2026-08-01", words = 1, kind = "new"))

        repository(first).importAll(
            ExportData(
                plan = samplePlan().copy(totalArticles = 50),
                records = emptyList(),
                history = emptyList(),
                exportedAt = clock.now(),
                vocabConfig = VocabConfig(totalWords = 2416, dailyWords = 40),
                vocabRecords = listOf(VocabRecord("2026-08-13", 60, "new", createdAt = 1234)),
            )
        )
        first.close()

        val second = openDb()
        assertEquals(0, second.taskRecordDao().getAll().size)
        val vocabRows = second.vocabRecordDao().getAll()
        assertEquals(1, vocabRows.size)
        assertEquals(60, vocabRows.single().words)
        assertEquals(1234, vocabRows.single().createdAt)
        assertEquals(50, planStore.getPlan()?.totalArticles)
        assertEquals(2416, vocabStore.getConfig().totalWords)
    }

    @Test
    fun `legacy import without vocab preserves existing vocab`() = runTest {
        val database = openDb()
        planStore.savePlan(samplePlan())
        vocabStore.saveConfig(VocabConfig(totalWords = 2416, dailyWords = 40))
        database.vocabRecordDao().insert(
            VocabRecordEntity(date = "2026-08-13", words = 40, kind = "new")
        )

        repository(database).importAll(
            ExportData(
                plan = samplePlan().copy(totalArticles = 50),
                records = emptyList(),
                history = emptyList(),
                exportedAt = clock.now(),
            )
        )

        assertEquals(1, database.vocabRecordDao().getAll().size)
        assertEquals(40, database.vocabRecordDao().getAll().single().words)
        assertEquals(2416, vocabStore.getConfig().totalWords)
    }

    private fun samplePlan() = Plan(
        totalArticles = 42,
        tasksPerArticle = 6,
        startArticle = 1,
        completedBeforeStart = 0,
        planStartDate = LocalDate.parse("2026-08-01"),
        daysPerArticle = 2,
        studyWeekdays = DayOfWeek.entries.toSet(),
        timezone = "Asia/Shanghai",
    )
}
