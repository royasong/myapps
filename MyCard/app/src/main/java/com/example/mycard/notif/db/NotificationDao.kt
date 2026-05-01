package com.example.mycard.notif.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: NotificationEntity): Long

    @Query("SELECT * FROM notifications ORDER BY ts DESC")
    fun observeAll(): Flow<List<NotificationEntity>>

    @Query("SELECT * FROM notifications WHERE amount IS NOT NULL AND ts >= :sinceTs ORDER BY ts DESC")
    fun observeParsedSince(sinceTs: Long): Flow<List<NotificationEntity>>

    @Query("SELECT COUNT(*) FROM notifications")
    fun observeCount(): Flow<Int>

    @Query("DELETE FROM notifications WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM notifications WHERE pkg = :pkg")
    suspend fun deleteByPkg(pkg: String)

    @Query("DELETE FROM notifications")
    suspend fun clear()
}
