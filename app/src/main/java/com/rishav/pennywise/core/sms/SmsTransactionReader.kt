package com.rishav.pennywise.core.sms

import android.content.Context
import android.provider.Telephony

data class SmsSyncSummary(
    val scannedMessageCount: Int,
    val transactionMessageCount: Int
)

class SmsTransactionReader {

    fun readSummary(context: Context): SmsSyncSummary {
        val projection = arrayOf(
            Telephony.Sms.BODY
        )

        val regex = Regex(
            pattern = "\\b(debited|credited|spent|purchase|txn|transaction|upi|withdrawn|paid|payment|received|sent|charged)\\b",
            option = RegexOption.IGNORE_CASE
        )

        var scanned = 0
        var matched = 0

        context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            projection,
            null,
            null,
            "${Telephony.Sms.DATE} DESC"
        )?.use { cursor ->
            val bodyIndex = cursor.getColumnIndex(Telephony.Sms.BODY)
            while (cursor.moveToNext()) {
                scanned += 1
                val body = if (bodyIndex >= 0) cursor.getString(bodyIndex).orEmpty() else ""
                if (regex.containsMatchIn(body)) {
                    matched += 1
                }
            }
        }

        return SmsSyncSummary(
            scannedMessageCount = scanned,
            transactionMessageCount = matched
        )
    }
}
