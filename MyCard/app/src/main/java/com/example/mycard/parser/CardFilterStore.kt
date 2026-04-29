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
        cached?.let { return it }
        synchronized(lock) {
            cached?.let { return it }
            val parsed = readShared(context) ?: CardFiltersFile()
            cached = parsed
            byPkgIndex = parsed.filters.groupBy { it.packageName }
            return parsed
        }
    }

    fun byPackage(context: Context, pkg: String): List<CardFilter> {
        load(context)
        return byPkgIndex[pkg].orEmpty()
    }

    fun invalidate() {
        synchronized(lock) {
            cached = null
            byPkgIndex = emptyMap()
            cachedFileUri = null
        }
    }

    fun saveAll(context: Context, file: CardFiltersFile) {
        synchronized(lock) {
            val uri = ensureFileUri(context, createIfMissing = true) ?: return
            val text = gson.toJson(file)
            try {
                context.contentResolver.openOutputStream(uri, "wt")?.use {
                    it.write(text.toByteArray(Charsets.UTF_8))
                }
                cached = file
                byPkgIndex = file.filters.groupBy { it.packageName }
            } catch (e: Exception) {
                Log.w(TAG, "save failed", e)
            }
        }
    }

    private fun readShared(context: Context): CardFiltersFile? {
        val uri = ensureFileUri(context, createIfMissing = false) ?: return null
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { r ->
                    gson.fromJson(r, CardFiltersFile::class.java)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "read failed", e)
            null
        }
    }

    private fun ensureFileUri(context: Context, createIfMissing: Boolean): Uri? {
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

        if (!createIfMissing) return null

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
