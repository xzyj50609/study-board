package com.zyj.ritual.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.zyj.ritual.core.time.BeijingClock
import com.zyj.ritual.data.local.AppDatabase
import com.zyj.ritual.data.repository.StudyRepository
import com.zyj.ritual.data.store.PlanStore
import com.zyj.ritual.domain.model.Plan
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 一次性补记 2026-08-13 那套卷的关卡。
 *
 * ### 背景
 * v1.0 说好「2016 年卷记为 8/13 完成、当天豁免、8/14–17 空着」，
 * 但代码里没有任何地方做这件事。用户装上就是一张暗铜色的缺额卡。
 *
 * ### 这里守的两个反方向的错
 * - **补不上**：装了新版还是欠账红卡 → 白改；
 * - **补多了**：用户自己撤销后又被插回来，或者本来就记过一套、现在变成两套
 *   → 白白多放四天假。这个错尤其阴：日历上看着一切正常，
 *   只有自己去数才知道多空了几天。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LegacyPaperSeedTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val dbFile = File.createTempFile("legacy-seed", ".db").also { it.delete() }
    private val planFile = File.createTempFile("legacy-seed-plan", ".preferences_pb").also { it.delete() }
    private val storeScope = CoroutineScope(Dispatchers.IO + Job())
    private val planStore = PlanStore(PreferenceDataStoreFactory.create(scope = storeScope) { planFile })
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

    private fun repo(database: AppDatabase) = StudyRepository(database, planStore, clock)

    private val existingPlan = Plan(
        totalArticles = 42,
        startArticle = 16,
        completedBeforeStart = 15,
        planStartDate = LocalDate.of(2026, 8, 5),
        daysPerArticle = 2,
    )

    @After
    fun tearDown() {
        db?.close()
        storeScope.cancel()
        dbFile.delete()
        planFile.delete()
    }

    @Test
    fun `老用户第一次启动新版 那套卷被补进去`() = runTest {
        planStore.savePlan(existingPlan)
        val database = openDb()

        repo(database).seedLegacyPaperSessionIfNeeded()

        val rows = database.paperSessionDao().getAll()
        database.close()

        assertEquals("那套卷没补进去，用户照旧看见欠账红卡", 1, rows.size)
        assertEquals("2026-08-13", rows[0].completedDate)
        assertEquals("2016 年卷", rows[0].name)
        assertEquals(7, rows[0].partsCount)
        assertEquals(4, rows[0].digestionDays)
    }

    @Test
    fun `补记只发生一次 反复启动不会越补越多`() = runTest {
        planStore.savePlan(existingPlan)
        val database = openDb()
        val repository = repo(database)

        repeat(5) { repository.seedLegacyPaperSessionIfNeeded() }

        val rows = database.paperSessionDao().getAll()
        database.close()
        assertEquals("反复启动补出了多套卷，白放好几个四天", 1, rows.size)
    }

    @Test
    fun `用户撤销之后 下次启动不会给他插回来`() = runTest {
        planStore.savePlan(existingPlan)
        val database = openDb()
        val repository = repo(database)

        repository.seedLegacyPaperSessionIfNeeded()
        val seeded = database.paperSessionDao().getAll().single()
        repository.undoPaperSession(seeded.id)

        // 相当于杀掉 App 再打开
        repository.seedLegacyPaperSessionIfNeeded()

        val rows = database.paperSessionDao().getAll()
        database.close()
        assertTrue("撤销按钮按了等于没按——被自动补回来了", rows.isEmpty())
    }

    @Test
    fun `已经自己记过一套卷 就不再补`() = runTest {
        planStore.savePlan(existingPlan)
        val database = openDb()
        val repository = repo(database)

        repository.registerPaperSession("我自己记的 2016", LocalDate.of(2026, 8, 13))
        repository.seedLegacyPaperSessionIfNeeded()

        val rows = database.paperSessionDao().getAll()
        database.close()
        assertEquals("在用户自己那条之外又补了一条，等于多放四天假", 1, rows.size)
        assertEquals("我自己记的 2016", rows[0].name)
    }

    @Test
    fun `全新用户的计划晚于那天 不该凭空多出别人的卷子`() = runTest {
        planStore.savePlan(existingPlan.copy(planStartDate = LocalDate.of(2026, 9, 1)))
        val database = openDb()

        repo(database).seedLegacyPaperSessionIfNeeded()

        val rows = database.paperSessionDao().getAll()
        database.close()
        assertTrue("新用户凭空多出一套 2016 年卷", rows.isEmpty())
    }

    @Test
    fun `还没建过计划时先不补 等设置完再说`() = runTest {
        val database = openDb()
        val repository = repo(database)

        repository.seedLegacyPaperSessionIfNeeded()
        assertTrue(database.paperSessionDao().getAll().isEmpty())

        // 用户这时才做首次设置，下次启动就应该补上
        planStore.savePlan(existingPlan)
        repository.seedLegacyPaperSessionIfNeeded()

        val rows = database.paperSessionDao().getAll()
        database.close()
        assertEquals("计划建好之后仍然没补上", 1, rows.size)
    }
}
