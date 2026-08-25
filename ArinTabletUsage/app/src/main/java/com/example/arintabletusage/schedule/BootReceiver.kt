package com.example.arintabletusage.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.arintabletusage.data.AppPreferences

/** 기기 재부팅 후에도 예약이 살아있도록 알람을 다시 걸어준다. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val prefs = AppPreferences(context)
        if (prefs.scheduleEnabled) {
            ReportScheduler.schedule(context, prefs.scheduleHour, prefs.scheduleMinute)
        }
    }
}
