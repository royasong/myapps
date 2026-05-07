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

object RawDumpAll {
    private const val TAG = "RawDumpAll"
    private const val FILE_NAME = "raw_notifications_all.jsonl"

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

    private fun file(): File = AppStorage.file(FILE_NAME)
}
