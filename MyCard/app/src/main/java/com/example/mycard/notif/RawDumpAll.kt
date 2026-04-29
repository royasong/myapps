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
            val uri = ensureFileUri(context) ?: return
            val current = readFromUri(resolver, uri)
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
            } ?: emptyList()
        } catch (e: Exception) {
            Log.w(TAG, "read failed (treating as empty)", e)
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
        } catch (e: Exception) {
            Log.w(TAG, "write failed, invalidating uri", e)
            cachedFileUri = null
        }
    }

    private fun ensureFileUri(context: Context): Uri? {
        cachedFileUri?.let {
            if (canOpen(context.contentResolver, it)) return it
            Log.w(TAG, "cached URI stale, refreshing")
            cachedFileUri = null
        }

        val resolver = context.contentResolver
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val relativePath = "${Environment.DIRECTORY_DOCUMENTS}/$SUBDIR/"

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
                    cachedFileUri = uri
                    return uri
                }
                Log.w(TAG, "stale MediaStore row id=$id, will recreate")
                staleId = id
            }
        }

        staleId?.let { id ->
            try {
                resolver.delete(ContentUris.withAppendedId(collection, id), null, null)
            } catch (e: Exception) {
                Log.w(TAG, "stale row delete failed (continuing)", e)
            }
        }

        val values = ContentValues().apply {
            put(MediaStore.Files.FileColumns.DISPLAY_NAME, FILE_NAME)
            put(MediaStore.Files.FileColumns.MIME_TYPE, MIME)
            put(MediaStore.Files.FileColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.Files.FileColumns.IS_PENDING, 0)
        }
        val newUri = resolver.insert(collection, values)
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
