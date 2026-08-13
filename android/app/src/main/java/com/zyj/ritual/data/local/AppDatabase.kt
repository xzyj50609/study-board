package com.zyj.ritual.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.zyj.ritual.data.local.dao.HistoryEventDao
import com.zyj.ritual.data.local.dao.TaskRecordDao
import com.zyj.ritual.data.local.dao.VocabRecordDao
import com.zyj.ritual.data.local.entity.HistoryEventEntity
import com.zyj.ritual.data.local.entity.TaskRecordEntity
import com.zyj.ritual.data.local.entity.VocabRecordEntity

@Database(
    entities = [
        TaskRecordEntity::class,
        HistoryEventEntity::class,
        VocabRecordEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskRecordDao(): TaskRecordDao
    abstract fun historyEventDao(): HistoryEventDao
    abstract fun vocabRecordDao(): VocabRecordDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
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
        }

        /**
         * v3：给 vocab_record 加写入时刻，历史页要拿它跟读文章的事件混排。
         *
         * ⚠️ 只能 ALTER ADD COLUMN，绝不能 DROP/重建表——那会把用户几个月的
         * 背词记录悄悄清空，App 照常能开、不崩不报错，只是累计数掉回 0。
         * 老行的 createdAt 落成 0，表示"只知道哪天，不知道几点"，不编时刻。
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `vocab_record` ADD COLUMN `createdAt` INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        /** 所有迁移，按顺序。测试和生产共用同一份，防止两边走岔 */
        val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3)

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ritual.db",
                )
                    .addMigrations(*ALL_MIGRATIONS)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}

