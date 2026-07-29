package com.example.mycard.notif.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: NotificationEntity): Long

    @Query(
        "SELECT COUNT(*) FROM notifications WHERE pkg = :pkg AND title = :title " +
            "AND text = :text AND bigText = :bigText AND ts >= :fromTs AND ts <= :toTs"
    )
    suspend fun countNearDuplicates(
        pkg: String,
        title: String,
        text: String,
        bigText: String,
        fromTs: Long,
        toTs: Long
    ): Int

    /**
     * 같은 내용이 [windowMs] 안에 이미 있으면 -1을 반환하고 건너뛴다.
     * 카드사 앱이 동일 알림을 수 ms 간격으로 중복 발송하는 것만 걸러내고,
     * 매달 문구가 똑같은 자동납부 알림처럼 시간이 떨어진 건은 정상 적재한다.
     */
    @Transaction
    suspend fun insertIfNotRecentDuplicate(entity: NotificationEntity, windowMs: Long): Long {
        val near = countNearDuplicates(
            entity.pkg, entity.title, entity.text, entity.bigText,
            entity.ts - windowMs, entity.ts + windowMs
        )
        return if (near > 0) -1L else insert(entity)
    }

    @Query("SELECT * FROM notifications ORDER BY ts DESC")
    fun observeAll(): Flow<List<NotificationEntity>>

    @Query("SELECT * FROM notifications WHERE ts >= :sinceTs AND ts < :untilTs ORDER BY ts DESC")
    fun observeInRange(sinceTs: Long, untilTs: Long): Flow<List<NotificationEntity>>

    @Query("SELECT * FROM notifications WHERE amount IS NOT NULL AND ts >= :sinceTs AND ts < :untilTs ORDER BY ts DESC")
    fun observeParsedInRange(sinceTs: Long, untilTs: Long): Flow<List<NotificationEntity>>

    @Query("SELECT * FROM notifications WHERE amount IS NOT NULL AND ts >= :sinceTs ORDER BY ts DESC")
    fun observeParsedSince(sinceTs: Long): Flow<List<NotificationEntity>>

    @Query("SELECT * FROM notifications WHERE amount IS NOT NULL AND ts >= :sinceTs ORDER BY ts DESC")
    suspend fun getParsedSince(sinceTs: Long): List<NotificationEntity>

    @Query("SELECT COUNT(*) FROM notifications")
    fun observeCount(): Flow<Int>

    @Query("DELETE FROM notifications WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM notifications WHERE pkg = :pkg")
    suspend fun deleteByPkg(pkg: String)

    @Query("DELETE FROM notifications")
    suspend fun clear()
}
