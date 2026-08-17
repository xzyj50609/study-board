package com.zyj.ritual

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.zyj.ritual.core.time.TestBeijingClock
import com.zyj.ritual.data.local.AppDatabase
import com.zyj.ritual.data.repository.BackupRepository
import com.zyj.ritual.data.repository.StudyRepository
import com.zyj.ritual.data.store.PlanStore
import com.zyj.ritual.data.store.VocabConfigStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 测试用的一整套隔离环境。
 *
 * 关键点：数据库走内存、DataStore 走临时文件。
 * **绝不能碰 App 真实的 datastore/plan.preferences_pb 和 ritual.db**——
 * 跑一次测试就把用户手机上的学习记录清了，那是比 bug 更糟的事。
 */
class TestEnv(fixedDate: LocalDate = LocalDate.of(2026, 8, 6)) {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val tempDir: File = File.createTempFile("ritual-test", "").let {
        it.delete()
        it.mkdirs()
        it
    }

    val clock = TestBeijingClock(
        fixedDate.atTime(9, 0).atZone(ZoneId.of("Asia/Shanghai")).toInstant()
    )

    val db: AppDatabase = Room
        .inMemoryDatabaseBuilder(context, AppDatabase::class.java)
        .build()

    private val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = scope) {
        File(tempDir, "plan-${System.nanoTime()}.preferences_pb")
    }

    private val planStore = PlanStore(dataStore)
    val repository = StudyRepository(db, planStore, clock)
    val backupRepository = BackupRepository(db, planStore, VocabConfigStore(dataStore), clock)

    fun advanceTo(date: LocalDate) {
        clock.setInstant(date.atTime(9, 0).atZone(ZoneId.of("Asia/Shanghai")).toInstant())
    }

    fun close() {
        db.close()
        scope.cancel()
        tempDir.deleteRecursively()
    }

    companion object {
        fun instantAt(date: LocalDate): Instant =
            date.atTime(9, 0).atZone(ZoneId.of("Asia/Shanghai")).toInstant()
    }
}
