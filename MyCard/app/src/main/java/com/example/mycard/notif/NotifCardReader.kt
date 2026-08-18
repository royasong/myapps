package com.example.mycard.notif

import android.content.Context
import com.example.mycard.notif.db.NotificationDatabase
import com.example.mycard.parser.CardFilterStore
import com.example.mycard.sms.SMSReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA)

/**
 * [monthOffset] 0 = 이번 달, -1 = 지난 달. 상한(다음 달 1일)까지 두므로 미래 ts가 섞이지 않는다.
 */
suspend fun readNotifCardGroups(
    context: Context,
    monthOffset: Int = 0
): List<SMSReader.SmsGroup> {
    val (sinceTs, untilTs) = monthRange(monthOffset)

    val entities = NotificationDatabase.get(context)
        .notificationDao()
        .getParsedInRange(sinceTs, untilTs)

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
