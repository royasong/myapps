package com.example.arintabletusage.permissions

import android.app.AppOpsManager
import android.content.Context
import android.os.Process
import android.provider.Settings

/** 앱 사용 기록 접근 권한(PACKAGE_USAGE_STATS), 오버레이 권한 등의 부여 여부를 확인한다. */
object PermissionHelper {
    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** 다른 앱 위에 표시(SYSTEM_ALERT_WINDOW) 권한 - 경고 오버레이를 띄우는 데 필요하다. */
    fun hasOverlayPermission(context: Context): Boolean = Settings.canDrawOverlays(context)
}
