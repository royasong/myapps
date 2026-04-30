package com.example.mycard.storage

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import java.io.File

object AppStorage {
    private const val SUBDIR = "Documents/MyCard"

    fun dir(): File = File(Environment.getExternalStorageDirectory(), SUBDIR).also {
        if (!it.exists()) it.mkdirs()
    }

    fun file(name: String): File = File(dir(), name)

    fun hasAllFilesAccess(): Boolean = Environment.isExternalStorageManager()

    fun openAllFilesAccessSettings(activity: Activity) {
        val intent = Intent(
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.parse("package:${activity.packageName}")
        )
        activity.startActivity(intent)
    }
}
