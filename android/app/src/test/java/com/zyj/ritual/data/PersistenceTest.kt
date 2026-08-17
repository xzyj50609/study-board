package com.zyj.ritual.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.zyj.ritual.core.time.BeijingClock
import com.zyj.ritual.data.local.AppDatabase
import com.zyj.ritual.data.local.entity.PaperSessionEntity
import com.zyj.ritual.data.repository.VocabRepository
import com.zyj.ritual.data.store.VocabConfigStore
import com.zyj.ritual.domain.vocab.VocabCalculator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
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
import java.util.TimeZone

/**
 * 「点了 → 数据真的进了硬盘 → 关掉重开还在」的关卡。
 *
 * ### 为什么非有不可
 *
 * 这个事故在本项目**真实发生过**：保存按钮没接线，JVM 单测全绿、构建成功、
 * APK 能装能开，数据一次都没落过盘。合并计划「哪些故障会伪装成正常」第 4 条
 * 就是照着它写的：**「每个写操作都要有『点了 → 关 APP → 重开 → 还在』的测试，
 * 不是只测 ViewModel 状态变了」**。
 *
 * 而在这个文件出现之前，背词打卡这条链路（今天页 +20 → ViewModel → Repository → Room）
 * **一个持久化测试都没有**。原来那 15 个 androidTest 用例要真机或模拟器，从来没跑过。
 *
 * ### 关键点：必须关库再开库
 *
 * 光写完再读一遍是不够的 —— Room 有内存缓存，写完立刻读**就算根本没落盘也能读到**，
 * 那正是"伪装成正常"。所以每个用例都走：写 → `close()` → 用**同一个文件**新建一个
 * 数据库实例 → 再读。这样读到的东西只可能来自磁盘。
 *
 * ⚠️ 别把这里的库改成 `inMemoryDatabaseBuilder`。那样测试会更快、也照样绿，
 * 但它恰恰把这个文件唯一想验的东西给绕过去了。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])   // compileSdk 是 37，Robolectric 还没跟上，钉一个它支持的
class PersistenceTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    /** 落在 Robolectric 的临时目录里，每个用例自己一份，互不干扰。 */
    private val dbFile: File = File.createTempFile("ritual-persistence", ".db").also { it.delete() }

    private var db: AppDatabase? = null

    /** 固定时钟。用真实时间会让「今天」跨零点时测试随机翻车。 */
    private val fixedClock = object : BeijingClock {
        override val zone: ZoneId = ZoneId.of("Asia/Shanghai")
        override fun now(): Instant =
            LocalDate.of(2026, 8, 6).atTime(9, 0).atZone(zone).toInstant()
        override fun dateFlow(): Flow<LocalDate> = flowOf(LocalDate.of(2026, 8, 6))
    }

    private fun openDb(): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, dbFile.absolutePath)
            .addMigrations(*AppDatabase.ALL_MIGRATIONS)
            .build()
            .also { db = it }

    private fun repo(database: AppDatabase) =
        VocabRepository(database, VocabConfigStore(context), fixedClock)

    @After
    fun tearDown() {
        db?.close()
        dbFile.delete()
    }

    @Test
    fun `背词打卡写进去之后 关掉数据库重新打开还在`() = runTest {
        // 相当于用户在今天页点了「+20 新词」
        val first = openDb()
        repo(first).addRecord(words = 20, kind = "new")
        first.close()   // 相当于把 APP 从后台划掉

        // 相当于重新打开 APP
        val second = openDb()
        val rows = second.vocabRecordDao().getAll()
        second.close()

        assertEquals("重开之后一条记录都没有，说明根本没落盘", 1, rows.size)
        assertEquals("2026-08-06", rows[0].date)
        assertEquals(20, rows[0].words)
        assertEquals("new", rows[0].kind)
    }

    @Test
    fun `连点多次加词 每一次都落盘 一条都不少`() = runTest {
        val first = openDb()
        val r = repo(first)
        r.addRecord(words = 20, kind = "new")
        r.addRecord(words = 20, kind = "review")
        r.addRecord(words = 15, kind = "new")
        first.close()

        val second = openDb()
        val rows = second.vocabRecordDao().getAll()
        second.close()

        assertEquals("点了 3 次，重开后条数对不上", 3, rows.size)
        assertEquals(55, rows.sumOf { it.words })
        assertEquals(listOf("new", "review", "new"), rows.map { it.kind })
    }

    @Test
    fun `导入指南灯备份之后 关掉重开数据还在且总数相等`() = runTest {
        val (config, records) = com.zyj.ritual.data.backup.BeiciBackupImporter.parseBeiciJson(
            """
            {
              "version": 2,
              "settings": { "totalWords": 1883, "startDate": "2026-07-27", "dailyWords": 20 },
              "records": [
                { "date": "2026-07-27", "words": 20, "kind": "new" },
                { "date": "2026-07-28", "words": 20, "kind": "new" },
                { "date": "2026-07-29", "words": 60, "kind": "new" },
                { "date": "2026-07-30", "words": 40, "kind": "backlog" }
              ]
            }
            """.trimIndent()
        )

        val first = openDb()
        repo(first).importBeiciData(config, records)
        first.close()

        val second = openDb()
        val rows = second.vocabRecordDao().getAll()
        second.close()

        // 合并计划「哪些故障会伪装成正常」第 1 条：迁移少了几天你看不出来
        assertEquals("导入的条数和重开后读到的对不上，有记录被吃掉了", records.size, rows.size)
        records.forEachIndexed { i, want ->
            assertEquals("第 ${i + 1} 条日期不对", want.date, rows[i].date)
            assertEquals("${want.date} 的词数不对", want.words, rows[i].words)
        }
    }

    /**
     * 公开仓库使用脱敏合成备份，保留真实导出暴露出的关键形状：34 条、总词数 776，
     * 且同一天分别出现 3 / 4 / 6 条记录。个人原始备份只保存在本地快照里。
     */
    @Test
    fun `多条同日备份导入后 关掉重开 总数和逐条都对得上`() = runTest {
        val rawJson = javaClass.classLoader!!
            .getResourceAsStream("synthetic-beici-backup.json")!!
            .bufferedReader(Charsets.UTF_8).readText()
        val (config, records) = com.zyj.ritual.data.backup.BeiciBackupImporter.parseBeiciJson(rawJson)

        // 先在导入这一步就钉死，免得后面数据库那步的失败混淆到底是哪层丢的
        assertEquals("解析出的条数和原始 JSON 对不上", 34, records.size)
        assertEquals("解析出的总词数和原始 JSON 对不上", 776, records.sumOf { it.words })
        assertEquals("2026-01-01", config.startDate)
        assertEquals(2416, config.totalWords)

        val first = openDb()
        repo(first).importBeiciData(config, records)
        first.close()

        val second = openDb()
        val rows = second.vocabRecordDao().getAll()
        second.close()

        assertEquals("导入 34 条，重开后条数对不上，有记录被吃掉了", 34, rows.size)
        assertEquals("总词数对不上", 776, rows.sumOf { it.words })
        // 同一天多条这个真实情况：逐条比，不能被去重或合并
        assertEquals(3, rows.count { it.date == "2026-01-01" })
        assertEquals(4, rows.count { it.date == "2026-01-02" })
        assertEquals(6, rows.count { it.date == "2026-01-03" })
        records.forEachIndexed { i, want ->
            assertEquals("第 ${i + 1} 条日期不对", want.date, rows[i].date)
            assertEquals("第 ${i + 1} 条词数不对", want.words, rows[i].words)
            assertEquals("第 ${i + 1} 条 kind 不对", want.kind, rows[i].kind)
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  背词打卡的时间戳
    // ════════════════════════════════════════════════════════════════

    @Test
    fun `加词记录带着写入时刻落盘 重开之后时刻还在`() = runTest {
        val first = openDb()
        repo(first).addRecord(words = 20, kind = "new")
        first.close()

        val second = openDb()
        val rows = second.vocabRecordDao().getAll()
        second.close()

        assertEquals(1, rows.size)
        // 时刻必须来自注入的时钟，不是 System.currentTimeMillis()——
        // 后者测试里没法钉，也绕开了「全工程时间只有一个来源」这条约束
        assertEquals(
            "写入时刻对不上注入的固定时钟，说明用了别的时间源",
            fixedClock.now().toEpochMilli(),
            rows[0].createdAt,
        )
    }

    @Test
    fun `备份导入的老记录没有时刻 落成 0 而不是当前时间`() = runTest {
        val (config, records) = com.zyj.ritual.data.backup.BeiciBackupImporter.parseBeiciJson(
            """
            {
              "version": 2,
              "settings": { "totalWords": 1883, "startDate": "2026-07-27", "dailyWords": 20 },
              "records": [ { "date": "2026-07-27", "words": 20, "kind": "new" } ]
            }
            """.trimIndent()
        )

        val first = openDb()
        repo(first).importBeiciData(config, records)
        first.close()

        val second = openDb()
        val rows = second.vocabRecordDao().getAll()
        second.close()

        // 备份 JSON 里根本没有时刻。拿导入那一刻的时间去填，会让 7 月 27 号那条
        // 在历史页上显示成今天的时刻——看着完全正常，实际是编的
        assertEquals("备份里没有的时刻不许编一个出来", 0L, rows[0].createdAt)
    }

    // ════════════════════════════════════════════════════════════════
    //  今天要复习多少：校准值落盘 + 时区
    // ════════════════════════════════════════════════════════════════

    /**
     * 这条守的是「哪些故障会伪装成正常」第 1 条。
     *
     * `saveReviewDue` 要把「今天」盖成北京时间的今天。如果哪天有人改成用设备时区
     * （`LocalDate.now()` 之类），在本机（Pacific，比北京晚 15~16 小时）就会存成昨天，
     * `reviewDueToday` 退回 null，复习那条进度条整天画不出来。
     * 而屏幕上看起来只是「你今天还没填」——**一点都不像故障**。
     *
     * 所以这条特意把 JVM 默认时区掰到 Pacific，再挑一个「北京已经是 8 号、
     * Pacific 还是 7 号」的时刻。用错时间源就会红。
     *
     * 用两个各自独立、指向同一个文件的 DataStore 实例来读写：
     * `preferencesDataStore(name=...)` 那个委托对同一个 Context 返回同一个实例，
     * 拿它测「重开还在」会被内存缓存糊弄过去——跟这个文件开头写的
     * 「别用 inMemoryDatabaseBuilder」是同一个道理。
     */
    @Test
    fun `今天要复习多少 存完关掉重开还在 且按北京时间算今天`() = runTest {
        val originalTz = TimeZone.getDefault()
        // 本机时区故意设成 Pacific，跟北京差 15 小时
        TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
        val prefsFile = File.createTempFile("vocab-config", ".preferences_pb")
            .also { it.delete() }

        // 北京 2026-08-08 07:00 == Pacific 2026-08-07 16:00（还是前一天）
        val beijing = ZoneId.of("Asia/Shanghai")
        val morningClock = object : BeijingClock {
            override val zone: ZoneId = beijing
            override fun now(): Instant =
                LocalDate.of(2026, 8, 8).atTime(7, 0).atZone(beijing).toInstant()
            override fun dateFlow(): Flow<LocalDate> = flowOf(LocalDate.of(2026, 8, 8))
        }

        try {
            // 前置断言：这个时刻在设备时区下确实还是 8 月 7 号，
            // 否则这条用例根本没在测它想测的东西
            assertEquals(
                "时区没掰过去，这条用例失去意义",
                LocalDate.of(2026, 8, 7),
                LocalDate.ofInstant(morningClock.now(), TimeZone.getDefault().toZoneId()),
            )

            // ── 存 ──
            val writeScope = CoroutineScope(Dispatchers.IO + Job())
            val dbA = openDb()
            VocabRepository(
                dbA,
                VocabConfigStore(PreferenceDataStoreFactory.create(scope = writeScope) { prefsFile }),
                morningClock,
            ).saveReviewDue(45)
            dbA.close()
            // DataStore 同一个文件不许有两个活实例，先把写那边彻底停掉
            writeScope.cancel()

            // ── 相当于把 APP 划掉重开：全新的 DataStore 实例，同一个文件 ──
            val readScope = CoroutineScope(Dispatchers.IO + Job())
            val dbB = openDb()
            val storeB = VocabConfigStore(
                PreferenceDataStoreFactory.create(scope = readScope) { prefsFile }
            )
            val config = storeB.getConfig()
            dbB.close()
            readScope.cancel()

            assertEquals("填的 45 没落盘", 45, config.reviewDue)
            assertEquals(
                "存的日期不是北京时间的今天。用设备时区的话这里会是 2026-08-07，" +
                    "复习那条进度条整天都画不出来，而屏幕上看着只像是你还没填",
                "2026-08-08",
                config.reviewDueDate,
            )

            // ── 真正要的效果：算出来的 reviewDueToday 不是 null，条子画得出来 ──
            val state = VocabCalculator.computeState(
                records = emptyList(),
                settings = config,
                todayStr = "2026-08-08",
            )
            assertEquals(45, state.reviewDueToday)
            assertEquals(45, state.reviewLeftToday)
        } finally {
            TimeZone.setDefault(originalTz)
            prefsFile.delete()
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  数据库迁移 v2 → v3
    // ════════════════════════════════════════════════════════════════

    /**
     * 这条守的是「哪些故障会伪装成正常」第 5 条：
     * 迁移把 `vocab_record` 的内容清空了，App 照常能开、不崩不报错，
     * 只是历史空了、累计复习数从 544 掉回 0。
     *
     * 做法是**直接拿生产用的那个 `MIGRATION_2_3` 对象**去跑一张真的 v2 表，
     * 用脱敏合成备份灌 34 条进去。改成 DROP TABLE 就会红。
     *
     * ⚠️ 这条**不覆盖** Room 的 schema 校验（identityHash 对不对、
     * 列类型一不一致）。那类问题 Room 自己会在打开时抛异常，是响的，不是哑的。
     */
    @Test
    fun `迁移到 v3 之后 合成备份的 34 条一条不少`() {
        val rawJson = javaClass.classLoader!!
            .getResourceAsStream("synthetic-beici-backup.json")!!
            .bufferedReader(Charsets.UTF_8).readText()
        val (_, records) =
            com.zyj.ritual.data.backup.BeiciBackupImporter.parseBeiciJson(rawJson)
        assertEquals(34, records.size)

        val v2File = File.createTempFile("ritual-migration", ".db").also { it.delete() }
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(v2File.absolutePath)
                .callback(object : SupportSQLiteOpenHelper.Callback(2) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        // v2 的 vocab_record：没有 createdAt 这一列
                        db.execSQL(
                            """
                            CREATE TABLE IF NOT EXISTS `vocab_record` (
                                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                `date` TEXT NOT NULL,
                                `words` INTEGER NOT NULL,
                                `kind` TEXT NOT NULL
                            )
                            """.trimIndent()
                        )
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, old: Int, new: Int) = Unit
                })
                .build()
        )

        try {
            val db = helper.writableDatabase
            records.forEach { r ->
                db.execSQL(
                    "INSERT INTO vocab_record (date, words, kind) VALUES (?, ?, ?)",
                    arrayOf<Any>(r.date, r.words, r.kind),
                )
            }

            // 迁移前先确认数据确实在，免得后面的失败其实是"根本没灌进去"
            db.query("SELECT COUNT(*) FROM vocab_record").use {
                it.moveToFirst()
                assertEquals("v2 库里就没灌进去，这条用例失去意义", 34, it.getInt(0))
            }

            // ── 跑生产用的那个迁移 ──
            AppDatabase.MIGRATION_2_3.migrate(db)

            db.query("SELECT date, words, kind, createdAt FROM vocab_record ORDER BY id").use { c ->
                assertEquals("迁移之后记录被清空了，而 App 照常能开，你看不出来", 34, c.count)
                var total = 0
                var i = 0
                while (c.moveToNext()) {
                    assertEquals("第 ${i + 1} 条日期在迁移中变了", records[i].date, c.getString(0))
                    assertEquals("第 ${i + 1} 条词数在迁移中变了", records[i].words, c.getInt(1))
                    assertEquals("第 ${i + 1} 条 kind 在迁移中变了", records[i].kind, c.getString(2))
                    // 老记录不知道几点，落 0；历史页据此不渲染时刻
                    assertEquals("老记录的时刻应该是 0，不许编一个出来", 0L, c.getLong(3))
                    total += c.getInt(1)
                    i++
                }
                assertEquals("总词数对不上", 776, total)
            }
        } finally {
            helper.close()
            v2File.delete()
        }
    }

    @Test
    fun `读文章勾选任务之后 关掉重开还在`() = runTest {
        val first = openDb()
        first.taskRecordDao().insert(
            com.zyj.ritual.data.local.entity.TaskRecordEntity(
                id = "1-1",
                articleIndex = 1,
                taskIndex = 1,
                completedAt = fixedClock.now().toString(),
                plannedDate = "2026-08-06",
                source = "CHECKED",
            )
        )
        first.close()

        val second = openDb()
        val rows = second.taskRecordDao().getAll()
        second.close()

        assertEquals("勾了一项，重开后没了", 1, rows.size)
        assertEquals("1-1", rows[0].id)
    }

    // ════════════════════════════════════════════════════════════════
    //  整套卷登记 + 数据库迁移 v3 → v4
    // ════════════════════════════════════════════════════════════════

    @Test
    fun `登记整套卷之后 关掉重开还在`() = runTest {
        val first = openDb()
        first.paperSessionDao().insert(
            PaperSessionEntity(
                name = "2016 年卷",
                completedDate = "2026-08-13",
                partsCount = 7,
                digestionDays = 4,
                createdAt = fixedClock.now().toEpochMilli(),
            )
        )
        first.close()

        val second = openDb()
        val rows = second.paperSessionDao().getAll()
        second.close()

        assertEquals("登记了一套卷，重开后没了", 1, rows.size)
        assertEquals("2016 年卷", rows[0].name)
        assertEquals("2026-08-13", rows[0].completedDate)
        assertEquals(7, rows[0].partsCount)
        assertEquals(4, rows[0].digestionDays)
    }

    @Test
    fun `迁移到 v4 之后 paper_session 表存在`() {
        val v3File = File.createTempFile("ritual-migration-v3", ".db").also { it.delete() }
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(v3File.absolutePath)
                .callback(object : SupportSQLiteOpenHelper.Callback(3) {
                    override fun onCreate(db: SupportSQLiteDatabase) = Unit
                    override fun onUpgrade(db: SupportSQLiteDatabase, old: Int, new: Int) = Unit
                })
                .build()
        )

        try {
            val db = helper.writableDatabase
            AppDatabase.MIGRATION_3_4.migrate(db)
            db.query(
                "SELECT name FROM sqlite_master WHERE type='table' AND name='paper_session'"
            ).use {
                it.moveToFirst()
                assertEquals("v4 迁移没有建出 paper_session 表", "paper_session", it.getString(0))
            }
        } finally {
            helper.close()
            v3File.delete()
        }
    }
}
