package com.example.mycard.notif

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader

object Blacklist {
    private const val TAG = "Blacklist"
    private const val FILE_NAME = "blacklist.txt"
    private const val MIME = "text/plain"
    private const val SUBDIR = "MyCard"

    private val cache = mutableSetOf<String>()
    @Volatile private var loaded = false
    private val lock = Any()
    @Volatile private var cachedFileUri: Uri? = null

    fun contains(context: Context, pkg: String): Boolean {
        ensureLoaded(context)
        return synchronized(lock) { pkg in cache }
    }

    fun all(context: Context): Set<String> {
        ensureLoaded(context)
        return synchronized(lock) { cache.toSet() }
    }

    fun add(context: Context, pkg: String) {
        ensureLoaded(context)
        synchronized(lock) {
            if (cache.add(pkg)) saveLocked(context)
        }
    }

    fun remove(context: Context, pkg: String) {
        ensureLoaded(context)
        synchronized(lock) {
            if (cache.remove(pkg)) saveLocked(context)
        }
    }

    private fun ensureLoaded(context: Context) {
        if (loaded) return
        synchronized(lock) {
            if (loaded) return
            val fromFile = readShared(context)
            if (fromFile != null) cache.addAll(fromFile)
            loaded = true
        }
    }

    private fun readShared(context: Context): Set<String>? {
        val uri = ensureFileUri(context, createIfMissing = false) ?: return null
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { r ->
                    r.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toSet()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "read failed", e)
            null
        }
    }

    private fun saveLocked(context: Context) {
        val uri = ensureFileUri(context, createIfMissing = true) ?: return
        val text = cache.sorted().joinToString("\n") + "\n"
        try {
            context.contentResolver.openOutputStream(uri, "wt")?.use {
                it.write(text.toByteArray(Charsets.UTF_8))
            }
        } catch (e: Exception) {
            Log.w(TAG, "save failed", e)
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
