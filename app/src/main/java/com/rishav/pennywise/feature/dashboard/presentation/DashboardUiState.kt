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
    WEEKLY,
    MONTHLY,
    YEARLY
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

data class ChartPointUiModel(
    val label: String,
    val value: Float,
    val amount: Int
)

data class CategoryBreakdownUiModel(
    val title: String,
    val amount: Int,
    val ratio: Float
)

data class RecentTransactionUiModel(
    val id: String,
    val title: String,
    val subtitle: String,
    val amount: Int,
    val dateLabel: String,
    val sourceLabel: String
)

data class AiInsightUiModel(
    val title: String,
    val description: String
)

data class BudgetUiModel(
    val id: String,
    val category: String,
    val limit: Int,
    val spent: Int
) {
    val progress: Float
        get() = if (limit == 0) 0f else (spent.toFloat() / limit.toFloat()).coerceAtMost(1f)

    val remaining: Int
        get() = (limit - spent).coerceAtLeast(0)
}

data class DashboardUiState(
    val selectedTab: DashboardTab = DashboardTab.HOME,
    val trackingStartOption: TrackingStartOption = TrackingStartOption.WEEKLY,
    val readingProgress: Float = 0f,
    val readingStatus: String = "",
    val readingHint: String = "",
    val lastSyncSummary: String = "",
    val isRefreshing: Boolean = false,
    val smsPermissionGranted: Boolean = false,
    val activeEmailAuthSource: SourceType? = null,
    val setupSheetMode: SetupSheetMode? = null,
    val selectedSourceType: SourceType? = null,
    val sourceItems: List<SourceSetupUiModel> = emptyList(),
    val totalExpense: Int = 6480,
    val budgetTarget: Int = 9000,
    val chartPoints: List<ChartPointUiModel> = emptyList(),
    val selectedChartPointIndex: Int = 0,
    val categoryBreakdown: List<CategoryBreakdownUiModel> = emptyList(),
    val latestTransaction: RecentTransactionUiModel? = null,
    val recentTransactions: List<RecentTransactionUiModel> = emptyList(),
    val aiInsights: List<AiInsightUiModel> = emptyList(),
    val budgets: List<BudgetUiModel> = emptyList(),
    val budgetDraftCategory: String = "Food",
    val budgetDraftAmount: String = ""
) {
    val connectedCount: Int = sourceItems.count { it.isConnected }
    val availableCount: Int = sourceItems.count { it.isAvailable }
    val canRefresh: Boolean = connectedCount > 0
    val selectedChartPoint: ChartPointUiModel?
        get() = chartPoints.getOrNull(selectedChartPointIndex)
    val configuredBudgetTotal: Int
        get() = budgets.sumOf { it.limit }
}
