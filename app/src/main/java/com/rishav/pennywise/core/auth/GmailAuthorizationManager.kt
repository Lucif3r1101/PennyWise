package com.rishav.pennywise.core.auth

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.rishav.pennywise.core.sms.SmsTransactionRecord
import com.rishav.pennywise.core.transactions.LocalTransactionEngine
import com.rishav.pennywise.feature.dashboard.presentation.SourceType
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

class GmailAuthorizationManager {

    fun startAuthorization(
        activity: Activity,
        launcher: ActivityResultLauncher<IntentSenderRequest>
    ) {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(
                listOf(
                    Scope(GMAIL_READONLY_SCOPE),
                    Scope(EMAIL_SCOPE),
                    Scope(PROFILE_SCOPE)
                )
            )
            .build()

        Identity.getAuthorizationClient(activity)
            .authorize(request)
            .addOnSuccessListener { result ->
                if (result.hasResolution()) {
                    val pendingIntent = result.pendingIntent
                    if (pendingIntent != null) {
                        launcher.launch(
                            IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                        )
                    }
                } else {
                    // Fallback path if Google Play services can grant without further UI.
                    launcher.launch(
                        IntentSenderRequest.Builder(
                            android.app.PendingIntent.getActivity(
                                activity,
                                404,
                                Intent(),
                                android.app.PendingIntent.FLAG_IMMUTABLE
                            ).intentSender
                        ).build()
                    )
                }
            }
    }

    fun completeAuthorization(context: Context, data: Intent?): Result<EmailAuthSummary> {
        val authorizationResult = Identity.getAuthorizationClient(context)
            .getAuthorizationResultFromIntent(data)
        val accessToken = authorizationResult.accessToken
        if (accessToken.isNullOrBlank()) {
            return Result.failure(IllegalStateException("Missing Gmail access token"))
        }
        return runCatching { fetchGmailSummary(accessToken) }
    }

    private fun fetchGmailSummary(accessToken: String): EmailAuthSummary {
        val connection = URL("https://gmail.googleapis.com/gmail/v1/users/me/messages?maxResults=30&q=newer_than:365d").openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("Authorization", "Bearer $accessToken")
        connection.setRequestProperty("Accept", "application/json")
        val response = connection.inputStream.bufferedReader().use(BufferedReader::readText)
        connection.disconnect()
        val json = JSONObject(response)
        val messages = json.optJSONArray("messages")
        val transactions = mutableListOf<SmsTransactionRecord>()
        for (index in 0 until minOf(messages?.length() ?: 0, 18)) {
            val id = messages?.optJSONObject(index)?.optString("id").orEmpty()
            if (id.isBlank()) continue
            fetchGmailMessage(accessToken, id)?.let(transactions::add)
        }
        return EmailAuthSummary(
            provider = SourceType.GMAIL,
            matchedCount = transactions.size,
            description = "Gmail connected and parsed ${transactions.size} likely transaction emails locally.",
            transactions = transactions
        )
    }

    private fun fetchGmailMessage(accessToken: String, messageId: String): SmsTransactionRecord? {
        val connection = URL("https://gmail.googleapis.com/gmail/v1/users/me/messages/$messageId?format=metadata").openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("Authorization", "Bearer $accessToken")
        connection.setRequestProperty("Accept", "application/json")
        val response = connection.inputStream.bufferedReader().use(BufferedReader::readText)
        connection.disconnect()
        val json = JSONObject(response)
        val snippet = json.optString("snippet")
        val timestamp = json.optString("internalDate")
            .toLongOrNull()
            ?: Instant.now().toEpochMilli()
        val parsed = LocalTransactionEngine.parse(snippet, timestamp) ?: return null
        return SmsTransactionRecord(
            amount = parsed.amount,
            timestampMillis = parsed.timestampMillis,
            category = parsed.category,
            merchant = parsed.merchant,
            kind = parsed.kind,
            confidence = parsed.confidence,
            referenceId = parsed.referenceId
        )
    }

    companion object {
        private const val GMAIL_READONLY_SCOPE = "https://www.googleapis.com/auth/gmail.readonly"
        private const val EMAIL_SCOPE = "email"
        private const val PROFILE_SCOPE = "profile"
    }
}
