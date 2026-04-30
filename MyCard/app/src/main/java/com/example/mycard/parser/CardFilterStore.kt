package com.example.mycard.parser

import android.content.Context
import android.util.Log
import com.example.mycard.storage.AppStorage
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.File
import java.io.FileReader

object CardFilterStore {
    private const val TAG = "CardFilterStore"
    private const val FILE_NAME = "card_filters.json"

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    @Volatile private var cached: CardFiltersFile? = null
    @Volatile private var byPkgIndex: Map<String, List<CardFilter>> = emptyMap()
    private val lock = Any()

    fun load(context: Context): CardFiltersFile {
        cached?.let {
            Log.d(TAG, "load: cache hit (filters=${it.filters.size}, pkgs=${byPkgIndex.keys})")
            return it
        }
        synchronized(lock) {
            cached?.let { return it }
            Log.d(TAG, "load: cache miss, reading from disk")
            val parsed = readShared()
            if (parsed != null) {
                cached = parsed
                byPkgIndex = parsed.filters.groupBy { it.packageName }
                Log.i(TAG, "load: loaded filters=${parsed.filters.size} pkgs=${byPkgIndex.keys}")
                return parsed
            }
            Log.w(TAG, "load: no readable file, bootstrapping empty CardFiltersFile so user can target it via adb push")
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
        }
    }

    fun saveAll(@Suppress("UNUSED_PARAMETER") context: Context, file: CardFiltersFile) {
        synchronized(lock) {
            val f = filePath()
            val text = gson.toJson(file)
            try {
                f.parentFile?.mkdirs()
                f.writeText(text, Charsets.UTF_8)
                cached = file
                byPkgIndex = file.filters.groupBy { it.packageName }
                Log.i(TAG, "saveAll: wrote filters=${file.filters.size} bytes=${text.length}")
            } catch (e: Exception) {
                Log.w(TAG, "saveAll: write failed", e)
            }
        }
    }

    private fun filePath(): File = AppStorage.file(FILE_NAME)

    private fun readShared(): CardFiltersFile? {
        val f = filePath()
        Log.d(TAG, "readShared: path=${f.absolutePath} exists=${f.exists()} canRead=${if (f.exists()) f.canRead() else "n/a"}")
        if (!f.exists() || !f.canRead()) return null
        return try {
            FileReader(f).use { r ->
                val parsed = gson.fromJson(r, CardFiltersFile::class.java)
                Log.i(TAG, "readShared: parsed filters=${parsed?.filters?.size ?: 0}")
                parsed
            }
        } catch (e: Exception) {
            Log.w(TAG, "readShared: read failed", e)
            null
        }
    }
}
