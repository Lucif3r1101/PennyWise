package com.rishav.pennywise.core.sms

import android.content.Context
import android.provider.Telephony

data class SmsTransactionRecord(
    val amount: Int,
    val timestampMillis: Long,
    val category: String
)

data class SmsSyncSummary(
    val scannedMessageCount: Int,
    val transactionMessageCount: Int,
    val transactions: List<SmsTransactionRecord>
)

class SmsTransactionReader {

    fun readSummary(context: Context): SmsSyncSummary {
        val projection = arrayOf(
            Telephony.Sms.BODY,
            Telephony.Sms.DATE
        )

        val expenseRegex = Regex(
            pattern = "\\b(debited|spent|purchase|txn|transaction|upi|withdrawn|paid|payment|sent|charged)\\b",
            option = RegexOption.IGNORE_CASE
        )
        val amountRegex = Regex(
            pattern = "(?:Rs\\.?|INR|₹)\\s*([0-9,]+(?:\\.\\d{1,2})?)",
            option = RegexOption.IGNORE_CASE
        )

        var scanned = 0
        val transactions = mutableListOf<SmsTransactionRecord>()

        context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            projection,
            null,
            null,
            "${Telephony.Sms.DATE} DESC"
        )?.use { cursor ->
            val bodyIndex = cursor.getColumnIndex(Telephony.Sms.BODY)
            val dateIndex = cursor.getColumnIndex(Telephony.Sms.DATE)

            while (cursor.moveToNext()) {
                scanned += 1
                val body = if (bodyIndex >= 0) cursor.getString(bodyIndex).orEmpty() else ""
                val timestamp = if (dateIndex >= 0) cursor.getLong(dateIndex) else 0L

                if (!expenseRegex.containsMatchIn(body)) continue

                val amount = amountRegex.find(body)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.replace(",", "")
                    ?.substringBefore(".")
                    ?.toIntOrNull()
                    ?: continue

                transactions += SmsTransactionRecord(
                    amount = amount,
                    timestampMillis = timestamp,
                    category = detectCategory(body)
                )
            }
        }

        return SmsSyncSummary(
            scannedMessageCount = scanned,
            transactionMessageCount = transactions.size,
            transactions = transactions
        )
    }

    private fun detectCategory(body: String): String {
        val text = body.lowercase()
        return when {
            listOf("swiggy", "zomato", "restaurant", "food", "cafe").any(text::contains) -> "Food"
            listOf("uber", "ola", "metro", "irctc", "fuel", "petrol", "diesel", "transport").any(text::contains) -> "Transport"
            listOf("amazon", "flipkart", "myntra", "shopping", "store", "mart").any(text::contains) -> "Shopping"
            listOf("electricity", "water", "gas", "broadband", "recharge", "bill").any(text::contains) -> "Bills"
            else -> "Others"
        }
    }
}
