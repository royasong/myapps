package com.example.mycard.notif

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import java.io.BufferedReader
import java.io.InputStreamReader

object RawDump {
    private const val TAG = "RawDump"
    private const val FILE_NAME = "raw_notifications.jsonl"
    private const val MIME = "application/x-ndjson"
    private const val SUBDIR = "MyCard"

    private val lock = Any()
    @Volatile private var cachedFileUri: Uri? = null

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

    fun appendShared(context: Context, line: String) {
        synchronized(lock) {
            val uri = ensureFileUri(context)
            if (uri == null) {
                Log.w(TAG, "could not resolve share-folder file uri")
                return
            }
            try {
                writeAppend(context, uri, line)
            } catch (e: Exception) {
                Log.w(TAG, "append failed, invalidating cache and retrying", e)
                cachedFileUri = null
                val retry = ensureFileUri(context) ?: return
                try {
                    writeAppend(context, retry, line)
                } catch (e2: Exception) {
                    Log.e(TAG, "append permanently failed", e2)
                }
            }
        }
    }

    fun sharedPathHint(): String =
        "/sdcard/${Environment.DIRECTORY_DOCUMENTS}/$SUBDIR/$FILE_NAME"

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

    private fun writeAppend(context: Context, uri: Uri, line: String) {
        context.contentResolver.openOutputStream(uri, "wa")?.use { os ->
            os.write(line.toByteArray(Charsets.UTF_8))
            os.write("\n".toByteArray(Charsets.UTF_8))
        } ?: throw IllegalStateException("openOutputStream returned null for $uri")
    }

    fun removeLinesByPkg(context: Context, targetPkg: String) {
        synchronized(lock) {
            val uri = ensureFileUri(context) ?: return
            val resolver = context.contentResolver
            val lines = try {
                resolver.openInputStream(uri)?.use { stream ->
                    BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { r ->
                        r.readLines()
                    }
                } ?: return
            } catch (e: Exception) {
                Log.w(TAG, "read for pkg-delete failed", e)
                return
            }

            val gson = Gson()
            val kept = lines.filter { line ->
                if (line.isBlank()) return@filter false
                try {
                    val obj = gson.fromJson(line, JsonObject::class.java)
                    val rowPkg = obj.get("pkg")?.asString.orEmpty()
                    rowPkg != targetPkg
                } catch (e: Exception) {
                    true
                }
            }

            try {
                resolver.openOutputStream(uri, "wt")?.use { os ->
                    kept.forEach {
                        os.write(it.toByteArray(Charsets.UTF_8))
                        os.write("\n".toByteArray(Charsets.UTF_8))
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "rewrite for pkg-delete failed", e)
            }
        }
    }

    fun removeLineById(context: Context, targetId: Long) {
        synchronized(lock) {
            val uri = ensureFileUri(context) ?: return
            val resolver = context.contentResolver
            val lines = try {
                resolver.openInputStream(uri)?.use { stream ->
                    BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { r ->
                        r.readLines()
                    }
                } ?: return
            } catch (e: Exception) {
                Log.w(TAG, "read for delete failed", e)
                return
            }

            val gson = Gson()
            val kept = lines.filter { line ->
                if (line.isBlank()) return@filter false
                try {
                    val obj = gson.fromJson(line, JsonObject::class.java)
                    val rowId = obj.get("id")?.asLong ?: -1L
                    rowId != targetId
                } catch (e: Exception) {
                    true
                }
            }

            try {
                resolver.openOutputStream(uri, "wt")?.use { os ->
                    kept.forEach {
                        os.write(it.toByteArray(Charsets.UTF_8))
                        os.write("\n".toByteArray(Charsets.UTF_8))
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "rewrite for delete failed", e)
            }
        }
    }
}
