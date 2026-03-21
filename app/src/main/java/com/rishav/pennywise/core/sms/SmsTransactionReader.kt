package com.rishav.pennywise.core.sms

import android.content.Context
import android.provider.Telephony
import com.rishav.pennywise.core.transactions.LocalTransactionEngine
import com.rishav.pennywise.core.transactions.TransactionKind

data class SmsTransactionRecord(
    val amount: Int,
    val timestampMillis: Long,
    val category: String,
    val merchant: String? = null,
    val kind: TransactionKind = TransactionKind.EXPENSE,
    val confidence: Float = 0f,
    val referenceId: String? = null
)

data class SmsSyncSummary(
    val scannedMessageCount: Int,
    val transactionMessageCount: Int,
    val transactions: List<SmsTransactionRecord>
)

class SmsTransactionReader {

    fun readSummary(context: Context): SmsSyncSummary {
        val projection = arrayOf(
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE
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
            val addressIndex = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
            val bodyIndex = cursor.getColumnIndex(Telephony.Sms.BODY)
            val dateIndex = cursor.getColumnIndex(Telephony.Sms.DATE)

            while (cursor.moveToNext()) {
                scanned += 1
                val address = if (addressIndex >= 0) cursor.getString(addressIndex).orEmpty() else ""
                val body = if (bodyIndex >= 0) cursor.getString(bodyIndex).orEmpty() else ""
                val timestamp = if (dateIndex >= 0) cursor.getLong(dateIndex) else 0L
                val parsed = LocalTransactionEngine.parse(
                    content = body,
                    timestampMillis = timestamp,
                    sender = address
                ) ?: continue

                transactions += SmsTransactionRecord(
                    amount = parsed.amount,
                    timestampMillis = parsed.timestampMillis,
                    category = parsed.category,
                    merchant = parsed.merchant,
                    kind = parsed.kind,
                    confidence = parsed.confidence,
                    referenceId = parsed.referenceId
                )
            }
        }

        return SmsSyncSummary(
            scannedMessageCount = scanned,
            transactionMessageCount = transactions.size,
            transactions = transactions
        )
    }
}
