package com.example.arintabletusage.schedule

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.arintabletusage.worker.UsageCheckWorker
import java.util.concurrent.TimeUnit

/** 매시간 사용 시간을 체크하는 주기 작업(UsageCheckWorker)을 예약한다. */
object UsageCheckScheduler {
    private const val WORK_NAME = "usage_check_hourly"

    /**
     * 이미 예약되어 있으면 그대로 유지한다(KEEP). WorkManager의 주기 작업은
     * 재부팅 후에도 자체적으로 다시 걸리므로 앱 시작 시 한 번만 호출하면 된다.
     */
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<UsageCheckWorker>(1, TimeUnit.HOURS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
