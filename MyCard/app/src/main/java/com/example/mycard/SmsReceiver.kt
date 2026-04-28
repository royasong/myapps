package com.example.mycard

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import com.example.mycard.ui.theme.SMSReader
import com.example.mycard.widget.CardWidgetProvider

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val senderAddress = messages[0].originatingAddress ?: return
        val fullBody = messages.joinToString("") { it.messageBody }

        if (!fullBody.contains("[Web발신]")) return

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

        // SMS_RECEIVED는 DB 저장 전에 발생하므로 저장 완료 후 재조회
        Handler(Looper.getMainLooper()).postDelayed({
            refreshAndNotify(context)
        }, 2000)
    }

    companion object {
        const val ACTION_SMS_UPDATED = "com.example.mycard.SMS_UPDATED"

        fun refreshAndNotify(context: Context) {
            try {
                val groups = SMSReader.readCardApprovalGrouped(context)
                val grandTotal = groups.sumOf { it.totalAmount }

                val prefs = context.getSharedPreferences("mycard_prefs", Context.MODE_PRIVATE)
                prefs.edit().putLong("widget_total", grandTotal).apply()

                val groupsJson = StringBuilder("[")
                groups.forEachIndexed { index, group ->
                    groupsJson.append("{\"id\":\"${group.id}\",\"total\":${group.totalAmount}}")
                    if (index < groups.size - 1) groupsJson.append(",")
                }
                groupsJson.append("]")
                prefs.edit().putString("widget_groups", groupsJson.toString()).apply()

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
