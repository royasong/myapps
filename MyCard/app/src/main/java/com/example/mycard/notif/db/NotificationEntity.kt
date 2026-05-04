package com.example.mycard.notif.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "notifications",
    indices = [
        Index(value = ["ts"]),
        Index(value = ["pkg", "title", "text", "bigText"], unique = true)
    ]
)
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val ts: Long,
    val pkg: String,
    val title: String = "",
    val text: String = "",
    val bigText: String = "",
    val subText: String = "",
    val category: String = "",
    val channelId: String = "",
    val rawExtras: String = "{}",
    val amount: Long? = null,
    val merchant: String? = null,
    val parsedAt: Long? = null,
    val filterId: String? = null
)
