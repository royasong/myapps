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
        Log.i(TAG, "rebuildFromRaw: start")
        // 디스크에서 최신 상태로 모두 다시 읽기
        CardFilterStore.invalidate()
        Whitelist.invalidate()
        Blacklist.invalidate()
        RawDump.invalidate()

        val objects = RawDump.readAllObjects(context)
        Log.i(TAG, "rebuildFromRaw: loaded ${objects.size} raw objects")
        if (objects.isEmpty()) {
            Log.w(TAG, "rebuildFromRaw: no raw objects, returning empty result")
            return@withContext RebuildResult(0, 0, 0, 0)
        }

        val dao = NotificationDatabase.get(context).notificationDao()
        dao.clear()
        Log.d(TAG, "rebuildFromRaw: db cleared")

        var rebuilt = 0
        var parsed = 0
        var skippedByBlacklist = 0
        var skippedByParseFail = 0

        for ((idx, obj) in objects.withIndex()) {
            val entity = parseObject(obj)
            if (entity == null) {
                Log.w(TAG, "rebuildFromRaw[$idx]: parseObject returned null")
                continue
            }

            if (Blacklist.contains(context, entity.pkg)) {
                Log.d(TAG, "rebuildFromRaw[$idx]: blacklisted pkg=${entity.pkg}")
                skippedByBlacklist++
                continue
            }

            val onWhitelist = Whitelist.contains(context, entity.pkg)
            val parseResult = if (onWhitelist) {
                CardParser.parse(context, entity)
            } else {
                Log.d(TAG, "rebuildFromRaw[$idx]: not whitelisted pkg=${entity.pkg}")
                null
            }

            val finalEntity = if (parseResult != null) {
                entity.copy(
                    amount = parseResult.amount,
                    merchant = parseResult.merchant,
                    parsedAt = System.currentTimeMillis(),
                    filterId = parseResult.filterId
                )
            } else {
                if (onWhitelist) {
                    Log.d(TAG, "rebuildFromRaw[$idx]: whitelisted but parse failed pkg=${entity.pkg} ts=${entity.ts}")
                    skippedByParseFail++
                }
                entity
            }

            try {
                val newId = dao.insert(finalEntity)
                if (newId == -1L) {
                    Log.d(TAG, "rebuildFromRaw[$idx]: dedupe-ignored pkg=${entity.pkg} ts=${entity.ts}")
                } else {
                    rebuilt++
                    if (parseResult != null) parsed++
                }
            } catch (e: Exception) {
                Log.w(TAG, "rebuildFromRaw[$idx]: insert failed id=${entity.id}", e)
            }
        }

        Log.i(
            TAG,
            "rebuildFromRaw: done rebuilt=$rebuilt parsed=$parsed skipBL=$skippedByBlacklist skipParseFail=$skippedByParseFail"
        )
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
