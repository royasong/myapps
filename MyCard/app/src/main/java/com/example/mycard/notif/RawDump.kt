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
            Log.d(TAG, "appendObject: cache size=${cache.size}")
            writeAllLocked(context)
        }
    }

    fun readAllObjects(context: Context): List<JsonObject> {
        synchronized(lock) {
            ensureLoadedLocked(context)
            Log.d(TAG, "readAllObjects: returning ${cache.size} objects")
            return cache.toList()
        }
    }

    fun removeLinesByPkg(context: Context, targetPkg: String) {
        synchronized(lock) {
            ensureLoadedLocked(context)
            val before = cache.size
            cache.removeAll { it.get("pkg")?.asString == targetPkg }
            Log.d(TAG, "removeLinesByPkg($targetPkg): $before -> ${cache.size}")
            if (cache.size != before) writeAllLocked(context)
        }
    }

    fun removeLineById(context: Context, targetId: Long) {
        synchronized(lock) {
            ensureLoadedLocked(context)
            val before = cache.size
            cache.removeAll { (it.get("id")?.asLong ?: -1L) == targetId }
            Log.d(TAG, "removeLineById($targetId): $before -> ${cache.size}")
            if (cache.size != before) writeAllLocked(context)
        }
    }

    fun sharedPathHint(): String =
        "/sdcard/${Environment.DIRECTORY_DOCUMENTS}/$SUBDIR/$FILE_NAME"

    private fun ensureLoadedLocked(context: Context) {
        if (loaded) return
        val uri = ensureFileUri(context) ?: run {
            Log.w(TAG, "ensureLoadedLocked: no uri, treating cache as empty")
            loaded = true
            return
        }
        Log.d(TAG, "ensureLoadedLocked: loading from uri=$uri")
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
            Log.i(TAG, "ensureLoadedLocked: loaded ${cache.size} objects")
        } catch (e: Exception) {
            Log.w(TAG, "ensureLoadedLocked: load failed", e)
        }
        loaded = true
    }

    private fun writeAllLocked(context: Context) {
        val uri = ensureFileUri(context) ?: run {
            Log.w(TAG, "writeAllLocked: no uri")
            return
        }
        try {
            context.contentResolver.openOutputStream(uri, "wt")?.use { os ->
                cache.forEach {
                    os.write(PRETTY_GSON.toJson(it).toByteArray(Charsets.UTF_8))
                    os.write("\n".toByteArray(Charsets.UTF_8))
                }
            }
            Log.d(TAG, "writeAllLocked: wrote ${cache.size} objects")
        } catch (e: Exception) {
            Log.w(TAG, "writeAllLocked: failed, invalidating uri", e)
            cachedFileUri = null
        }
    }

    private fun ensureFileUri(context: Context): Uri? {
        cachedFileUri?.let {
            Log.d(TAG, "ensureFileUri: cached uri=$it")
            return it
        }
        val resolver = context.contentResolver
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val relativePath = "${Environment.DIRECTORY_DOCUMENTS}/$SUBDIR/"
        Log.d(TAG, "ensureFileUri: query name=$FILE_NAME path=$relativePath")

        val projection = arrayOf(MediaStore.Files.FileColumns._ID)
        val selection =
            "${MediaStore.Files.FileColumns.DISPLAY_NAME} = ? AND " +
            "${MediaStore.Files.FileColumns.RELATIVE_PATH} = ?"
        val args = arrayOf(FILE_NAME, relativePath)

        var staleId: Long? = null
        resolver.query(collection, projection, selection, args, null)?.use { c ->
            if (c.moveToFirst()) {
                val id = c.getLong(0)
                val uri = ContentUris.withAppendedId(collection, id)
                if (canOpen(resolver, uri)) {
                    Log.i(TAG, "ensureFileUri: existing row id=$id uri=$uri")
                    cachedFileUri = uri
                    return uri
                }
                Log.w(TAG, "ensureFileUri: stale row id=$id (cannot open), will recreate")
                staleId = id
            } else {
                Log.d(TAG, "ensureFileUri: no row found, creating")
            }
        } ?: Log.w(TAG, "ensureFileUri: query returned null cursor")

        staleId?.let { id ->
            try {
                resolver.delete(ContentUris.withAppendedId(collection, id), null, null)
            } catch (e: Exception) {
                Log.w(TAG, "ensureFileUri: stale row delete failed", e)
            }
        }

        val values = ContentValues().apply {
            put(MediaStore.Files.FileColumns.DISPLAY_NAME, FILE_NAME)
            put(MediaStore.Files.FileColumns.MIME_TYPE, MIME)
            put(MediaStore.Files.FileColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.Files.FileColumns.IS_PENDING, 0)
        }
        val newUri = resolver.insert(collection, values)
        Log.i(TAG, "ensureFileUri: inserted new row uri=$newUri")
        cachedFileUri = newUri
        return newUri
    }

    private fun canOpen(resolver: android.content.ContentResolver, uri: Uri): Boolean {
        return try {
            resolver.openInputStream(uri)?.close()
            true
        } catch (e: Exception) {
            false
        }
    }
}
