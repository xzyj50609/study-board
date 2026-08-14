package com.zyj.ritual.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.zyj.ritual.core.time.BeijingClock
import com.zyj.ritual.data.local.AppDatabase
import com.zyj.ritual.data.local.entity.VocabRecordEntity
import com.zyj.ritual.data.repository.VocabRepository
import com.zyj.ritual.data.store.VocabConfigStore
import com.zyj.ritual.domain.vocab.VocabCalculator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 背词设置「存了 → 关掉 → 重开还在，而且一个数都没被改坏」的关卡。
 *
 * v1.0 的事故正好卡在这条链路上：设置页拿写死的默认配置当初值，
 * 用户只改了日期一按保存，词书总量就被从 2416 悄悄写回 1883。
 * 存是真存进去了，存的却是错的——所以光验"落盘了没有"不够，
 * 还得验"落下去的到底是不是用户填的那组数"。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VocabSetupPersistenceTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val dbFile = File.createTempFile("vocab-setup", ".db").also { it.delete() }
    private val prefsFile = File.createTempFile("vocab-setup", ".preferences_pb").also { it.delete() }
    private var db: AppDatabase? = null

    private val clock = object : BeijingClock {
        override val zone: ZoneId = ZoneId.of("Asia/Shanghai")
        override fun now(): Instant = Instant.parse("2026-08-14T02:00:00Z")
        override fun dateFlow(): Flow<LocalDate> = flowOf(LocalDate.parse("2026-08-14"))
    }

    private fun openDb(): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, dbFile.absolutePath)
            .addMigrations(*AppDatabase.ALL_MIGRATIONS)
            .build()
            .also { db = it }

    @After
    fun tearDown() {
        db?.close()
        dbFile.delete()
        prefsFile.delete()
    }

    /** 真实数据形状：起点 380，7 月底记了 40 个，8 月起 40/天 */
    private suspend fun seedRecords(database: AppDatabase) {
        listOf(
            "2026-07-30" to 20,
            "2026-07-31" to 20,
            "2026-08-02" to 40,
            "2026-08-03" to 40,
        ).forEach { (date, words) ->
            database.vocabRecordDao().insert(
                VocabRecordEntity(
                    date = date,
                    words = words,
                    kind = "new",
                    createdAt = clock.now().toEpochMilli(),
                )
            )
        }
    }

    @Test
    fun `保存设置之后 关掉重开 每个数都还是当时填的那个`() = runTest {
        val database = openDb()
        seedRecords(database)

        // —— 第一次：打开设置页填一组数并保存 ——
        val scope1 = CoroutineScope(Dispatchers.IO + Job())
        val store1 = VocabConfigStore(PreferenceDataStoreFactory.create(scope = scope1) { prefsFile })
        VocabRepository(database, store1, clock).saveSetup(
            bookName = "2027考研真题核心词汇",
            totalWords = 2416,
            planStartDate = "2026-08-02",
            planStartDone = 420,
            dailyWords = 40,
            examDate = "2026-12-19",
        )
        scope1.coroutineContext[Job]!!.cancelAndJoin()   // 相当于把 App 划掉

        // —— 第二次：重新打开，从磁盘读 ——
        val scope2 = CoroutineScope(Dispatchers.IO + Job())
        val store2 = VocabConfigStore(PreferenceDataStoreFactory.create(scope = scope2) { prefsFile })
        val config = store2.getConfig()
        scope2.coroutineContext[Job]!!.cancelAndJoin()
        database.close()

        assertEquals("词书总量被写回默认值了——v1.0 就是这么坏的", 2416, config.totalWords)
        assertEquals(40, config.dailyWords)
        assertEquals(420, config.planStartDone)
        assertEquals("2026-08-02", config.rateChanges.single().from)
        assertEquals("2026-12-19", config.examDate)
        // initialDone 是反推出来的：420 − 7 月底那 40 个
        assertEquals(380, config.initialDone)

        // 最要紧的一条：重开之后，进度算出来还是那个数
        val records = listOf(
            com.zyj.ritual.domain.vocab.VocabRecord("2026-07-30", 20, "new"),
            com.zyj.ritual.domain.vocab.VocabRecord("2026-07-31", 20, "new"),
            com.zyj.ritual.domain.vocab.VocabRecord("2026-08-02", 40, "new"),
            com.zyj.ritual.domain.vocab.VocabRecord("2026-08-03", 40, "new"),
        )
        val state = VocabCalculator.computeState(records, config, "2026-08-14")
        assertEquals(500, state.doneWords)          // 380 + 120
        assertEquals("2026-09-20", state.planFinishDate)
    }
}
