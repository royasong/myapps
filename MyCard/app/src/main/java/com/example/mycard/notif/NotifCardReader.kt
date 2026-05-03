package com.example.mycard.notif

import android.content.Context
import com.example.mycard.notif.db.NotificationDatabase
import com.example.mycard.parser.CardFilterStore
import com.example.mycard.sms.SMSReader
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA)

suspend fun readNotifCardGroups(context: Context): List<SMSReader.SmsGroup> {
    val startOfMonth = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    val entities = NotificationDatabase.get(context)
        .notificationDao()
        .getParsedSince(startOfMonth)

    val filters = CardFilterStore.load(context).filters
    val filterIdToCompany = filters.associate { it.id to it.cardCompany }
    val pkgToCompany = filters.associate { it.packageName to it.cardCompany }

    return entities
        .groupBy {
            it.filterId?.let { fid -> filterIdToCompany[fid] }
                ?: pkgToCompany[it.pkg]
                ?: it.pkg
        }
        .map { (company, items) ->
            val smsItems = items.map { e ->
                SMSReader.SmsItem(
                    e.pkg,
                    DATE_FORMAT.format(Date(e.ts)),
                    e.merchant ?: e.text.ifEmpty { e.title },
                    e.amount ?: 0L
                )
            }
            SMSReader.SmsGroup(company, items.sumOf { it.amount ?: 0L }, smsItems)
        }
        .sortedByDescending { it.totalAmount }
}
