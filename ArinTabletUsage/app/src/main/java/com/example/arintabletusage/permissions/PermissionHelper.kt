package com.example.arintabletusage.permissions

import android.app.AppOpsManager
import android.content.Context
import android.os.Process

/** 앱 사용 기록 접근 권한(PACKAGE_USAGE_STATS) 부여 여부를 확인한다. */
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
}
