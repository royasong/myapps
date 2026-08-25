package com.example.arintabletusage.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.arintabletusage.data.AppPreferences
import com.example.arintabletusage.mail.EmailSender
import com.example.arintabletusage.notification.ReportNotifier
import com.example.arintabletusage.permissions.PermissionHelper
import com.example.arintabletusage.report.UsageReportBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 실제로 사용시간 리포트를 만들고 등록된 이메일로 발송하는 백그라운드 작업. */
class SendReportWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val prefs = AppPreferences(applicationContext)
        val recipient = prefs.recipientEmail
        val senderEmail = prefs.senderEmail
        val senderPassword = prefs.senderPassword

        if (recipient.isBlank() || senderEmail.isBlank() || senderPassword.isBlank()) {
            prefs.lastSendResult = "실패: 수신 이메일 또는 발신 계정 설정이 비어있습니다."
            prefs.lastSendAt = System.currentTimeMillis()
            ReportNotifier.notify(applicationContext, false, "이메일/발신 계정 설정을 확인해주세요.")
            return@withContext Result.failure()
        }

        if (!PermissionHelper.hasUsageAccess(applicationContext)) {
            prefs.lastSendResult = "실패: 사용 기록 접근 권한이 허용되어 있지 않습니다."
            prefs.lastSendAt = System.currentTimeMillis()
            ReportNotifier.notify(applicationContext, false, "사용 기록 접근 권한을 허용해주세요.")
            return@withContext Result.failure()
        }

        val report = UsageReportBuilder.buildTodayReport(applicationContext)
        val bodyText = UsageReportBuilder.buildReportText(report)
        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).format(Date())
        val subject = "[ArinTabletUsage] 태블릿 사용시간 리포트 $dateStr"

        val config = EmailSender.SmtpConfig(
            host = prefs.smtpHost,
            port = prefs.smtpPort,
            senderEmail = senderEmail,
            senderPassword = senderPassword
        )

        val result = EmailSender.sendTextMail(config, recipient, subject, bodyText)
        prefs.lastSendAt = System.currentTimeMillis()

        return@withContext if (result.isSuccess) {
            prefs.lastSendResult = "성공: $recipient 로 리포트 전송 완료"
            ReportNotifier.notify(applicationContext, true, "리포트를 $recipient 로 전송했습니다.")
            Result.success()
        } else {
            val msg = result.exceptionOrNull()?.message ?: "알 수 없는 오류"
            prefs.lastSendResult = "실패: $msg"
            ReportNotifier.notify(applicationContext, false, "전송 실패: $msg")
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }
}
