package com.example.mycard.notif

import android.content.Context
import android.util.Log
import com.example.mycard.storage.AppStorage
import java.io.File

object Whitelist {
    private const val TAG = "Whitelist"
    private const val FILE_NAME = "whitelist.txt"

    private val DEFAULT_PACKAGES = setOf(
        "kr.co.samsungcard.mpocket",
        "com.ehyundai.mcard",
        "com.hanaskcard.paycla",
        "com.wooricard.smartapp",
        "kr.co.ivlog.mobile.app.cjonecard",
        "com.hyundaicard.appcard",
        "net.ib.android.smcard",
        "com.sktelecom.smartcard.SmartcardService",
        "com.example.mycard",
        "com.shcard.smartpay"
    )

    private val cache = mutableSetOf<String>()
    @Volatile private var loaded = false
    private val lock = Any()

    fun contains(@Suppress("UNUSED_PARAMETER") context: Context, pkg: String): Boolean {
        ensureLoaded()
        return synchronized(lock) { pkg in cache }
    }

    fun all(@Suppress("UNUSED_PARAMETER") context: Context): Set<String> {
        ensureLoaded()
        return synchronized(lock) { cache.toSet() }
    }

    fun add(@Suppress("UNUSED_PARAMETER") context: Context, pkg: String) {
        ensureLoaded()
        synchronized(lock) {
            if (cache.add(pkg)) saveLocked()
        }
    }

    fun remove(@Suppress("UNUSED_PARAMETER") context: Context, pkg: String) {
        ensureLoaded()
        synchronized(lock) {
            if (cache.remove(pkg)) saveLocked()
        }
    }

    fun invalidate() {
        synchronized(lock) {
            cache.clear()
            loaded = false
        }
    }

    private fun file(): File = AppStorage.file(FILE_NAME)

    private fun ensureLoaded() {
        if (loaded) return
        synchronized(lock) {
            if (loaded) return
            val fromFile = readShared()
            if (fromFile != null) {
                cache.addAll(fromFile)
            } else {
                cache.addAll(DEFAULT_PACKAGES)
                saveLocked()
            }
            loaded = true
        }
    }

    private fun readShared(): Set<String>? {
        val f = file()
        if (!f.exists()) return null
        return try {
            f.readLines(Charsets.UTF_8).map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        } catch (e: Exception) {
            Log.w(TAG, "read failed", e)
            null
        }
    }

    private fun saveLocked() {
        val f = file()
        val text = cache.sorted().joinToString("\n") + "\n"
        try {
            f.parentFile?.mkdirs()
            f.writeText(text, Charsets.UTF_8)
        } catch (e: Exception) {
            Log.w(TAG, "save failed", e)
        }
    }
}
