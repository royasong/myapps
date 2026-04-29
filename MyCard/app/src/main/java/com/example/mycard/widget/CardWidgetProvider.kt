package com.example.mycard.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.SharedPreferences
import android.content.Intent
import android.widget.RemoteViews
import com.example.mycard.R
import com.example.mycard.MainActivity
import com.example.mycard.ui.theme.SMSReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CardWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        
        // 새로고침 버튼 클릭 — DB 쿼리를 백그라운드 스레드에서 실행
        if (intent.action == "com.example.mycard.WIDGET_REFRESH") {
            val pending = goAsync()
            kotlin.concurrent.thread {
                try {
                    com.example.mycard.SmsReceiver.refreshAndNotify(context)
                } finally {
                    pending.finish()
                }
            }
        } else {
            super.onReceive(context, intent)
        }
    }

    companion object {
        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val prefs: SharedPreferences = context.getSharedPreferences("mycard_prefs", Context.MODE_PRIVATE)
            val totalAmount = prefs.getLong("widget_total", 0L)
            val groupsJson = prefs.getString("widget_groups", "[]") ?: "[]"
            val todayCount = prefs.getInt("widget_today_count", 0)
            val totalCount = prefs.getInt("widget_total_count", 0)

            val views = RemoteViews(context.packageName, R.layout.widget_card)
            views.setTextViewText(R.id.widget_total, "%,d원".format(totalAmount))
            views.setTextViewText(R.id.widget_title, "이번 달 카드 결제 총액 ($todayCount/$totalCount)")

            // 그룹 데이터 파싱
            val groups = parseGroupsJson(groupsJson)

            // 설정 입력 순서로 정렬
            val cardGroupStr = prefs.getString("cardGroup", "") ?: ""
            val settingsOrder = cardGroupStr.split("\n")
                .mapNotNull { line ->
                    val parts = line.trim().split(",")
                    if (parts.size >= 2) parts[1].trim() else null
                }
            val sortedGroups = if (settingsOrder.isEmpty()) groups else {
                val orderMap = settingsOrder.withIndex().associate { (i, id) -> id to i }
                groups.sortedBy { (id, _) -> orderMap[id] ?: Int.MAX_VALUE }
            }

            // 그룹 목록을 텍스트로 표시 (최대 5개)
            val groupsText = StringBuilder()
            sortedGroups.take(5).forEach { (id, amount) ->
                groupsText.append("${id}: %,d원\n".format(amount))
            }
            if (sortedGroups.size > 5) {
                groupsText.append("... 외 ${sortedGroups.size - 5}개")
            }

            views.setTextViewText(R.id.widget_groups_text, groupsText.toString())

            // 위젯 클릭 시 앱 실행
            /*
            val intent = Intent(context, MainActivity::class.java)
            val pendingIntent = android.app.PendingIntent.getActivity(
                context, 0, intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_container, pendingIntent)
            */
            // 새로고침 버튼 클릭 시 브로드캐스트 전송
            val refreshIntent = Intent("com.example.mycard.WIDGET_REFRESH").apply {
                setPackage(context.packageName)
            }
            val refreshPendingIntent = android.app.PendingIntent.getBroadcast(
                context, 1, refreshIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_MUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_refresh_btn, refreshPendingIntent)
            views.setOnClickPendingIntent(R.id.widget_container, refreshPendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private val ID_REGEX = Regex("id:([^,]+)")
        private val TOTAL_REGEX = Regex("total:(\\d+)")

        private fun parseGroupsJson(json: String): List<Pair<String, Long>> {
            val groups = mutableListOf<Pair<String, Long>>()
            try {
                // JSON 파싱: [{"id":"name","total":1000},...]
                val items = json.removeSurrounding("[", "]").split("},{")
                for (item in items) {
                    val clean = item.replace("{", "").replace("}", "").replace("\"", "")
                    val idMatch = ID_REGEX.find(clean)
                    val totalMatch = TOTAL_REGEX.find(clean)
                    if (idMatch != null && totalMatch != null) {
                        val id = idMatch.groupValues[1].trim()
                        val total = totalMatch.groupValues[1].toLongOrNull() ?: 0L
                        groups.add(Pair(id, total))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return groups
        }
    }

    override fun onEnabled(context: Context) {}
    override fun onDisabled(context: Context) {}
}