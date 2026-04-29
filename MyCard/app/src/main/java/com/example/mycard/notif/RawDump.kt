package com.example.mycard.notif

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.google.gson.GsonBuilder
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonStreamParser
import java.io.InputStreamReader

object RawDump {
    private const val TAG = "RawDump"
    private const val FILE_NAME = "raw_notifications.jsonl"
    private const val MIME = "application/x-ndjson"
    private const val SUBDIR = "MyCard"

    private val PRETTY_GSON = GsonBuilder().setPrettyPrinting().create()

    private val lock = Any()
    @Volatile private var cachedFileUri: Uri? = null
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

    fun appendObject(context: Context, obj: JsonObject) {
        synchronized(lock) {
            ensureLoadedLocked(context)
            cache.add(obj)
            writeAllLocked(context)
        }
    }

    fun readAllObjects(context: Context): List<JsonObject> {
        synchronized(lock) {
            ensureLoadedLocked(context)
            return cache.toList()
        }
    }

    fun removeLinesByPkg(context: Context, targetPkg: String) {
        synchronized(lock) {
            ensureLoadedLocked(context)
            val before = cache.size
            cache.removeAll { it.get("pkg")?.asString == targetPkg }
            if (cache.size != before) writeAllLocked(context)
        }
    }

    fun removeLineById(context: Context, targetId: Long) {
        synchronized(lock) {
            ensureLoadedLocked(context)
            val before = cache.size
            cache.removeAll { (it.get("id")?.asLong ?: -1L) == targetId }
            if (cache.size != before) writeAllLocked(context)
        }
    }

    fun sharedPathHint(): String =
        "/sdcard/${Environment.DIRECTORY_DOCUMENTS}/$SUBDIR/$FILE_NAME"

    private fun ensureLoadedLocked(context: Context) {
        if (loaded) return
        val uri = ensureFileUri(context) ?: run {
            loaded = true
            return
        }
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                InputStreamReader(stream, Charsets.UTF_8).use { r ->
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
            }
        } catch (e: Exception) {
            Log.w(TAG, "load failed", e)
        }
        loaded = true
    }

    private fun writeAllLocked(context: Context) {
        val uri = ensureFileUri(context) ?: return
        try {
            context.contentResolver.openOutputStream(uri, "wt")?.use { os ->
                cache.forEach {
                    os.write(PRETTY_GSON.toJson(it).toByteArray(Charsets.UTF_8))
                    os.write("\n".toByteArray(Charsets.UTF_8))
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "writeAll failed, invalidating uri", e)
            cachedFileUri = null
        }
    }

    private fun ensureFileUri(context: Context): Uri? {
        cachedFileUri?.let { return it }
        val resolver = context.contentResolver
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val relativePath = "${Environment.DIRECTORY_DOCUMENTS}/$SUBDIR/"

        val projection = arrayOf(MediaStore.Files.FileColumns._ID)
        val selection =
            "${MediaStore.Files.FileColumns.DISPLAY_NAME} = ? AND " +
            "${MediaStore.Files.FileColumns.RELATIVE_PATH} = ?"
        val args = arrayOf(FILE_NAME, relativePath)

        resolver.query(collection, projection, selection, args, null)?.use { c ->
            if (c.moveToFirst()) {
                val id = c.getLong(0)
                val uri = ContentUris.withAppendedId(collection, id)
                cachedFileUri = uri
                return uri
            }
        }

        val values = ContentValues().apply {
            put(MediaStore.Files.FileColumns.DISPLAY_NAME, FILE_NAME)
            put(MediaStore.Files.FileColumns.MIME_TYPE, MIME)
            put(MediaStore.Files.FileColumns.RELATIVE_PATH, relativePath)
        }
        val newUri = resolver.insert(collection, values)
        cachedFileUri = newUri
        return newUri
    }
}
