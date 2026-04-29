package com.example.mycard.parser

import android.content.Context
import com.example.mycard.notif.db.NotificationEntity
import java.util.regex.Matcher
import java.util.regex.Pattern

data class ParseResult(
    val amount: Long,
    val merchant: String?,
    val type: String,
    val filterId: String
)

object CardParser {

    fun parse(context: Context, entity: NotificationEntity): ParseResult? {
        val candidates = CardFilterStore.byPackage(context, entity.pkg)
        if (candidates.isEmpty()) return null

        val title = entity.title
        val body = if (entity.bigText.isNotEmpty()) entity.bigText else entity.text

        for (filter in candidates) {
            val result = tryMatch(filter, title, body) ?: continue
            return result
        }
        return null
    }

    private fun tryMatch(filter: CardFilter, title: String, body: String): ParseResult? {
        val titleRegex = filter.match.titleRegex
        val bodyRegex = filter.match.bodyRegex

        if (titleRegex.isNullOrEmpty() && bodyRegex.isNullOrEmpty()) return null

        var amountText: String? = null
        var merchant: String? = null

        if (!titleRegex.isNullOrEmpty()) {
            val m = compile(titleRegex)?.matcher(title) ?: return null
            if (!m.find()) return null
            amountText = groupOrNull(m, "amount") ?: amountText
            merchant = groupOrNull(m, "merchant") ?: merchant
        }

        if (!bodyRegex.isNullOrEmpty()) {
            val m = compile(bodyRegex)?.matcher(body) ?: return null
            if (!m.find()) return null
            amountText = groupOrNull(m, "amount") ?: amountText
            merchant = groupOrNull(m, "merchant") ?: merchant
        }

        val amountStr = amountText ?: return null
        val amount = amountStr.replace(",", "").toLongOrNull() ?: return null
        val signedAmount = if (filter.match.type == Match.TYPE_CANCEL) -amount else amount

        return ParseResult(
            amount = signedAmount,
            merchant = merchant?.takeIf { it.isNotBlank() },
            type = filter.match.type,
            filterId = filter.id
        )
    }

    private fun compile(regex: String): Pattern? = try {
        Pattern.compile(regex)
    } catch (e: Exception) {
        null
    }

    private fun groupOrNull(matcher: Matcher, name: String): String? = try {
        matcher.group(name)
    } catch (e: Exception) {
        null
    }
}
