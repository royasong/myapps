package com.example.mycard.notif

import android.content.Context
import android.util.Log
import com.example.mycard.notif.db.NotificationDatabase
import com.example.mycard.notif.db.NotificationEntity
import com.example.mycard.parser.CardFilterStore
import com.example.mycard.parser.CardParser
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class RebuildResult(
    val rebuilt: Int,
    val parsed: Int,
    val skippedByBlacklist: Int,
    val skippedByParseFail: Int
)

object UpdateAction {

    private const val TAG = "UpdateAction"

    suspend fun rebuildFromRaw(context: Context): RebuildResult = withContext(Dispatchers.IO) {
        // 디스크에서 최신 상태로 모두 다시 읽기
        CardFilterStore.invalidate()
        Whitelist.invalidate()
        Blacklist.invalidate()
        RawDump.invalidate()

        val objects = RawDump.readAllObjects(context)
        if (objects.isEmpty()) {
            return@withContext RebuildResult(0, 0, 0, 0)
        }

        val dao = NotificationDatabase.get(context).notificationDao()
        dao.clear()

        var rebuilt = 0
        var parsed = 0
        var skippedByBlacklist = 0
        var skippedByParseFail = 0

        for (obj in objects) {
            val entity = parseObject(obj) ?: continue

            if (Blacklist.contains(context, entity.pkg)) {
                skippedByBlacklist++
                continue
            }

            val parseResult = if (Whitelist.contains(context, entity.pkg)) {
                CardParser.parse(context, entity)
            } else null

            val finalEntity = if (parseResult != null) {
                parsed++
                entity.copy(
                    amount = parseResult.amount,
                    merchant = parseResult.merchant,
                    parsedAt = System.currentTimeMillis()
                )
            } else {
                if (Whitelist.contains(context, entity.pkg)) skippedByParseFail++
                entity
            }

            try {
                dao.insert(finalEntity)
                rebuilt++
            } catch (e: Exception) {
                Log.w(TAG, "insert failed for id=${entity.id}", e)
            }
        }

        RebuildResult(
            rebuilt = rebuilt,
            parsed = parsed,
            skippedByBlacklist = skippedByBlacklist,
            skippedByParseFail = skippedByParseFail
        )
    }

    private fun parseObject(obj: JsonObject): NotificationEntity? {
        return try {
            NotificationEntity(
                id = obj.get("id")?.asLong ?: 0L,
                ts = obj.get("ts")?.asLong ?: 0L,
                pkg = obj.get("pkg")?.asString.orEmpty(),
                title = obj.get("title")?.asString.orEmpty(),
                text = obj.get("text")?.asString.orEmpty(),
                bigText = obj.get("bigText")?.asString.orEmpty(),
                subText = obj.get("subText")?.asString.orEmpty(),
                category = obj.get("category")?.asString.orEmpty(),
                channelId = obj.get("channelId")?.asString.orEmpty(),
                rawExtras = obj.get("rawExtras")?.asString ?: "{}"
            )
        } catch (e: Exception) {
            Log.w(TAG, "parseObject failed", e)
            null
        }
    }
}
