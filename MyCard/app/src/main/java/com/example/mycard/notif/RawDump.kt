package com.example.mycard.notif

import android.content.Context
import android.os.Bundle
import android.util.Log
import com.example.mycard.storage.AppStorage
import com.google.gson.GsonBuilder
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonStreamParser
import java.io.BufferedWriter
import java.io.File
import java.io.FileReader

object RawDump {
    private const val TAG = "RawDump"
    private const val FILE_NAME = "raw_notifications.jsonl"

    private val PRETTY_GSON = GsonBuilder().setPrettyPrinting().create()

    private val lock = Any()
    @Volatile private var loaded = false
    private val cache = mutableListOf<JsonObject>()

    fun bundleToJson(bundle: Bundle?): String {
        if (bundle == null) return "{}"
        val obj = JsonObject()
        for (key in bundle.keySet()) {
            try {
                val value = bundle.get(key)
                when (value) {
                    null -> obj.add(key, JsonNull.INSTANCE)
                    is CharSequence -> obj.addProperty(key, value.toString())
                    is Number -> obj.addProperty(key, value)
                    is Boolean -> obj.addProperty(key, value)
                    is Array<*> -> obj.addProperty(
                        key, value.joinToString(", ") { it?.toString() ?: "null" }
                    )
                    else -> obj.addProperty(key, value.toString())
                }
            } catch (e: Exception) {
                obj.addProperty(key, "<error: ${e.javaClass.simpleName}>")
            }
        }
        return obj.toString()
    }

    fun appendObject(@Suppress("UNUSED_PARAMETER") context: Context, obj: JsonObject) {
        synchronized(lock) {
            ensureLoadedLocked()
            cache.add(obj)
            writeAllLocked()
        }
    }

    fun readAllObjects(@Suppress("UNUSED_PARAMETER") context: Context): List<JsonObject> {
        synchronized(lock) {
            ensureLoadedLocked()
            return cache.toList()
        }
    }

    fun removeLinesByPkg(@Suppress("UNUSED_PARAMETER") context: Context, targetPkg: String) {
        synchronized(lock) {
            ensureLoadedLocked()
            val before = cache.size
            cache.removeAll { it.get("pkg")?.asString == targetPkg }
            if (cache.size != before) writeAllLocked()
        }
    }

    fun removeLineById(@Suppress("UNUSED_PARAMETER") context: Context, targetId: Long) {
        synchronized(lock) {
            ensureLoadedLocked()
            val before = cache.size
            cache.removeAll { (it.get("id")?.asLong ?: -1L) == targetId }
            if (cache.size != before) writeAllLocked()
        }
    }

    fun invalidate() {
        synchronized(lock) {
            cache.clear()
            loaded = false
        }
    }

    fun sharedPathHint(): String = file().absolutePath

    private fun file(): File = AppStorage.file(FILE_NAME)

    private fun ensureLoadedLocked() {
        if (loaded) return
        val f = file()
        if (!f.exists()) {
            loaded = true
            return
        }
        try {
            FileReader(f).use { r ->
                val parser = JsonStreamParser(r)
                while (parser.hasNext()) {
                    try {
                        val el = parser.next()
                        if (el.isJsonObject) cache.add(el.asJsonObject)
                    } catch (e: Exception) {
                        Log.w(TAG, "stream parse error, skipping element", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "load failed", e)
        }
        loaded = true
    }

    private fun writeAllLocked() {
        val f = file()
        try {
            f.parentFile?.mkdirs()
            BufferedWriter(f.writer(Charsets.UTF_8)).use { w ->
                cache.forEach {
                    w.write(PRETTY_GSON.toJson(it))
                    w.write("\n")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "writeAll failed", e)
        }
    }
}
