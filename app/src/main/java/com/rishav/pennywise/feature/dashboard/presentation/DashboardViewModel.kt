package com.rishav.pennywise.feature.dashboard.presentation

import androidx.lifecycle.ViewModel
import com.rishav.pennywise.core.ai.LocalAiOrchestrator
import com.rishav.pennywise.core.auth.EmailAuthSummary
import com.rishav.pennywise.core.sms.SmsSyncSummary
import com.rishav.pennywise.core.sms.SmsTransactionRecord
import com.rishav.pennywise.core.transactions.TransactionKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.ceil

class DashboardViewModel : ViewModel() {

    private val zoneId = ZoneId.systemDefault()
    private val localAiOrchestrator = LocalAiOrchestrator()
    private var latestSmsTransactions: List<TrackedTransaction> = emptyList()
    private var latestEmailTransactions: List<TrackedTransaction> = emptyList()

    private val defaultBudgets = listOf(
        BudgetUiModel(
            id = "budget-food",
            category = "Food",
            limit = 6000,
            spent = 1880
        )
    )

    private val _uiState = MutableStateFlow(
        DashboardUiState(
            readingProgress = 0.18f,
            readingStatus = "Waiting for your first connected source",
            readingHint = "Connect SMS to begin reading transaction data.",
            sourceItems = buildSourceItems(
                smsPermissionGranted = false
            ),
            chartPoints = placeholderChartPoints(TrackingStartOption.WEEKLY),
            selectedChartPointIndex = placeholderChartPoints(TrackingStartOption.WEEKLY).lastIndex,
            categoryBreakdown = placeholderCategories(6480),
            totalExpense = 6480,
            budgetTarget = 9000,
            budgets = defaultBudgets,
            budgetDraftCategory = "Food",
            aiInsights = localAiOrchestrator.generateInsights(
                transactions = emptyList(),
                categories = placeholderCategories(6480),
                budgets = defaultBudgets
            )
        )
    )
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    fun onTabSelected(tab: DashboardTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun onTrackingStartOptionSelected(option: TrackingStartOption) {
        _uiState.update { state ->
            val refreshed = enrichStateForTransactions(
                state = state.copy(trackingStartOption = option),
                transactions = allTransactions()
            )
            refreshed.copy(
                readingProgress = trackingProgress(refreshed.connectedCount > 0, option),
                readingStatus = trackingStatus(refreshed.connectedCount > 0, option),
                readingHint = trackingHint(option)
            )
        }
    }

    fun onChartPointSelected(index: Int) {
        _uiState.update { state ->
            state.copy(selectedChartPointIndex = index.coerceIn(0, state.chartPoints.lastIndex.coerceAtLeast(0)))
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
                readingHint = "You can return here anytime and enable SMS."
            )
        }
    }

    fun onSmsPermissionStateChanged(granted: Boolean) {
        _uiState.update { state ->
            state.copy(
                smsPermissionGranted = granted,
                sourceItems = buildSourceItems(smsPermissionGranted = granted)
            )
        }
    }

    fun onConfiguredSourcesAcknowledged() {
        _uiState.update {
            it.copy(
                setupSheetMode = null,
                selectedSourceType = null,
                activeEmailAuthSource = null,
                readingStatus = if (it.smsPermissionGranted) {
                    "SMS is connected and ready to track transactions."
                } else {
                    "SMS still needs permission."
                },
                readingHint = "Email sources are paused for now. We will add them back later."
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
        latestSmsTransactions = summary.transactions.map {
            TrackedTransaction(record = it, sourceType = SourceType.SMS)
        }
        _uiState.update { state ->
            val refreshed = enrichStateForTransactions(state, allTransactions())
            refreshed.copy(
                isRefreshing = false,
                readingProgress = trackingProgress(true, refreshed.trackingStartOption),
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
        latestEmailTransactions = latestEmailTransactions + summary.transactions.map {
            TrackedTransaction(record = it, sourceType = summary.provider)
        }
        _uiState.update { state ->
            val updatedItems = state.sourceItems.map { item ->
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
            val refreshed = enrichStateForTransactions(
                state = state.copy(sourceItems = updatedItems),
                transactions = allTransactions()
            )
            refreshed.copy(
                setupSheetMode = null,
                selectedSourceType = null,
                activeEmailAuthSource = null,
                readingProgress = if (refreshed.smsPermissionGranted) 0.82f else 0.48f,
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

    fun onBudgetDraftCategoryChanged(category: String) {
        _uiState.update { it.copy(budgetDraftCategory = category) }
    }

    fun onBudgetDraftAmountChanged(amount: String) {
        _uiState.update {
            it.copy(budgetDraftAmount = amount.filter(Char::isDigit).take(6))
        }
    }

    fun onCreateBudget() {
        _uiState.update { state ->
            val amount = state.budgetDraftAmount.toIntOrNull() ?: return@update state
            if (amount <= 0) return@update state

            val updatedBudgets = state.budgets
                .filterNot { it.category.equals(state.budgetDraftCategory, ignoreCase = true) } + BudgetUiModel(
                id = "budget-${state.budgetDraftCategory.lowercase()}",
                category = state.budgetDraftCategory,
                limit = amount,
                spent = state.categoryBreakdown.firstOrNull {
                    it.title.equals(state.budgetDraftCategory, ignoreCase = true)
                }?.amount ?: 0
            )

            state.copy(
                budgets = updatedBudgets.sortedBy { it.category },
                budgetDraftAmount = "",
                readingStatus = "Budget saved for ${state.budgetDraftCategory}",
                readingHint = "Home now reflects this budget alongside your live spending data."
            )
        }
    }

    private fun buildSourceItems(
        smsPermissionGranted: Boolean
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
            )
        )
    }

    private fun enrichStateForTransactions(
        state: DashboardUiState,
        transactions: List<TrackedTransaction>
    ): DashboardUiState {
        val preferredTransactions = preferredTransactions(transactions)
        val analytics = computeAnalytics(transactions, state.trackingStartOption)
        val syncedBudgets = syncBudgetsWithCategories(state.budgets, analytics.categories)
        val budgetTarget = syncedBudgets.sumOf { it.limit }
            .takeIf { it > 0 }
            ?: analytics.suggestedBudget

        return state.copy(
            totalExpense = analytics.totalExpense,
            budgetTarget = budgetTarget,
            chartPoints = analytics.chartPoints,
            selectedChartPointIndex = analytics.defaultPointIndex,
            categoryBreakdown = analytics.categories,
            latestTransaction = mapLatestTransaction(preferredTransactions.maxByOrNull { it.record.timestampMillis }),
            recentTransactions = preferredTransactions
                .sortedByDescending { it.record.timestampMillis }
                .take(6)
                .map(::mapRecentTransaction),
            aiInsights = localAiOrchestrator.generateInsights(
                transactions = preferredTransactions.map { it.record },
                categories = analytics.categories,
                budgets = syncedBudgets
            ),
            budgets = syncedBudgets
        )
    }

    private fun computeAnalytics(
        transactions: List<TrackedTransaction>,
        option: TrackingStartOption
    ): AnalyticsSnapshot {
        val filteredTransactions = preferredTransactions(transactions)
        if (filteredTransactions.isEmpty()) {
            val fallbackTotal = when (option) {
                TrackingStartOption.WEEKLY -> 6480
                TrackingStartOption.MONTHLY -> 18320
                TrackingStartOption.YEARLY -> 74260
            }
            val points = placeholderChartPoints(option)
            return AnalyticsSnapshot(
                totalExpense = fallbackTotal,
                suggestedBudget = (fallbackTotal * 1.35f).toInt(),
                chartPoints = points,
                defaultPointIndex = points.lastIndex.coerceAtLeast(0),
                categories = placeholderCategories(fallbackTotal)
            )
        }

        val now = LocalDate.now(zoneId)

        return when (option) {
            TrackingStartOption.WEEKLY -> {
                val weekStart = now.with(DayOfWeek.MONDAY)
                val labels = (0..6).map { weekStart.plusDays(it.toLong()) }
                val totals = labels.associateWith { 0 }.toMutableMap()
                val filtered = filteredTransactions.filter { tracked ->
                    val date = tracked.record.localDate()
                    !date.isBefore(weekStart) && !date.isAfter(weekStart.plusDays(6))
                }
                filtered.forEach { tracked ->
                    val date = tracked.record.localDate()
                    totals[date] = (totals[date] ?: 0) + tracked.record.amount
                }
                analyticsFromTotals(
                    totals = labels.map { date ->
                        date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH) to (totals[date] ?: 0)
                    },
                    filteredTransactions = filtered
                )
            }

            TrackingStartOption.MONTHLY -> {
                val month = YearMonth.from(now)
                val weekCount = ceil(month.lengthOfMonth() / 7.0).toInt()
                val labels = (1..weekCount).map { "W$it" }
                val totals = labels.associateWith { 0 }.toMutableMap()
                val filtered = filteredTransactions.filter { tracked ->
                    YearMonth.from(tracked.record.localDate()) == month
                }
                filtered.forEach { tracked ->
                    val weekIndex = ((tracked.record.localDate().dayOfMonth - 1) / 7) + 1
                    val label = "W$weekIndex"
                    totals[label] = (totals[label] ?: 0) + tracked.record.amount
                }
                analyticsFromTotals(
                    totals = labels.map { it to (totals[it] ?: 0) },
                    filteredTransactions = filtered
                )
            }

            TrackingStartOption.YEARLY -> {
                val monthLabels = (1..now.monthValue).map { YearMonth.of(now.year, it) }
                val totals = monthLabels.associateWith { 0 }.toMutableMap()
                val filtered = filteredTransactions.filter { tracked ->
                    tracked.record.localDate().year == now.year
                }
                filtered.forEach { tracked ->
                    val month = YearMonth.from(tracked.record.localDate())
                    totals[month] = (totals[month] ?: 0) + tracked.record.amount
                }
                analyticsFromTotals(
                    totals = monthLabels.map { month ->
                        month.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH) to (totals[month] ?: 0)
                    },
                    filteredTransactions = filtered
                )
            }
        }
    }

    private fun analyticsFromTotals(
        totals: List<Pair<String, Int>>,
        filteredTransactions: List<TrackedTransaction>
    ): AnalyticsSnapshot {
        val max = totals.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
        val totalExpense = filteredTransactions.sumOf { it.record.amount }.coerceAtLeast(totals.sumOf { it.second })
        val categoryTotals = filteredTransactions
            .groupBy { it.record.category }
            .mapValues { entry -> entry.value.sumOf { it.record.amount } }
            .toList()
            .sortedByDescending { it.second }
            .take(5)

        val points = totals.map { (label, amount) ->
            ChartPointUiModel(
                label = label,
                value = amount.toFloat() / max.toFloat(),
                amount = amount
            )
        }
        val defaultIndex = points.indexOfLast { it.amount > 0 }.takeIf { it >= 0 } ?: points.lastIndex.coerceAtLeast(0)

        return AnalyticsSnapshot(
            totalExpense = totalExpense.coerceAtLeast(1),
            suggestedBudget = (totalExpense * 1.25f).toInt().coerceAtLeast(5000),
            chartPoints = points,
            defaultPointIndex = defaultIndex,
            categories = if (categoryTotals.isEmpty()) {
                placeholderCategories(totalExpense.coerceAtLeast(1))
            } else {
                categoryTotals.map { (title, amount) ->
                    CategoryBreakdownUiModel(
                        title = title,
                        amount = amount,
                        ratio = amount.toFloat() / totalExpense.coerceAtLeast(1).toFloat()
                    )
                }
            }
        )
    }

    private fun placeholderChartPoints(option: TrackingStartOption): List<ChartPointUiModel> {
        return when (option) {
            TrackingStartOption.WEEKLY -> listOf(
                ChartPointUiModel("Mon", 0.18f, 1200),
                ChartPointUiModel("Tue", 0.34f, 2280),
                ChartPointUiModel("Wed", 0.26f, 1740),
                ChartPointUiModel("Thu", 0.52f, 3480),
                ChartPointUiModel("Fri", 0.46f, 3080),
                ChartPointUiModel("Sat", 0.71f, 4760),
                ChartPointUiModel("Sun", 0.39f, 2620)
            )

            TrackingStartOption.MONTHLY -> listOf(
                ChartPointUiModel("W1", 0.31f, 3120),
                ChartPointUiModel("W2", 0.56f, 5640),
                ChartPointUiModel("W3", 0.44f, 4420),
                ChartPointUiModel("W4", 0.68f, 6840),
                ChartPointUiModel("W5", 0.49f, 4930)
            )

            TrackingStartOption.YEARLY -> listOf(
                ChartPointUiModel("Jan", 0.28f, 8200),
                ChartPointUiModel("Feb", 0.34f, 9900),
                ChartPointUiModel("Mar", 0.38f, 11240),
                ChartPointUiModel("Apr", 0.43f, 12680),
                ChartPointUiModel("May", 0.41f, 12090),
                ChartPointUiModel("Jun", 0.58f, 17080),
                ChartPointUiModel("Jul", 0.66f, 19400),
                ChartPointUiModel("Aug", 0.62f, 18240)
            )
        }
    }

    private fun placeholderCategories(totalExpense: Int): List<CategoryBreakdownUiModel> {
        return listOf(
            CategoryBreakdownUiModel("Food", (totalExpense * 0.29f).toInt(), 0.29f),
            CategoryBreakdownUiModel("Shopping", (totalExpense * 0.24f).toInt(), 0.24f),
            CategoryBreakdownUiModel("Bills", (totalExpense * 0.19f).toInt(), 0.19f),
            CategoryBreakdownUiModel("Transport", (totalExpense * 0.16f).toInt(), 0.16f),
            CategoryBreakdownUiModel("Others", (totalExpense * 0.12f).toInt(), 0.12f)
        )
    }

    private fun syncBudgetsWithCategories(
        budgets: List<BudgetUiModel>,
        categories: List<CategoryBreakdownUiModel>
    ): List<BudgetUiModel> {
        return budgets.map { budget ->
            budget.copy(
                spent = categories.firstOrNull {
                    it.title.equals(budget.category, ignoreCase = true)
                }?.amount ?: 0
            )
        }
    }

    private fun mapLatestTransaction(transaction: TrackedTransaction?): RecentTransactionUiModel? {
        return transaction?.let(::mapRecentTransaction)
    }

    private fun mapRecentTransaction(transaction: TrackedTransaction): RecentTransactionUiModel {
        val date = Instant.ofEpochMilli(transaction.record.timestampMillis).atZone(zoneId)
        val merchantTitle = transaction.record.merchant?.takeIf { it.isNotBlank() }
        return RecentTransactionUiModel(
            id = "${transaction.sourceType.name}-${transaction.record.timestampMillis}-${transaction.record.amount}",
            title = if (merchantTitle != null) {
                merchantTitle
            } else {
                when (transaction.record.kind) {
                    TransactionKind.INCOME -> "${transaction.record.category} credit"
                    TransactionKind.REFUND -> "${transaction.record.category} refund"
                    else -> "${transaction.record.category} expense"
                }
            },
            subtitle = buildSubtitle(transaction),
            amount = transaction.record.amount,
            dateLabel = date.format(DateTimeFormatter.ofPattern("dd MMM, hh:mm a")),
            sourceLabel = sourceLabel(transaction.sourceType)
        )
    }

    private fun sourceLabel(type: SourceType): String {
        return when (type) {
            SourceType.SMS -> "SMS"
            SourceType.GMAIL -> "Gmail"
            SourceType.OUTLOOK -> "Outlook"
        }
    }

    private fun allTransactions(): List<TrackedTransaction> {
        return dedupeTransactions(latestSmsTransactions + latestEmailTransactions)
    }

    private fun preferredTransactions(transactions: List<TrackedTransaction>): List<TrackedTransaction> {
        return transactions.filter {
            it.record.confidence >= 0.55f &&
                (it.record.kind == TransactionKind.EXPENSE || it.record.kind == TransactionKind.REFUND)
        }
    }

    private fun dedupeTransactions(transactions: List<TrackedTransaction>): List<TrackedTransaction> {
        return transactions
            .sortedByDescending { it.record.confidence }
            .distinctBy { tracked ->
                val minuteBucket = tracked.record.timestampMillis / 60000L
                listOf(
                    tracked.record.amount.toString(),
                    tracked.record.referenceId ?: "",
                    tracked.record.merchant?.lowercase() ?: "",
                    tracked.record.kind.name,
                    minuteBucket.toString()
                ).joinToString("|")
            }
    }

    private fun buildSubtitle(transaction: TrackedTransaction): String {
        val confidence = (transaction.record.confidence * 100).toInt()
        return listOfNotNull(
            sourceLabel(transaction.sourceType),
            transaction.record.category.takeIf { it.isNotBlank() },
            "$confidence% match"
        ).joinToString(" | ")
    }

    private fun trackingProgress(hasConnectedSources: Boolean, option: TrackingStartOption): Float {
        if (!hasConnectedSources) return 0.18f
        return when (option) {
            TrackingStartOption.WEEKLY -> 0.54f
            TrackingStartOption.MONTHLY -> 0.72f
            TrackingStartOption.YEARLY -> 0.92f
        }
    }

    private fun trackingStatus(hasConnectedSources: Boolean, option: TrackingStartOption): String {
        if (!hasConnectedSources) return "Waiting for your first connected source"
        return when (option) {
            TrackingStartOption.WEEKLY -> "Showing weekly expense movement"
            TrackingStartOption.MONTHLY -> "Showing monthly expense movement"
            TrackingStartOption.YEARLY -> "Showing yearly expense movement"
        }
    }

    private fun trackingHint(option: TrackingStartOption): String {
        return when (option) {
            TrackingStartOption.WEEKLY -> "Weekly keeps recent swings easy to spot."
            TrackingStartOption.MONTHLY -> "Monthly smooths spend into week-by-week patterns."
            TrackingStartOption.YEARLY -> "Yearly helps you understand the bigger spending trend."
        }
    }

    private fun firstIncompleteSource(items: List<SourceSetupUiModel>): SourceType? {
        return items.firstOrNull { item -> item.isAvailable && !item.isConnected }?.type
    }

    private fun SmsTransactionRecord.localDate(): LocalDate {
        return Instant.ofEpochMilli(timestampMillis).atZone(zoneId).toLocalDate()
    }

    private data class TrackedTransaction(
        val record: SmsTransactionRecord,
        val sourceType: SourceType
    )

    private data class AnalyticsSnapshot(
        val totalExpense: Int,
        val suggestedBudget: Int,
        val chartPoints: List<ChartPointUiModel>,
        val defaultPointIndex: Int,
        val categories: List<CategoryBreakdownUiModel>
    )
}
