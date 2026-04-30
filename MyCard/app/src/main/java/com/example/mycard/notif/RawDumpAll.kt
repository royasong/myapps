package com.example.mycard.notif

import android.content.Context
import android.util.Log
import com.example.mycard.storage.AppStorage
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonStreamParser
import java.io.BufferedWriter
import java.io.File
import java.io.FileReader

object RawDumpAll {
    private const val TAG = "RawDumpAll"
    private const val FILE_NAME = "raw_notifications_all.jsonl"

    private val PRETTY_GSON = GsonBuilder().setPrettyPrinting().create()

    private val lock = Any()

    fun appendObject(@Suppress("UNUSED_PARAMETER") context: Context, obj: JsonObject) {
        synchronized(lock) {
            val f = file()
            val current = readFromFile(f)
            Log.d(TAG, "appendObject: read ${current.size} existing, appending 1 -> writing ${current.size + 1}")
            writeToFile(f, current + obj)
        }
    }

    private fun file(): File = AppStorage.file(FILE_NAME)

    private fun readFromFile(f: File): List<JsonObject> {
        if (!f.exists()) return emptyList()
        return try {
            FileReader(f).use { r ->
                val parser = JsonStreamParser(r)
                buildList {
                    while (parser.hasNext()) {
                        try {
                            val el = parser.next()
                            if (el.isJsonObject) add(el.asJsonObject)
                        } catch (e: Exception) {
                            Log.w(TAG, "stream parse error, skipping element", e)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "readFromFile: read failed (treating as empty) path=${f.absolutePath}", e)
            emptyList()
        }
    }

    private fun writeToFile(f: File, items: List<JsonObject>) {
        try {
            f.parentFile?.mkdirs()
            BufferedWriter(f.writer(Charsets.UTF_8)).use { w ->
                items.forEach {
                    w.write(PRETTY_GSON.toJson(it))
                    w.write("\n")
                }
            }
            Log.d(TAG, "writeToFile: wrote ${items.size} objects")
        } catch (e: Exception) {
            Log.w(TAG, "writeToFile: failed", e)
        }
    }
}
