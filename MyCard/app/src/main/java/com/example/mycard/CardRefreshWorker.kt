package com.example.mycard

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.mycard.sms.SMSReader
import com.example.mycard.widget.CardWidgetProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.os.Environment

class CardRefreshWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val context = applicationContext
            
            // SMS 읽기
            val groups = SMSReader.readCardApprovalGrouped(context)
            
            // 위젷 데이터 업데이트
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
            
            // 위젷 업데이트 알림
            val appWidgetManager = android.appwidget.AppWidgetManager.getInstance(context)
            val widgetComponentName = android.content.ComponentName(context, CardWidgetProvider::class.java)
            val widgetIds = appWidgetManager.getAppWidgetIds(widgetComponentName)
            appWidgetManager.notifyAppWidgetViewDataChanged(widgetIds, R.id.widget_total)
            
            // 텍스트 파일 저장
            val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.KOREA)
            val fileName = "card_approval_${dateFormat.format(Date())}.txt"
            
            val content = buildString {
                appendLine("=== 카드 승인 내역 ===")
                appendLine("저장일시: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.KOREA).format(Date())}")
                appendLine()
                
                appendLine("이번 달 총 승인: ${String.format("%,d", grandTotal)}원")
                appendLine("=".repeat(30))
                appendLine()
                
                groups.forEach { group ->
                    appendLine("${group.id}: ${String.format("%,d", group.totalAmount)}원")
                }
            }
            
            // 내부 저장소
            val internalFile = File(context.getExternalFilesDir(null), fileName)
            internalFile.writeText(content, Charsets.UTF_8)
            
            // Document 영역
            val docsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            if (docsDir != null) {
                val docFile = File(docsDir, fileName)
                docFile.writeText(content, Charsets.UTF_8)
            }
            
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }
}