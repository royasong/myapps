package com.example.mycard.parser

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

object CardFilterStore {
    private const val TAG = "CardFilterStore"
    private const val FILE_NAME = "card_filters.json"
    private const val MIME = "application/json"
    private const val SUBDIR = "MyCard"

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    @Volatile private var cached: CardFiltersFile? = null
    @Volatile private var byPkgIndex: Map<String, List<CardFilter>> = emptyMap()
    @Volatile private var cachedFileUri: Uri? = null
    private val lock = Any()

    fun load(context: Context): CardFiltersFile {
        cached?.let {
            Log.d(TAG, "load: cache hit (filters=${it.filters.size}, pkgs=${byPkgIndex.keys})")
            return it
        }
        synchronized(lock) {
            cached?.let { return it }
            Log.d(TAG, "load: cache miss, reading from MediaStore")
            val parsed = readShared(context)
            if (parsed != null) {
                cached = parsed
                byPkgIndex = parsed.filters.groupBy { it.packageName }
                Log.i(TAG, "load: loaded filters=${parsed.filters.size} pkgs=${byPkgIndex.keys}")
                return parsed
            }
            Log.w(TAG, "load: no readable file, bootstrapping empty CardFiltersFile so adb push can target a MyCard-owned row")
            val empty = CardFiltersFile()
            saveAll(context, empty)
            return cached ?: empty
        }
    }

    fun byPackage(context: Context, pkg: String): List<CardFilter> {
        load(context)
        val result = byPkgIndex[pkg].orEmpty()
        Log.d(TAG, "byPackage(pkg=$pkg): candidates=${result.size}")
        return result
    }

    fun invalidate() {
        synchronized(lock) {
            Log.d(TAG, "invalidate: clearing cache (had ${cached?.filters?.size ?: 0} filters)")
            cached = null
            byPkgIndex = emptyMap()
            cachedFileUri = null
        }
    }

    fun saveAll(context: Context, file: CardFiltersFile) {
        synchronized(lock) {
            val uri = ensureFileUri(context, createIfMissing = true) ?: run {
                Log.w(TAG, "saveAll: no uri available")
                return
            }
            val text = gson.toJson(file)
            try {
                context.contentResolver.openOutputStream(uri, "wt")?.use {
                    it.write(text.toByteArray(Charsets.UTF_8))
                }
                cached = file
                byPkgIndex = file.filters.groupBy { it.packageName }
                Log.i(TAG, "saveAll: wrote filters=${file.filters.size} bytes=${text.length}")
            } catch (e: Exception) {
                Log.w(TAG, "saveAll: write failed", e)
            }
        }
    }

    private fun readShared(context: Context): CardFiltersFile? {
        val uri = ensureFileUri(context, createIfMissing = false)
        if (uri != null) {
            Log.d(TAG, "readShared: opening uri=$uri")
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { r ->
                        val parsed = gson.fromJson(r, CardFiltersFile::class.java)
                        Log.i(TAG, "readShared: parsed filters=${parsed?.filters?.size ?: 0} (via MediaStore)")
                        if (parsed != null) return parsed
                    }
                } ?: Log.w(TAG, "readShared: openInputStream returned null")
            } catch (e: Exception) {
                Log.w(TAG, "readShared: MediaStore read failed for uri=$uri", e)
            }
        } else {
            Log.w(TAG, "readShared: no MediaStore row for $FILE_NAME, trying direct file fallback")
        }

        val direct = File(
            Environment.getExternalStorageDirectory(),
            "${Environment.DIRECTORY_DOCUMENTS}/$SUBDIR/$FILE_NAME"
        )
        Log.d(TAG, "readShared: fallback path=${direct.absolutePath} exists=${direct.exists()} canRead=${if (direct.exists()) direct.canRead() else "n/a"}")
        if (!direct.exists() || !direct.canRead()) return null
        return try {
            direct.bufferedReader(Charsets.UTF_8).use { r ->
                val parsed = gson.fromJson(r, CardFiltersFile::class.java)
                Log.i(TAG, "readShared: parsed filters=${parsed?.filters?.size ?: 0} (via direct file)")
                parsed
            }
        } catch (e: Exception) {
            Log.w(TAG, "readShared: direct file read failed", e)
            null
        }
    }

    private fun ensureFileUri(context: Context, createIfMissing: Boolean): Uri? {
        cachedFileUri?.let {
            Log.d(TAG, "ensureFileUri: cached uri=$it")
            return it
        }
        val resolver = context.contentResolver
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val relativePath = "${Environment.DIRECTORY_DOCUMENTS}/$SUBDIR/"
        Log.d(TAG, "ensureFileUri: query collection=$collection name=$FILE_NAME path=$relativePath createIfMissing=$createIfMissing")

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
                    Log.i(TAG, "ensureFileUri: existing row id=$id uri=$uri")
                    return uri
                }
                Log.w(TAG, "ensureFileUri: stale row id=$id (cannot open), will recreate")
                staleId = id
            } else {
                Log.d(TAG, "ensureFileUri: no row matched")
            }
        } ?: Log.w(TAG, "ensureFileUri: query returned null cursor")

        if (!createIfMissing) {
            Log.d(TAG, "ensureFileUri: not creating")
            return null
        }

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
            Log.w(TAG, "canOpen: failed for uri=$uri", e)
            false
        }
    }
}
