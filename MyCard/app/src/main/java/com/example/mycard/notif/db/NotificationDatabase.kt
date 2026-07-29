package com.example.mycard.notif.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [NotificationEntity::class],
    version = 3,
    exportSchema = false
)
abstract class NotificationDatabase : RoomDatabase() {
    abstract fun notificationDao(): NotificationDao

    companion object {
        /** 카드사 앱의 동일 알림 중복 발송을 걸러내는 시간 창 (그 이상 떨어진 동일 문구는 별건으로 적재) */
        const val DEDUPE_WINDOW_MS = 5_000L

        private const val DUP_INDEX = "index_notifications_pkg_title_text_bigText"

        /**
         * (pkg,title,text,bigText) UNIQUE 인덱스를 일반 인덱스로 교체.
         * UNIQUE 때문에 매달 문구가 동일한 자동납부 알림이 유실되던 문제를 해소한다.
         * 기존 알림 이력은 그대로 보존한다.
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP INDEX IF EXISTS `$DUP_INDEX`")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `$DUP_INDEX` " +
                        "ON `notifications` (`pkg`, `title`, `text`, `bigText`)"
                )
            }
        }

        @Volatile
        private var INSTANCE: NotificationDatabase? = null

        fun get(context: Context): NotificationDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    NotificationDatabase::class.java,
                    "notifications.db"
                )
                    .addMigrations(MIGRATION_2_3)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
