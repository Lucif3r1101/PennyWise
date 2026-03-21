package com.rishav.pennywise.feature.dashboard.presentation

import androidx.lifecycle.ViewModel
import com.rishav.pennywise.BuildConfig
import com.rishav.pennywise.core.auth.EmailAuthSummary
import com.rishav.pennywise.core.sms.SmsSyncSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class DashboardViewModel : ViewModel() {

    private val gmailConfigured = BuildConfig.GMAIL_CONFIGURED
    private val outlookConfigured = BuildConfig.OUTLOOK_CONFIGURED

    private val _uiState = MutableStateFlow(
        DashboardUiState(
            readingProgress = 0.18f,
            readingStatus = "Waiting for your first connected source",
            readingHint = "Connect SMS, Gmail, or Outlook to begin reading data.",
            sourceItems = buildSourceItems(
                smsPermissionGranted = false,
                gmailConnected = false,
                outlookConnected = false
            )
        )
    )
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    fun onTabSelected(tab: DashboardTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun onTrackingStartOptionSelected(option: TrackingStartOption) {
        _uiState.update {
            it.copy(
                trackingStartOption = option,
                readingProgress = if (it.connectedCount == 0) 0.18f else if (option == TrackingStartOption.FROM_NOW) 0.36f else 0.58f,
                readingStatus = if (it.connectedCount == 0) {
                    "Waiting for your first connected source"
                } else if (option == TrackingStartOption.FROM_NOW) {
                    "Tracking from this moment onward"
                } else {
                    "Importing messages and receipts from the start of this year"
                },
                readingHint = if (option == TrackingStartOption.FROM_NOW) {
                    "Best for quick setup with fresh tracking."
                } else {
                    "Best for avoiding cold-start gaps in charts and categories."
                }
            )
        }
    }

    fun onAllowAllClick() {
        _uiState.update {
            it.copy(
                setupSheetMode = SetupSheetMode.ALL,
                selectedSourceType = firstIncompleteSource(it.sourceItems)
            )
        }
    }

    fun onSourceSelected(type: SourceType) {
        _uiState.update {
            it.copy(
                setupSheetMode = SetupSheetMode.SINGLE,
                selectedSourceType = type
            )
        }
    }

    fun onDismissSetupSheet() {
        _uiState.update { it.copy(setupSheetMode = null, selectedSourceType = null) }
    }

    fun onGiveLaterClick() {
        _uiState.update {
            it.copy(
                setupSheetMode = null,
                selectedSourceType = null,
                activeEmailAuthSource = null,
                readingStatus = "Setup paused until you connect a source",
                readingHint = "You can return here anytime and enable SMS, Gmail, or Outlook."
            )
        }
    }

    fun onSmsPermissionStateChanged(granted: Boolean) {
        _uiState.update {
            val gmailConnected = it.sourceItems.firstOrNull { item -> item.type == SourceType.GMAIL }?.isConnected == true
            val outlookConnected = it.sourceItems.firstOrNull { item -> item.type == SourceType.OUTLOOK }?.isConnected == true
            it.copy(
                smsPermissionGranted = granted,
                sourceItems = buildSourceItems(
                    smsPermissionGranted = granted,
                    gmailConnected = gmailConnected,
                    outlookConnected = outlookConnected
                )
            )
        }
    }

    fun connectConfiguredEmailSources() {
        _uiState.update {
            it.copy(
                sourceItems = buildSourceItems(
                    smsPermissionGranted = it.smsPermissionGranted,
                    gmailConnected = gmailConfigured,
                    outlookConnected = outlookConfigured
                ),
                setupSheetMode = null,
                selectedSourceType = null,
                activeEmailAuthSource = null,
                readingStatus = if (it.smsPermissionGranted) {
                    "Sources connected and ready to refresh"
                } else {
                    "Email providers are ready, SMS still needs permission"
                },
                readingHint = "Email providers are configured locally. Real email reading will require OAuth sign-in next."
            )
        }
    }

    fun onSmsSyncStarted() {
        _uiState.update {
            it.copy(
                isRefreshing = true,
                readingStatus = "Reading SMS transaction history",
                readingHint = "Scanning your inbox for transaction-related messages."
            )
        }
    }

    fun onSmsSyncCompleted(summary: SmsSyncSummary) {
        _uiState.update {
            val progress = if (it.trackingStartOption == TrackingStartOption.FROM_THIS_YEAR) 0.92f else 0.74f
            it.copy(
                isRefreshing = false,
                readingProgress = progress,
                lastSyncSummary = "Scanned ${summary.scannedMessageCount} SMS and found ${summary.transactionMessageCount} likely transaction messages",
                readingStatus = if (summary.transactionMessageCount > 0) {
                    "Transaction SMS imported successfully"
                } else {
                    "SMS scan completed but no transaction messages were matched"
                },
                readingHint = "Refresh again anytime after new alerts arrive."
            )
        }
    }

    fun onSmsSyncFailed(message: String) {
        _uiState.update {
            it.copy(
                isRefreshing = false,
                readingStatus = "SMS sync could not be completed",
                readingHint = message
            )
        }
    }

    fun onSetupPrimaryHandledForCurrentSelection() {
        _uiState.update { it.copy(setupSheetMode = null, selectedSourceType = null, activeEmailAuthSource = null) }
    }

    fun onEmailAuthStarted(sourceType: SourceType) {
        _uiState.update {
            it.copy(
                activeEmailAuthSource = sourceType,
                readingStatus = "Opening ${sourceType.name.lowercase().replaceFirstChar(Char::uppercase)} sign-in",
                readingHint = "Complete the browser sign-in flow to let PennyWise read transaction emails."
            )
        }
    }

    fun onEmailAuthCompleted(summary: EmailAuthSummary) {
        _uiState.update {
            val updatedItems = it.sourceItems.map { item ->
                if (item.type == summary.provider) {
                    item.copy(
                        statusLabel = "Connected",
                        actionLabel = "Connected",
                        isConnected = true
                    )
                } else {
                    item
                }
            }
            it.copy(
                sourceItems = updatedItems,
                setupSheetMode = null,
                selectedSourceType = null,
                activeEmailAuthSource = null,
                readingProgress = if (it.smsPermissionGranted) 0.82f else 0.48f,
                readingStatus = "${summary.provider.name.lowercase().replaceFirstChar(Char::uppercase)} inbox connected",
                readingHint = "Transaction emails can now be included during future sync cycles.",
                lastSyncSummary = summary.description
            )
        }
    }

    fun onEmailAuthFailed(sourceType: SourceType, message: String) {
        _uiState.update {
            it.copy(
                activeEmailAuthSource = null,
                readingStatus = "${sourceType.name.lowercase().replaceFirstChar(Char::uppercase)} connection failed",
                readingHint = message
            )
        }
    }

    private fun buildSourceItems(
        smsPermissionGranted: Boolean,
        gmailConnected: Boolean,
        outlookConnected: Boolean
    ): List<SourceSetupUiModel> {
        return listOf(
            SourceSetupUiModel(
                type = SourceType.SMS,
                title = "SMS Access",
                description = "Read bank, card and UPI transaction messages automatically.",
                statusLabel = if (smsPermissionGranted) "Connected" else "Required",
                actionLabel = if (smsPermissionGranted) "Connected" else "Enable",
                isConnected = smsPermissionGranted,
                isAvailable = true
            ),
            SourceSetupUiModel(
                type = SourceType.GMAIL,
                title = "Gmail",
                description = "Connect Gmail to import receipt emails and finance alerts.",
                statusLabel = when {
                    gmailConnected -> "Connected"
                    gmailConfigured -> "Ready to connect"
                    else -> "Missing local config"
                },
                actionLabel = if (gmailConnected) "Connected" else "Connect",
                isConnected = gmailConnected,
                isAvailable = gmailConfigured
            ),
            SourceSetupUiModel(
                type = SourceType.OUTLOOK,
                title = "Outlook",
                description = "Connect Outlook to include work reimbursements and billing receipts.",
                statusLabel = when {
                    outlookConnected -> "Connected"
                    outlookConfigured -> "Ready to connect"
                    else -> "Missing local config"
                },
                actionLabel = if (outlookConnected) "Connected" else "Connect",
                isConnected = outlookConnected,
                isAvailable = outlookConfigured
            )
        )
    }

    private fun firstIncompleteSource(items: List<SourceSetupUiModel>): SourceType? {
        return items.firstOrNull { item -> item.isAvailable && !item.isConnected }?.type
    }
}
