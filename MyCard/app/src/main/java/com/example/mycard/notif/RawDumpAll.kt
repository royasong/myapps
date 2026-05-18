package com.example.mycard.notif

import android.content.Context
import android.util.Log
import com.example.mycard.storage.AppStorage
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.util.Calendar

object RawDumpAll {
    private const val TAG = "RawDumpAll"

    private val PRETTY_GSON = GsonBuilder().setPrettyPrinting().create()

    private val lock = Any()

    fun appendObject(@Suppress("UNUSED_PARAMETER") context: Context, obj: JsonObject) {
        synchronized(lock) {
            val f = file()
            try {
                f.parentFile?.mkdirs()
                BufferedWriter(OutputStreamWriter(FileOutputStream(f, true), Charsets.UTF_8)).use { w ->
                    w.write(PRETTY_GSON.toJson(obj))
                    w.write("\n")
                }
                Log.d(TAG, "appendObject: appended 1 object")
            } catch (e: Exception) {
                Log.w(TAG, "appendObject: failed", e)
            }
        }
    }

    private fun currentMonthFilename(): String {
        val cal = Calendar.getInstance()
        val year = cal.get(Calendar.YEAR)
        val month = (cal.get(Calendar.MONTH) + 1).toString().padStart(2, '0')
        return "raw_notifications_all_${year}_${month}.jsonl"
    }

    private fun file(): File = AppStorage.file(currentMonthFilename())
}
