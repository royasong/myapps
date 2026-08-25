package com.example.arintabletusage.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.arintabletusage.data.AppPreferences
import com.example.arintabletusage.worker.SendReportWorker

/**
 * 예약된 시각에 울리는 알람을 받아 리포트 발송 작업을 WorkManager에 위임하고,
 * 다음 날 같은 시각으로 알람을 재예약해서 매일 반복되도록 한다.
 */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = AppPreferences(context)

        val request = OneTimeWorkRequestBuilder<SendReportWorker>().build()
        WorkManager.getInstance(context).enqueue(request)

        if (prefs.scheduleEnabled) {
            ReportScheduler.schedule(context, prefs.scheduleHour, prefs.scheduleMinute)
        }
    }
}
