package com.example.mycard

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.example.mycard.notif.readNotifCardGroups
import com.example.mycard.widget.CardWidgetProvider
import kotlinx.coroutines.runBlocking
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val senderAddress = messages[0].originatingAddress ?: return

        val fullBody = messages.joinToString("") { it.messageBody }
        if (!fullBody.contains("[Web발신]")) return
        if (!fullBody.contains("승인") && !fullBody.contains("취소")) return

        val prefs = context.getSharedPreferences("mycard_prefs", Context.MODE_PRIVATE)
        val cardGroupStr = prefs.getString("cardGroup", "") ?: ""

        val isCardSms = cardGroupStr.split("\n").any { line ->
            val parts = line.trim().split(",")
            if (parts.size >= 2) {
                val configPhone = parts[0].trim()
                senderAddress.contains(configPhone) || configPhone.contains(senderAddress)
            } else false
        }

        if (!isCardSms) return
        // goAsync()로 onReceive 종료 후에도 작업 유지, 스레드에서 DB 저장 대기 후 재조회
        val pending = goAsync()
        thread {
            try {
                Thread.sleep(2000)
                refreshAndNotify(context)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_SMS_UPDATED = "com.example.mycard.SMS_UPDATED"

        fun refreshAndNotify(context: Context) {
            try {
                val groups = runBlocking { readNotifCardGroups(context) }
                val grandTotal = groups.sumOf { it.totalAmount }
                val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).format(Date())
                val totalCount = groups.sumOf { it.items.size }
                val todayCount = groups.sumOf { g -> g.items.count { it.date.startsWith(todayStr) } }

                val groupsJson = StringBuilder("[")
                groups.forEachIndexed { index, group ->
                    groupsJson.append("{\"id\":\"${group.id}\",\"total\":${group.totalAmount}}")
                    if (index < groups.size - 1) groupsJson.append(",")
                }
                groupsJson.append("]")

                val prefs = context.getSharedPreferences("mycard_prefs", Context.MODE_PRIVATE)
                prefs.edit()
                    .putLong("widget_total", grandTotal)
                    .putInt("widget_today_count", todayCount)
                    .putInt("widget_total_count", totalCount)
                    .putString("widget_groups", groupsJson.toString())
                    .apply()

                val appWidgetManager = AppWidgetManager.getInstance(context)
                val widgetComponentName = ComponentName(context, CardWidgetProvider::class.java)
                val widgetIds = appWidgetManager.getAppWidgetIds(widgetComponentName)
                for (widgetId in widgetIds) {
                    CardWidgetProvider.updateAppWidget(context, appWidgetManager, widgetId)
                }

                // 앱이 실행 중인 경우 UI 갱신 요청
                context.sendBroadcast(
                    Intent(ACTION_SMS_UPDATED).setPackage(context.packageName)
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
