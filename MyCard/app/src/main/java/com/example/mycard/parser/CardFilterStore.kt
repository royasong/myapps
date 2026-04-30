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
        cached?.let { return it }
        synchronized(lock) {
            cached?.let { return it }
            val parsed = readShared() ?: CardFiltersFile()
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
            } catch (e: Exception) {
                Log.w(TAG, "save failed", e)
            }
        }
    }

    private fun filePath(): File = AppStorage.file(FILE_NAME)

    private fun readShared(): CardFiltersFile? {
        val f = filePath()
        if (!f.exists()) return null
        return try {
            FileReader(f).use { r ->
                gson.fromJson(r, CardFiltersFile::class.java)
            }
        } catch (e: Exception) {
            Log.w(TAG, "read failed", e)
            null
        }
    }
}
