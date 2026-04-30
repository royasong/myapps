package com.example.mycard.notif

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonStreamParser
import java.io.InputStreamReader

object RawDumpAll {
    private const val TAG = "RawDumpAll"
    private const val FILE_NAME = "raw_notifications_all.jsonl"
    private const val MIME = "application/x-ndjson"
    private const val SUBDIR = "MyCard"

    private val PRETTY_GSON = GsonBuilder().setPrettyPrinting().create()

    private val lock = Any()
    @Volatile private var cachedFileUri: Uri? = null

    fun appendObject(context: Context, obj: JsonObject) {
        synchronized(lock) {
            val resolver = context.contentResolver
            val uri = ensureFileUri(context) ?: run {
                Log.w(TAG, "appendObject: no uri")
                return
            }
            val current = readFromUri(resolver, uri)
            Log.d(TAG, "appendObject: read ${current.size} existing, appending 1 -> writing ${current.size + 1}")
            writeToUri(resolver, uri, current + obj)
        }
    }

    private fun readFromUri(resolver: ContentResolver, uri: Uri): List<JsonObject> {
        return try {
            resolver.openInputStream(uri)?.use { stream ->
                InputStreamReader(stream, Charsets.UTF_8).use { r ->
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
            } ?: run {
                Log.w(TAG, "readFromUri: openInputStream returned null")
                emptyList()
            }
        } catch (e: Exception) {
            Log.w(TAG, "readFromUri: read failed (treating as empty) uri=$uri", e)
            emptyList()
        }
    }

    private fun writeToUri(resolver: ContentResolver, uri: Uri, items: List<JsonObject>) {
        try {
            resolver.openOutputStream(uri, "wt")?.use { os ->
                items.forEach {
                    os.write(PRETTY_GSON.toJson(it).toByteArray(Charsets.UTF_8))
                    os.write("\n".toByteArray(Charsets.UTF_8))
                }
            }
            Log.d(TAG, "writeToUri: wrote ${items.size} objects")
        } catch (e: Exception) {
            Log.w(TAG, "writeToUri: failed, invalidating uri", e)
            cachedFileUri = null
        }
    }

    private fun ensureFileUri(context: Context): Uri? {
        cachedFileUri?.let {
            if (canOpen(context.contentResolver, it)) {
                Log.d(TAG, "ensureFileUri: cached uri=$it")
                return it
            }
            Log.w(TAG, "ensureFileUri: cached URI stale, refreshing")
            cachedFileUri = null
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

    private fun canOpen(resolver: ContentResolver, uri: Uri): Boolean {
        return try {
            resolver.openInputStream(uri)?.close()
            true
        } catch (e: Exception) {
            false
        }
    }
}
