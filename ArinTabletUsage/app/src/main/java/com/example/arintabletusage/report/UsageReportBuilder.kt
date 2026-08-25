package com.example.arintabletusage.report

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** 앱 하나의 오늘 사용 정보 */
data class AppUsageEntry(
    val packageName: String,
    val label: String,
    val durationMillis: Long,
    val launchCount: Int
)

/** 오늘 하루치 태블릿 사용시간 리포트 원본 데이터 */
data class UsageReport(
    val generatedAt: Long,
    val rangeStart: Long,
    val rangeEnd: Long,
    val totalDurationMillis: Long,
    val totalLaunchCount: Int,
    val apps: List<AppUsageEntry>
)

/**
 * UsageStatsManager의 이벤트 로그(ACTIVITY_RESUMED / ACTIVITY_PAUSED)를 직접 순회해서
 * 오늘 자정부터 지금까지의 앱별 포그라운드 사용 시간을 계산하고, 텍스트 리포트로 만든다.
 */
object UsageReportBuilder {

    fun buildTodayReport(context: Context): UsageReport {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val startOfDay = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val events = usm.queryEvents(startOfDay, now)
        val foregroundStart = HashMap<String, Long>()
        val totalDuration = HashMap<String, Long>()
        val launchCount = HashMap<String, Int>()
        val event = UsageEvents.Event()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName ?: continue
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    foregroundStart[pkg] = event.timeStamp
                    launchCount[pkg] = (launchCount[pkg] ?: 0) + 1
                }
                UsageEvents.Event.ACTIVITY_PAUSED -> {
                    val start = foregroundStart.remove(pkg)
                    if (start != null && event.timeStamp > start) {
                        totalDuration[pkg] = (totalDuration[pkg] ?: 0L) + (event.timeStamp - start)
                    }
                }
            }
        }
        // 리포트 생성 시점까지 아직 포그라운드에 남아있는 앱은 지금까지의 시간을 더해준다.
        for ((pkg, start) in foregroundStart) {
            if (now > start) {
                totalDuration[pkg] = (totalDuration[pkg] ?: 0L) + (now - start)
            }
        }

        val pm = context.packageManager
        val selfPackage = context.packageName
        val apps = totalDuration.entries
            .filter { it.key != selfPackage && it.value >= 1000L }
            .map { (pkg, duration) ->
                val label = try {
                    val ai: ApplicationInfo = pm.getApplicationInfo(pkg, 0)
                    pm.getApplicationLabel(ai).toString()
                } catch (e: PackageManager.NameNotFoundException) {
                    pkg
                }
                AppUsageEntry(pkg, label, duration, launchCount[pkg] ?: 0)
            }
            .sortedByDescending { it.durationMillis }

        return UsageReport(
            generatedAt = now,
            rangeStart = startOfDay,
            rangeEnd = now,
            totalDurationMillis = apps.sumOf { it.durationMillis },
            totalLaunchCount = apps.sumOf { it.launchCount },
            apps = apps
        )
    }

    fun formatDuration(millis: Long): String {
        val totalMinutes = millis / 60000
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return if (hours > 0) "${hours}시간 ${minutes}분" else "${minutes}분"
    }

    /** 요약(Simple) 섹션과 앱별 상세(Detail) 섹션을 모두 포함한 텍스트 리포트를 만든다. */
    fun buildReportText(report: UsageReport): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA)
        val sb = StringBuilder()
        sb.appendLine("=== 태블릿 사용시간 리포트 ===")
        sb.appendLine("생성 시각: ${sdf.format(Date(report.generatedAt))}")
        sb.appendLine("집계 범위: ${sdf.format(Date(report.rangeStart))} ~ ${sdf.format(Date(report.rangeEnd))}")
        sb.appendLine()

        sb.appendLine("[요약]")
        sb.appendLine("- 총 사용 시간: ${formatDuration(report.totalDurationMillis)}")
        sb.appendLine("- 실행된 앱 수: ${report.apps.size}개")
        sb.appendLine("- 총 앱 실행(포그라운드 전환) 횟수: ${report.totalLaunchCount}회")
        if (report.apps.isNotEmpty()) {
            val top = report.apps.first()
            val pct = percentOf(top.durationMillis, report.totalDurationMillis)
            sb.appendLine("- 가장 많이 사용한 앱: ${top.label} (${formatDuration(top.durationMillis)}, ${pct}%)")
        } else {
            sb.appendLine("- 오늘 기록된 앱 사용 내역이 없습니다.")
        }
        sb.appendLine()

        sb.appendLine("[앱별 상세 사용 시간]")
        if (report.apps.isEmpty()) {
            sb.appendLine("(내역 없음)")
        } else {
            report.apps.forEachIndexed { index, app ->
                val pct = percentOf(app.durationMillis, report.totalDurationMillis)
                sb.appendLine(
                    "${index + 1}. ${app.label} - ${formatDuration(app.durationMillis)} " +
                        "(${pct}%, 실행 ${app.launchCount}회)"
                )
            }
        }
        sb.appendLine()
        sb.appendLine("- ArinTabletUsage 자동 발송 리포트 -")
        return sb.toString()
    }

    private fun percentOf(part: Long, total: Long): Long =
        if (total > 0) part * 100 / total else 0
}
