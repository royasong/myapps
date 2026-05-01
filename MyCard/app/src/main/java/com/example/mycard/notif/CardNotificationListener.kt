package com.example.mycard.notif

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.mycard.notif.db.NotificationDatabase
import com.example.mycard.notif.db.NotificationEntity
import com.example.mycard.parser.CardParser
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class CardNotificationListener : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val n = sbn.notification ?: return
        val pkg = sbn.packageName ?: return

        val extras = n.extras
        val rawExtrasJson = RawDump.bundleToJson(extras)
        val baseEntity = NotificationEntity(
            ts = sbn.postTime,
            pkg = pkg,
            title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),
            text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty(),
            bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty(),
            subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString().orEmpty(),
            category = n.category.orEmpty(),
            channelId = n.channelId.orEmpty(),
            rawExtras = rawExtrasJson
        )

        Log.d(TAG, "posted pkg=$pkg ts=${sbn.postTime} title=${baseEntity.title} body.len=${baseEntity.bigText.length.coerceAtLeast(baseEntity.text.length)}")

        val ctx = applicationContext
        scope.launch {
            try {
                RawDumpAll.appendObject(ctx, buildExternalObject(baseEntity, rawExtrasJson))
                Log.d(TAG, "raw all dump ok pkg=$pkg")
            } catch (e: Exception) {
                Log.w(TAG, "raw all dump failed pkg=$pkg", e)
            }

            if (Blacklist.contains(ctx, pkg)) {
                Log.d(TAG, "blacklisted pkg=$pkg, skipping db/whitelist")
                return@launch
            }

            try {
                val onWhitelist = Whitelist.contains(ctx, pkg)
                val parsed = if (onWhitelist) {
                    Log.d(TAG, "whitelisted pkg=$pkg, attempting parse")
                    CardParser.parse(ctx, baseEntity)
                } else {
                    Log.d(TAG, "not whitelisted pkg=$pkg, skipping parse")
                    null
                }

                val finalEntity = if (parsed != null) {
                    Log.i(TAG, "parsed pkg=$pkg amount=${parsed.amount} merchant=${parsed.merchant} filter=${parsed.filterId}")
                    baseEntity.copy(
                        amount = parsed.amount,
                        merchant = parsed.merchant,
                        parsedAt = System.currentTimeMillis(),
                        filterId = parsed.filterId
                    )
                } else {
                    if (onWhitelist) Log.d(TAG, "whitelisted but parse returned null pkg=$pkg")
                    baseEntity
                }

                val newId = NotificationDatabase.get(ctx).notificationDao().insert(finalEntity)
                if (newId == -1L) {
                    Log.i(TAG, "db insert dedupe-ignored pkg=$pkg title=${finalEntity.title}")
                } else {
                    Log.d(TAG, "db insert ok id=$newId pkg=$pkg parsed=${parsed != null}")
                    val withId = finalEntity.copy(id = newId)
                    try {
                        RawDump.appendObject(ctx, buildExternalObject(withId, rawExtrasJson))
                        Log.d(TAG, "raw dump ok pkg=$pkg id=$newId")
                    } catch (e: Exception) {
                        Log.w(TAG, "raw dump failed pkg=$pkg", e)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "db insert failed pkg=$pkg", e)
            }
        }
    }

    override fun onListenerConnected() { Log.i(TAG, "listener connected") }
    override fun onListenerDisconnected() { Log.i(TAG, "listener disconnected") }

    private fun buildExternalObject(entity: NotificationEntity, rawExtras: String): JsonObject {
        val obj = JsonObject()
        obj.addProperty("id", entity.id)
        obj.addProperty("ts", entity.ts)
        obj.addProperty("pkg", entity.pkg)
        obj.addProperty("title", entity.title)
        obj.addProperty("text", entity.text)
        obj.addProperty("bigText", entity.bigText)
        obj.addProperty("subText", entity.subText)
        obj.addProperty("category", entity.category)
        obj.addProperty("channelId", entity.channelId)
        obj.addProperty("rawExtras", rawExtras)
        return obj
    }

    companion object {
        private const val TAG = "CardNotifListener"
    }
}
