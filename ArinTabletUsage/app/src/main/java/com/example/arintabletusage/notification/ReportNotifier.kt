package com.example.arintabletusage.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/** 리포트 발송 성공/실패를 사용자에게 알려주는 간단한 알림. */
object ReportNotifier {
    private const val CHANNEL_ID = "usage_report_channel"
    private const val NOTIFICATION_ID = 2001

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "사용시간 리포트", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
    }

    fun notify(context: Context, success: Boolean, message: String) {
        ensureChannel(context)
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(if (success) "리포트 전송 완료" else "리포트 전송 실패")
            .setContentText(message)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }
}
