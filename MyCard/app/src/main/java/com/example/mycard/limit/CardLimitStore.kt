package com.example.mycard.limit

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * 카드(그룹)별 월 한도. 값은 mycard_prefs의 "card_limits"에 JSON 맵으로 저장한다.
 *
 * Room 테이블 대신 prefs를 쓰는 이유: mycard_prefs는 이미 Activity/Worker/Widget이 공유하는
 * 단일 소스라 위젯에서도 바로 읽을 수 있고, 방금 올린 DB version 3 위에 마이그레이션을
 * 하나 더 얹지 않아도 된다.
 */
object CardLimitStore {
    private const val TAG = "CardLimitStore"
    private const val PREFS = "mycard_prefs"
    const val KEY = "card_limits"

    private val gson = Gson()
    private val type = object : TypeToken<Map<String, Long>>() {}.type

    fun load(context: Context): Map<String, Long> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return emptyMap()
        return try {
            gson.fromJson<Map<String, Long>>(raw, type)?.filterValues { it > 0L } ?: emptyMap()
        } catch (e: Exception) {
            Log.w(TAG, "load failed", e)
            emptyMap()
        }
    }

    /** 한도가 0 이하이면 해당 카드의 한도를 해제한다. */
    fun setLimit(context: Context, company: String, monthlyLimit: Long) {
        val next = load(context).toMutableMap()
        if (monthlyLimit <= 0L) next.remove(company) else next[company] = monthlyLimit
        save(context, next)
    }

    fun saveAll(context: Context, limits: Map<String, Long>) {
        save(context, limits.filterValues { it > 0L })
    }

    private fun save(context: Context, limits: Map<String, Long>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, gson.toJson(limits))
            .apply()
        Log.i(TAG, "save: ${limits.size} limits")
    }
}
