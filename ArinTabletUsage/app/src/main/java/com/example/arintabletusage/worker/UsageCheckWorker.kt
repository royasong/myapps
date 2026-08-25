package com.example.arintabletusage.worker

import android.content.Context
import android.provider.Settings
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.arintabletusage.data.AppPreferences
import com.example.arintabletusage.overlay.UsageWarningOverlay
import com.example.arintabletusage.permissions.PermissionHelper
import com.example.arintabletusage.report.UsageReportBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 매시간 실행되어 오늘 하루 총 사용 시간을 확인하고,
 * 설정된 경고 기준(기본 3시간)을 넘으면 경고 오버레이를 띄운다.
 */
class UsageCheckWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = AppPreferences(applicationContext)

        if (!PermissionHelper.hasUsageAccess(applicationContext)) {
            return Result.success()
        }
        if (!Settings.canDrawOverlays(applicationContext)) {
            return Result.success()
        }

        val report = withContext(Dispatchers.IO) {
            UsageReportBuilder.buildTodayReport(applicationContext)
        }
        val thresholdHours = prefs.warningThresholdHours
        val thresholdMillis = thresholdHours * 3_600_000L

        if (thresholdMillis > 0 && report.totalDurationMillis >= thresholdMillis) {
            val message = "오늘 하루 사용 시간이 ${thresholdHours}시간을 넘었습니다.\n잠시 쉬어가는 건 어떨까요?"
            withContext(Dispatchers.Main) {
                UsageWarningOverlay.show(applicationContext, message)
            }
        }

        return Result.success()
    }
}
