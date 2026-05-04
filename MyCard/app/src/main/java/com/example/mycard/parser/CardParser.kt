package com.example.mycard.parser

import android.content.Context
import android.util.Log
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

    private const val TAG = "CardParser"

    fun parse(context: Context, entity: NotificationEntity): ParseResult? {
        val candidates = CardFilterStore.byPackage(context, entity.pkg)
        if (candidates.isEmpty()) {
            Log.d(TAG, "parse: no candidates pkg=${entity.pkg}")
            return null
        }

        val title = entity.title
        val body = if (entity.bigText.isNotEmpty()) entity.bigText else entity.text
        Log.d(
            TAG,
            "parse: pkg=${entity.pkg} candidates=${candidates.size} title.len=${title.length} body.len=${body.length} bodyHead=${body.take(60).replace("\n", "\\n").replace("\r", "\\r")}"
        )

        for (filter in candidates) {
            val result = tryMatch(filter, title, body)
            if (result != null) {
                Log.i(
                    TAG,
                    "parse: matched filter=${filter.id} amount=${result.amount} merchant=${result.merchant} type=${result.type}"
                )
                return result
            }
        }
        Log.d(TAG, "parse: no filter matched (pkg=${entity.pkg}, tried=${candidates.size})")
        return null
    }

    private fun tryMatch(filter: CardFilter, title: String, body: String): ParseResult? {
        val titleRegex = filter.match.titleRegex
        val bodyRegex = filter.match.bodyRegex

        if (titleRegex.isNullOrEmpty() && bodyRegex.isNullOrEmpty()) {
            Log.d(TAG, "tryMatch[${filter.id}]: both regex null/empty")
            return null
        }

        var amountText: String? = null
        var merchant: String? = null

        if (!titleRegex.isNullOrEmpty()) {
            val pattern = compile(titleRegex) ?: run {
                Log.w(TAG, "tryMatch[${filter.id}]: title regex compile failed: $titleRegex")
                return null
            }
            val m = pattern.matcher(title)
            if (!m.find()) {
                Log.d(TAG, "tryMatch[${filter.id}]: title regex did not match (title=${title.take(80)})")
                return null
            }
            amountText = groupOrNull(m, "amount") ?: amountText
            merchant = groupOrNull(m, "merchant") ?: merchant
            Log.d(TAG, "tryMatch[${filter.id}]: title matched amount=$amountText merchant=$merchant")
        }

        if (!bodyRegex.isNullOrEmpty()) {
            val pattern = compile(bodyRegex) ?: run {
                Log.w(TAG, "tryMatch[${filter.id}]: body regex compile failed: $bodyRegex")
                return null
            }
            val m = pattern.matcher(body)
            if (!m.find()) {
                Log.d(
                    TAG,
                    "tryMatch[${filter.id}]: body regex did not match (regex=$bodyRegex body=${body.take(120).replace("\n", "\\n").replace("\r", "\\r")})"
                )
                return null
            }
            amountText = groupOrNull(m, "amount") ?: amountText
            merchant = groupOrNull(m, "merchant") ?: merchant
            Log.d(TAG, "tryMatch[${filter.id}]: body matched amount=$amountText merchant=$merchant")
        }

        val amountStr = amountText ?: run {
            Log.d(TAG, "tryMatch[${filter.id}]: no amount captured")
            return null
        }
        val amount = amountStr.replace(",", "").toLongOrNull() ?: run {
            Log.w(TAG, "tryMatch[${filter.id}]: amount not numeric: $amountStr")
            return null
        }
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
        Log.w(TAG, "compile failed regex=$regex", e)
        null
    }

    private fun groupOrNull(matcher: Matcher, name: String): String? = try {
        matcher.group(name)
    } catch (e: Exception) {
        null
    }
}
