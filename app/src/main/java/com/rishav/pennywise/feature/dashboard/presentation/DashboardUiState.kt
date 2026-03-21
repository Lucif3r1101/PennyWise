package com.rishav.pennywise.feature.dashboard.presentation

enum class DashboardTab(
    val label: String,
    val iconText: String
) {
    HOME(label = "Home", iconText = "H"),
    AI_INSIGHTS(label = "AI Insights", iconText = "AI"),
    CATEGORIES(label = "Categories", iconText = "C")
}

enum class TrackingStartOption {
    FROM_NOW,
    FROM_THIS_YEAR
}

enum class SetupSheetMode {
    ALL,
    SINGLE
}

enum class SourceType {
    SMS,
    GMAIL,
    OUTLOOK
}

data class SourceSetupUiModel(
    val type: SourceType,
    val title: String,
    val description: String,
    val statusLabel: String,
    val actionLabel: String,
    val isConnected: Boolean,
    val isAvailable: Boolean
)

data class DashboardUiState(
    val selectedTab: DashboardTab = DashboardTab.HOME,
    val trackingStartOption: TrackingStartOption = TrackingStartOption.FROM_NOW,
    val readingProgress: Float = 0f,
    val readingStatus: String = "",
    val readingHint: String = "",
    val lastSyncSummary: String = "",
    val isRefreshing: Boolean = false,
    val smsPermissionGranted: Boolean = false,
    val activeEmailAuthSource: SourceType? = null,
    val setupSheetMode: SetupSheetMode? = null,
    val selectedSourceType: SourceType? = null,
    val sourceItems: List<SourceSetupUiModel> = emptyList()
) {
    val connectedCount: Int = sourceItems.count { it.isConnected }
    val availableCount: Int = sourceItems.count { it.isAvailable }
    val canRefresh: Boolean = connectedCount > 0
}
