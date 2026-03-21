package com.rishav.pennywise.core.auth

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.rishav.pennywise.BuildConfig
import com.rishav.pennywise.MainActivity
import com.rishav.pennywise.core.sms.SmsTransactionRecord
import com.rishav.pennywise.feature.dashboard.presentation.SourceType
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues
import org.json.JSONObject
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.OffsetDateTime

data class EmailAuthSummary(
    val provider: SourceType,
    val matchedCount: Int,
    val description: String,
    val transactions: List<SmsTransactionRecord>
)

class EmailAuthManager {

    fun startAuthorization(activity: Activity, provider: SourceType) {
        val config = providerConfig(provider) ?: return
        val service = AuthorizationService(activity)
        val request = AuthorizationRequest.Builder(
            config.serviceConfiguration,
            config.clientId,
            ResponseTypeValues.CODE,
            config.redirectUri
        ).setScope(config.scope).build()

        val completionIntent = Intent(activity, MainActivity::class.java).apply {
            action = ACTION_AUTH_COMPLETE
            putExtra(EXTRA_PROVIDER, provider.name)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val cancelIntent = Intent(activity, MainActivity::class.java).apply {
            action = ACTION_AUTH_CANCELLED
            putExtra(EXTRA_PROVIDER, provider.name)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        service.performAuthorizationRequest(
            request,
            PendingIntent.getActivity(activity, provider.ordinal + 40, completionIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE),
            PendingIntent.getActivity(activity, provider.ordinal + 80, cancelIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        )
    }

    fun isAuthRedirect(intent: Intent): Boolean {
        return intent.action == ACTION_AUTH_COMPLETE || intent.action == ACTION_AUTH_CANCELLED
    }

    fun completeAuthorization(context: Context, intent: Intent): Result<EmailAuthSummary> {
        val provider = intent.getStringExtra(EXTRA_PROVIDER)?.let(SourceType::valueOf)
            ?: return Result.failure(IllegalArgumentException("Missing auth provider"))

        if (intent.action == ACTION_AUTH_CANCELLED) {
            return Result.failure(IllegalStateException("${provider.name} sign-in was cancelled"))
        }

        val response = AuthorizationResponse.fromIntent(intent)
        val exception = AuthorizationException.fromIntent(intent)

        if (exception != null) {
            return Result.failure(exception)
        }

        response ?: return Result.failure(IllegalStateException("Missing authorization response"))

        val config = providerConfig(provider)
            ?: return Result.failure(IllegalArgumentException("Unsupported provider"))

        val tokenJson = exchangeToken(response, config)
        val accessToken = tokenJson.optString("access_token")
        if (accessToken.isBlank()) {
            return Result.failure(IllegalStateException("Missing access token"))
        }

        val summary = when (provider) {
            SourceType.GMAIL -> fetchGmailSummary(accessToken)
            SourceType.OUTLOOK -> fetchOutlookSummary(accessToken)
            SourceType.SMS -> return Result.failure(IllegalArgumentException("SMS does not use OAuth"))
        }

        return Result.success(summary)
    }

    private fun exchangeToken(response: AuthorizationResponse, config: ProviderConfig): JSONObject {
        val request = response.createTokenExchangeRequest()
        val body = buildString {
            append("grant_type=authorization_code")
            append("&code=").append(Uri.encode(request.authorizationCode))
            append("&redirect_uri=").append(Uri.encode(request.redirectUri.toString()))
            append("&client_id=").append(Uri.encode(config.clientId))
            append("&code_verifier=").append(Uri.encode(request.codeVerifier))
        }
        return postForm(config.tokenEndpoint.toString(), body)
    }

    private fun fetchGmailSummary(accessToken: String): EmailAuthSummary {
        val connection = URL("https://gmail.googleapis.com/gmail/v1/users/me/messages?maxResults=25&q=newer_than:365d").openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("Authorization", "Bearer $accessToken")
        connection.setRequestProperty("Accept", "application/json")
        val response = connection.inputStream.bufferedReader().use(BufferedReader::readText)
        connection.disconnect()
        val json = JSONObject(response)
        val messages = json.optJSONArray("messages")
        val transactions = mutableListOf<SmsTransactionRecord>()
        for (index in 0 until minOf(messages?.length() ?: 0, 15)) {
            val id = messages?.optJSONObject(index)?.optString("id").orEmpty()
            if (id.isBlank()) continue
            fetchGmailMessage(accessToken, id)?.let(transactions::add)
        }
        return EmailAuthSummary(
            provider = SourceType.GMAIL,
            matchedCount = transactions.size,
            description = "Gmail connected and parsed ${transactions.size} likely transaction emails.",
            transactions = transactions
        )
    }

    private fun fetchOutlookSummary(accessToken: String): EmailAuthSummary {
        val connection = URL("https://graph.microsoft.com/v1.0/me/messages?\$top=25&\$select=subject,bodyPreview,receivedDateTime").openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("Authorization", "Bearer $accessToken")
        connection.setRequestProperty("Accept", "application/json")
        val response = connection.inputStream.bufferedReader().use(BufferedReader::readText)
        connection.disconnect()
        val json = JSONObject(response)
        val messages = json.optJSONArray("value")
        val transactions = mutableListOf<SmsTransactionRecord>()
        for (index in 0 until (messages?.length() ?: 0)) {
            val message = messages?.optJSONObject(index) ?: continue
            parseEmailTransaction(
                content = listOf(
                    message.optString("subject"),
                    message.optString("bodyPreview")
                ).joinToString(" "),
                timestampMillis = message.optString("receivedDateTime")
                    .takeIf { it.isNotBlank() }
                    ?.let { OffsetDateTime.parse(it).toInstant().toEpochMilli() }
                    ?: System.currentTimeMillis()
            )?.let(transactions::add)
        }
        return EmailAuthSummary(
            provider = SourceType.OUTLOOK,
            matchedCount = transactions.size,
            description = "Outlook connected and parsed ${transactions.size} likely transaction emails.",
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
        return parseEmailTransaction(snippet, timestamp)
    }

    private fun postForm(url: String, body: String): JSONObject {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        OutputStreamWriter(connection.outputStream).use { it.write(body) }
        val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
        val response = stream.bufferedReader().use(BufferedReader::readText)
        connection.disconnect()
        return JSONObject(response)
    }

    private fun providerConfig(provider: SourceType): ProviderConfig? {
        return when (provider) {
            SourceType.GMAIL -> if (BuildConfig.GMAIL_CONFIGURED) {
                ProviderConfig(
                    clientId = BuildConfig.GMAIL_CLIENT_ID,
                    redirectUri = Uri.parse("${BuildConfig.GMAIL_REDIRECT_SCHEME}://${BuildConfig.GMAIL_REDIRECT_HOST}"),
                    scope = "https://www.googleapis.com/auth/gmail.readonly",
                    serviceConfiguration = AuthorizationServiceConfiguration(
                        Uri.parse("https://accounts.google.com/o/oauth2/v2/auth"),
                        Uri.parse("https://oauth2.googleapis.com/token")
                    ),
                    tokenEndpoint = Uri.parse("https://oauth2.googleapis.com/token")
                )
            } else null

            SourceType.OUTLOOK -> if (BuildConfig.OUTLOOK_CONFIGURED) {
                val tenant = BuildConfig.OUTLOOK_TENANT_ID.ifBlank { "common" }
                ProviderConfig(
                    clientId = BuildConfig.OUTLOOK_CLIENT_ID,
                    redirectUri = Uri.parse("${BuildConfig.OUTLOOK_REDIRECT_SCHEME}://${BuildConfig.OUTLOOK_REDIRECT_HOST}"),
                    scope = "openid profile offline_access User.Read Mail.Read",
                    serviceConfiguration = AuthorizationServiceConfiguration(
                        Uri.parse("https://login.microsoftonline.com/$tenant/oauth2/v2.0/authorize"),
                        Uri.parse("https://login.microsoftonline.com/$tenant/oauth2/v2.0/token")
                    ),
                    tokenEndpoint = Uri.parse("https://login.microsoftonline.com/$tenant/oauth2/v2.0/token")
                )
            } else null

            SourceType.SMS -> null
        }
    }

    private data class ProviderConfig(
        val clientId: String,
        val redirectUri: Uri,
        val scope: String,
        val serviceConfiguration: AuthorizationServiceConfiguration,
        val tokenEndpoint: Uri
    )

    companion object {
        const val ACTION_AUTH_COMPLETE = "com.rishav.pennywise.AUTH_COMPLETE"
        const val ACTION_AUTH_CANCELLED = "com.rishav.pennywise.AUTH_CANCELLED"
        const val EXTRA_PROVIDER = "auth_provider"
    }

    private fun parseEmailTransaction(
        content: String,
        timestampMillis: Long
    ): SmsTransactionRecord? {
        val expenseRegex = Regex(
            pattern = "\\b(debited|spent|purchase|txn|transaction|upi|withdrawn|paid|payment|charged|order|receipt|invoice)\\b",
            option = RegexOption.IGNORE_CASE
        )
        val amountRegex = Regex(
            pattern = "(?:Rs\\.?|INR|₹)\\s*([0-9,]+(?:\\.\\d{1,2})?)",
            option = RegexOption.IGNORE_CASE
        )

        if (!expenseRegex.containsMatchIn(content)) return null

        val amount = amountRegex.find(content)
            ?.groupValues
            ?.getOrNull(1)
            ?.replace(",", "")
            ?.substringBefore(".")
            ?.toIntOrNull()
            ?: return null

        return SmsTransactionRecord(
            amount = amount,
            timestampMillis = timestampMillis,
            category = detectCategory(content)
        )
    }

    private fun detectCategory(content: String): String {
        val text = content.lowercase()
        return when {
            listOf("swiggy", "zomato", "restaurant", "food", "cafe").any(text::contains) -> "Food"
            listOf("uber", "ola", "metro", "irctc", "fuel", "petrol", "diesel", "transport").any(text::contains) -> "Transport"
            listOf("amazon", "flipkart", "myntra", "shopping", "store", "mart").any(text::contains) -> "Shopping"
            listOf("electricity", "water", "gas", "broadband", "recharge", "bill", "invoice").any(text::contains) -> "Bills"
            else -> "Others"
        }
    }
}
