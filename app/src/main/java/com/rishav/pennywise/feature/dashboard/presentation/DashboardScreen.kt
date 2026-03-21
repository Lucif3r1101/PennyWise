package com.rishav.pennywise.feature.dashboard.presentation

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rishav.pennywise.R
import com.rishav.pennywise.core.sms.SmsTransactionReader
import com.rishav.pennywise.core.ui.textUnitResource
import kotlin.math.abs

@Composable
fun DashboardRoute(
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val smsReader = SmsTransactionReader()

    val smsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.onSmsPermissionStateChanged(granted)
        if (granted) {
            val wasAllowAllFlow = uiState.setupSheetMode == SetupSheetMode.ALL
            viewModel.onSmsSyncStarted()
            runCatching { smsReader.readSummary(context) }
                .onSuccess(viewModel::onSmsSyncCompleted)
                .onFailure { viewModel.onSmsSyncFailed(it.message ?: "Unknown SMS sync error.") }
            if (wasAllowAllFlow) {
                viewModel.onConfiguredSourcesAcknowledged()
            } else {
                viewModel.onSetupPrimaryHandledForCurrentSelection()
            }
        }
    }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED
        viewModel.onSmsPermissionStateChanged(granted)
    }

    DashboardScreen(
        uiState = uiState,
        modifier = modifier,
        onTabSelected = viewModel::onTabSelected,
        onTrackingStartOptionSelected = viewModel::onTrackingStartOptionSelected,
        onChartPointSelected = viewModel::onChartPointSelected,
        onAllowAllClick = viewModel::onAllowAllClick,
        onSourceSelected = { sourceType ->
            if (sourceType == SourceType.SMS) {
                if (!uiState.smsPermissionGranted) {
                    smsPermissionLauncher.launch(Manifest.permission.READ_SMS)
                } else {
                    viewModel.onSourceSelected(sourceType)
                }
            }
        },
        onDismissSetupSheet = viewModel::onDismissSetupSheet,
        onSetupPrimaryClick = {
            when {
                uiState.setupSheetMode == SetupSheetMode.ALL -> {
                    if (!uiState.smsPermissionGranted) {
                        smsPermissionLauncher.launch(Manifest.permission.READ_SMS)
                    } else {
                        viewModel.onConfiguredSourcesAcknowledged()
                    }
                }

                uiState.selectedSourceType == SourceType.SMS -> {
                    if (!uiState.smsPermissionGranted) {
                        smsPermissionLauncher.launch(Manifest.permission.READ_SMS)
                    }
                }

                uiState.selectedSourceType == SourceType.GMAIL || uiState.selectedSourceType == SourceType.OUTLOOK -> Unit
            }
        },
        onGiveLaterClick = viewModel::onGiveLaterClick,
        onRefreshReading = {
            if (uiState.smsPermissionGranted) {
                viewModel.onSmsSyncStarted()
                runCatching { smsReader.readSummary(context) }
                    .onSuccess(viewModel::onSmsSyncCompleted)
                    .onFailure { viewModel.onSmsSyncFailed(it.message ?: "Unknown SMS sync error.") }
            } else {
                smsPermissionLauncher.launch(Manifest.permission.READ_SMS)
            }
        },
        onBudgetDraftCategoryChanged = viewModel::onBudgetDraftCategoryChanged,
        onBudgetDraftAmountChanged = viewModel::onBudgetDraftAmountChanged,
        onCreateBudget = viewModel::onCreateBudget
    )
}

@Composable
fun DashboardScreen(
    uiState: DashboardUiState,
    onTabSelected: (DashboardTab) -> Unit,
    onTrackingStartOptionSelected: (TrackingStartOption) -> Unit,
    onChartPointSelected: (Int) -> Unit,
    onAllowAllClick: () -> Unit,
    onSourceSelected: (SourceType) -> Unit,
    onDismissSetupSheet: () -> Unit,
    onSetupPrimaryClick: () -> Unit,
    onGiveLaterClick: () -> Unit,
    onRefreshReading: () -> Unit,
    onBudgetDraftCategoryChanged: (String) -> Unit,
    onBudgetDraftAmountChanged: (String) -> Unit,
    onCreateBudget: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                tonalElevation = dimensionResource(id = R.dimen.space_xs),
                containerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.navigationBarsPadding()
            ) {
                DashboardTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = uiState.selectedTab == tab,
                        onClick = { onTabSelected(tab) },
                        icon = {
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(
                                        if (uiState.selectedTab == tab) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.background
                                    )
                                    .padding(
                                        horizontal = dimensionResource(id = R.dimen.space_sm),
                                        vertical = dimensionResource(id = R.dimen.space_xs)
                                    )
                            ) {
                                Text(text = tab.iconText, fontWeight = FontWeight.Bold)
                            }
                        },
                        label = { Text(text = tab.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Surface(
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (uiState.selectedTab) {
                DashboardTab.HOME -> HomeTabContent(
                    uiState = uiState,
                    onTrackingStartOptionSelected = onTrackingStartOptionSelected,
                    onChartPointSelected = onChartPointSelected,
                    onAllowAllClick = onAllowAllClick,
                    onSourceSelected = onSourceSelected,
                    onRefreshReading = onRefreshReading
                )

                DashboardTab.AI_INSIGHTS -> AiInsightsTabContent(
                    insights = uiState.aiInsights
                )

                DashboardTab.CATEGORIES -> CategoriesTabContent(
                    uiState = uiState,
                    onBudgetDraftCategoryChanged = onBudgetDraftCategoryChanged,
                    onBudgetDraftAmountChanged = onBudgetDraftAmountChanged,
                    onCreateBudget = onCreateBudget
                )
            }
        }

        if (uiState.setupSheetMode != null) {
            SetupBottomSheet(
                uiState = uiState,
                onDismiss = onDismissSetupSheet,
                onPrimaryClick = onSetupPrimaryClick,
                onSecondaryClick = onGiveLaterClick
            )
        }
    }
}

@Composable
private fun HomeTabContent(
    uiState: DashboardUiState,
    onTrackingStartOptionSelected: (TrackingStartOption) -> Unit,
    onChartPointSelected: (Int) -> Unit,
    onAllowAllClick: () -> Unit,
    onSourceSelected: (SourceType) -> Unit,
    onRefreshReading: () -> Unit
) {
    val progress by animateFloatAsState(targetValue = uiState.readingProgress, label = "reading_progress")

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(id = R.dimen.space_lg))
    ) {
        item {
            HomeHeader(
                totalExpense = uiState.totalExpense,
                budget = uiState.budgetTarget,
                latestTransaction = uiState.latestTransaction,
                onAllowAllClick = onAllowAllClick
            )
        }

        item {
            PermissionBannerCard(
                modifier = Modifier.padding(horizontal = dimensionResource(id = R.dimen.space_screen_horizontal)),
                connectedCount = uiState.connectedCount,
                availableCount = uiState.availableCount,
                onAllowAllClick = onAllowAllClick,
                onRefreshReading = onRefreshReading,
                isRefreshing = uiState.isRefreshing
            )
        }

        item {
            Column(
                modifier = Modifier.padding(horizontal = dimensionResource(id = R.dimen.space_screen_horizontal)),
                verticalArrangement = Arrangement.spacedBy(dimensionResource(id = R.dimen.space_sm))
            ) {
                uiState.sourceItems.forEach { item ->
                    SourceCard(item = item, onClick = { onSourceSelected(item.type) })
                }
            }
        }

        item {
            SpendingOverviewCard(
                modifier = Modifier.padding(horizontal = dimensionResource(id = R.dimen.space_screen_horizontal)),
                selectedOption = uiState.trackingStartOption,
                periodData = uiState.chartPoints,
                selectedChartPointIndex = uiState.selectedChartPointIndex,
                totalExpense = uiState.totalExpense,
                selectedPoint = uiState.selectedChartPoint,
                onTrackingStartOptionSelected = onTrackingStartOptionSelected,
                onChartPointSelected = onChartPointSelected
            )
        }

        item {
            BudgetSummaryCard(
                modifier = Modifier.padding(horizontal = dimensionResource(id = R.dimen.space_screen_horizontal)),
                budgets = uiState.budgets
            )
        }

        item {
            RecentTransactionsCard(
                modifier = Modifier.padding(horizontal = dimensionResource(id = R.dimen.space_screen_horizontal)),
                latestTransaction = uiState.latestTransaction,
                recentTransactions = uiState.recentTransactions
            )
        }

        item {
            ReadingCard(
                modifier = Modifier.padding(horizontal = dimensionResource(id = R.dimen.space_screen_horizontal)),
                progress = progress,
                status = uiState.readingStatus,
                hint = uiState.readingHint,
                lastSyncSummary = uiState.lastSyncSummary,
                canRefresh = uiState.canRefresh,
                isRefreshing = uiState.isRefreshing,
                onRefreshReading = onRefreshReading
            )
        }
    }
}

@Composable
private fun AiInsightsTabContent(
    insights: List<AiInsightUiModel>
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = dimensionResource(id = R.dimen.space_screen_horizontal),
            vertical = dimensionResource(id = R.dimen.space_screen_vertical)
        ),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(id = R.dimen.space_lg))
    ) {
        item {
            SectionHeading(
                eyebrow = stringResource(id = R.string.ai_insights_title),
                title = stringResource(id = R.string.ai_insights_header_title),
                description = stringResource(id = R.string.ai_insights_header_description)
            )
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(dimensionResource(id = R.dimen.space_md))) {
                insights.forEach { insight ->
                    Card(
                        shape = RoundedCornerShape(dimensionResource(id = R.dimen.card_corner_large)),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(dimensionResource(id = R.dimen.space_lg))) {
                            Text(
                                text = insight.title,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                                fontSize = textUnitResource(id = R.dimen.text_heading_small)
                            )
                            Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.space_xs)))
                            Text(
                                text = insight.description,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = textUnitResource(id = R.dimen.text_body)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoriesTabContent(
    uiState: DashboardUiState,
    onBudgetDraftCategoryChanged: (String) -> Unit,
    onBudgetDraftAmountChanged: (String) -> Unit,
    onCreateBudget: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = dimensionResource(id = R.dimen.space_screen_horizontal),
            vertical = dimensionResource(id = R.dimen.space_screen_vertical)
        ),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(id = R.dimen.space_lg))
    ) {
        item {
            SectionHeading(
                eyebrow = stringResource(id = R.string.categories_title),
                title = stringResource(id = R.string.categories_header_title),
                description = stringResource(id = R.string.categories_header_description)
            )
        }

        item {
            CategoryBreakdownCard(categories = uiState.categoryBreakdown)
        }

        item {
            BudgetPlannerCard(
                budgets = uiState.budgets,
                selectedCategory = uiState.budgetDraftCategory,
                amount = uiState.budgetDraftAmount,
                onCategorySelected = onBudgetDraftCategoryChanged,
                onAmountChanged = onBudgetDraftAmountChanged,
                onCreateBudget = onCreateBudget
            )
        }
    }
}

@Composable
private fun HomeHeader(
    totalExpense: Int,
    budget: Int,
    latestTransaction: RecentTransactionUiModel?,
    onAllowAllClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 36.dp, bottomEnd = 36.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.secondary
                    )
                )
            )
            .statusBarsPadding()
            .padding(dimensionResource(id = R.dimen.space_lg))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(id = R.string.app_name),
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = textUnitResource(id = R.dimen.text_heading)
                )
                Text(
                    text = stringResource(id = R.string.home_welcome_subtitle),
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.84f),
                    fontSize = textUnitResource(id = R.dimen.text_caption)
                )
            }
            TextButton(onClick = onAllowAllClick) {
                Text(
                    text = stringResource(id = R.string.home_open_setup),
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.space_lg)))

        Card(
            shape = RoundedCornerShape(dimensionResource(id = R.dimen.card_corner_large)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(dimensionResource(id = R.dimen.space_lg))) {
                Text(
                    text = stringResource(id = R.string.home_total_expense_title),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = textUnitResource(id = R.dimen.text_caption)
                )
                Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.space_xs)))
                Text(
                    text = "Rs ${formatIndianCurrency(totalExpense)}",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = textUnitResource(id = R.dimen.text_title)
                )
                Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.space_sm)))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    MetricLabel(
                        title = stringResource(id = R.string.home_budget_target_title),
                        value = "Rs ${formatIndianCurrency(budget)}"
                    )
                    MetricLabel(
                        title = stringResource(id = R.string.home_latest_transaction_title),
                        value = latestTransaction?.let { "Rs ${formatIndianCurrency(it.amount)}" }
                            ?: stringResource(id = R.string.home_no_transactions_short)
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionBannerCard(
    modifier: Modifier = Modifier,
    connectedCount: Int,
    availableCount: Int,
    onAllowAllClick: () -> Unit,
    onRefreshReading: () -> Unit,
    isRefreshing: Boolean
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(dimensionResource(id = R.dimen.card_corner_large)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(modifier = Modifier.padding(dimensionResource(id = R.dimen.space_lg))) {
            Text(
                text = stringResource(id = R.string.home_setup_eyebrow),
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                fontSize = textUnitResource(id = R.dimen.text_micro)
            )
            Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.space_xs)))
            Text(
                text = stringResource(id = R.string.home_setup_title),
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                fontSize = textUnitResource(id = R.dimen.text_heading_small)
            )
            Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.space_xs)))
            Text(
                text = stringResource(id = R.string.home_setup_description),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = textUnitResource(id = R.dimen.text_body)
            )
            Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.space_md)))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricLabel(
                    title = stringResource(id = R.string.home_reading_sources),
                    value = "$connectedCount/$availableCount"
                )
                MetricLabel(
                    title = stringResource(id = R.string.home_progress_title),
                    value = if (isRefreshing) {
                        stringResource(id = R.string.home_refreshing)
                    } else {
                        stringResource(id = R.string.home_progress_ready)
                    }
                )
            }
            Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.space_md)))
            Row(horizontalArrangement = Arrangement.spacedBy(dimensionResource(id = R.dimen.space_sm))) {
                Button(
                    onClick = onAllowAllClick,
                    shape = RoundedCornerShape(dimensionResource(id = R.dimen.card_corner_medium)),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(text = stringResource(id = R.string.home_allow_all))
                }
                OutlinedButton(onClick = onRefreshReading) {
                    Text(text = stringResource(id = R.string.home_refresh))
                }
            }
        }
    }
}

@Composable
private fun SpendingOverviewCard(
    modifier: Modifier = Modifier,
    selectedOption: TrackingStartOption,
    periodData: List<ChartPointUiModel>,
    selectedChartPointIndex: Int,
    totalExpense: Int,
    selectedPoint: ChartPointUiModel?,
    onTrackingStartOptionSelected: (TrackingStartOption) -> Unit,
    onChartPointSelected: (Int) -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(dimensionResource(id = R.dimen.card_corner_large)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(dimensionResource(id = R.dimen.space_lg))) {
            SectionHeading(
                eyebrow = stringResource(id = R.string.home_chart_eyebrow),
                title = stringResource(id = R.string.home_chart_title),
                description = stringResource(id = R.string.home_chart_description)
            )
            Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.space_md)))
            Row(horizontalArrangement = Arrangement.spacedBy(dimensionResource(id = R.dimen.space_sm))) {
                TrackingOptionChip(
                    text = stringResource(id = R.string.home_range_week),
                    selected = selectedOption == TrackingStartOption.WEEKLY,
                    onClick = { onTrackingStartOptionSelected(TrackingStartOption.WEEKLY) }
                )
                TrackingOptionChip(
                    text = stringResource(id = R.string.home_range_month),
                    selected = selectedOption == TrackingStartOption.MONTHLY,
                    onClick = { onTrackingStartOptionSelected(TrackingStartOption.MONTHLY) }
                )
                TrackingOptionChip(
                    text = stringResource(id = R.string.home_range_year),
                    selected = selectedOption == TrackingStartOption.YEARLY,
                    onClick = { onTrackingStartOptionSelected(TrackingStartOption.YEARLY) }
                )
            }
            Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.space_lg)))
            LineExpenseChart(
                points = periodData,
                selectedIndex = selectedChartPointIndex,
                onPointSelected = onChartPointSelected
            )
            Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.space_md)))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(dimensionResource(id = R.dimen.card_corner_medium))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(dimensionResource(id = R.dimen.space_md)),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = selectedPoint?.label ?: stringResource(id = R.string.home_chart_title),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = textUnitResource(id = R.dimen.text_micro)
                        )
                        Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.space_xs)))
                        Text(
                            text = "Rs ${formatIndianCurrency(selectedPoint?.amount ?: 0)}",
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold,
                            fontSize = textUnitResource(id = R.dimen.text_heading_small)
                        )
                    }
                    Text(
                        text = "Total Rs ${formatIndianCurrency(totalExpense)}",
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Medium,
                        fontSize = textUnitResource(id = R.dimen.text_caption)
                    )
                }
            }
        }
    }
}

@Composable
private fun LineExpenseChart(
    points: List<ChartPointUiModel>,
    selectedIndex: Int,
    onPointSelected: (Int) -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val primaryLineColor = primaryColor.copy(alpha = 0.16f)
    val primaryFillStart = primaryColor.copy(alpha = 0.22f)
    val primaryFillEnd = primaryColor.copy(alpha = 0.02f)
    val selectedMarkerColor = MaterialTheme.colorScheme.secondary
    val unselectedMarkerColor = MaterialTheme.colorScheme.surface

    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .pointerInput(points) {
                    detectTapGestures { offset ->
                        if (points.isEmpty()) return@detectTapGestures
                        val width = size.width.toFloat().coerceAtLeast(1f)
                        val spacing = if (points.size == 1) 0f else width / points.lastIndex.coerceAtLeast(1)
                        val tappedIndex = points.indices.minByOrNull { index ->
                            abs(offset.x - (spacing * index))
                        } ?: 0
                        onPointSelected(tappedIndex)
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                if (points.isEmpty()) return@Canvas

                val chartHeight = size.height - 28.dp.toPx()
                val stepX = if (points.size == 1) 0f else size.width / points.lastIndex.coerceAtLeast(1)
                val plotted = points.mapIndexed { index, point ->
                    Offset(
                        x = stepX * index,
                        y = chartHeight - (point.value.coerceIn(0f, 1f) * (chartHeight - 24.dp.toPx())) - 8.dp.toPx()
                    )
                }

                drawLine(
                    color = primaryLineColor,
                    start = Offset(0f, chartHeight),
                    end = Offset(size.width, chartHeight),
                    strokeWidth = 2.dp.toPx()
                )

                val fillPath = Path().apply {
                    moveTo(plotted.first().x, chartHeight)
                    lineTo(plotted.first().x, plotted.first().y)
                    for (index in 0 until plotted.lastIndex) {
                        val current = plotted[index]
                        val next = plotted[index + 1]
                        val controlX = (current.x + next.x) / 2f
                        cubicTo(controlX, current.y, controlX, next.y, next.x, next.y)
                    }
                    lineTo(plotted.last().x, chartHeight)
                    close()
                }

                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            primaryFillStart,
                            primaryFillEnd
                        )
                    )
                )

                val linePath = Path().apply {
                    moveTo(plotted.first().x, plotted.first().y)
                    for (index in 0 until plotted.lastIndex) {
                        val current = plotted[index]
                        val next = plotted[index + 1]
                        val controlX = (current.x + next.x) / 2f
                        cubicTo(controlX, current.y, controlX, next.y, next.x, next.y)
                    }
                }

                drawPath(
                    path = linePath,
                    color = primaryColor,
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                )

                plotted.forEachIndexed { index, offset ->
                    val selected = index == selectedIndex
                    drawCircle(
                        color = if (selected) selectedMarkerColor else unselectedMarkerColor,
                        radius = if (selected) 8.dp.toPx() else 6.dp.toPx(),
                        center = offset
                    )
                    drawCircle(
                        color = primaryColor,
                        radius = if (selected) 4.dp.toPx() else 3.dp.toPx(),
                        center = offset
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.space_sm)))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            points.forEachIndexed { index, point ->
                Text(
                    text = point.label,
                    color = if (selectedIndex == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (selectedIndex == index) FontWeight.Bold else FontWeight.Medium,
                    fontSize = textUnitResource(id = R.dimen.text_micro),
                    modifier = Modifier.clickable { onPointSelected(index) }
                )
            }
        }
    }
}

@Composable
private fun BudgetSummaryCard(
    modifier: Modifier = Modifier,
    budgets: List<BudgetUiModel>
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(dimensionResource(id = R.dimen.card_corner_large)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(dimensionResource(id = R.dimen.space_lg))) {
            SectionHeading(
                eyebrow = stringResource(id = R.string.home_budget_summary_eyebrow),
                title = stringResource(id = R.string.home_budget_summary_title),
                description = stringResource(id = R.string.home_budget_summary_description)
            )
            Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.space_md)))
            if (budgets.isEmpty()) {
                Text(
                    text = stringResource(id = R.string.home_budget_summary_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = textUnitResource(id = R.dimen.text_body)
                )
            } else {
                budgets.take(3).forEachIndexed { index, budget ->
                    BudgetRow(budget = budget)
                    if (index != budgets.take(3).lastIndex) {
                        Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.space_md)))
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentTransactionsCard(
    modifier: Modifier = Modifier,
    latestTransaction: RecentTransactionUiModel?,
    recentTransactions: List<RecentTransactionUiModel>
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(dimensionResource(id = R.dimen.card_corner_large)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(dimensionResource(id = R.dimen.space_lg))) {
            SectionHeading(
                eyebrow = stringResource(id = R.string.home_recent_eyebrow),
                title = stringResource(id = R.string.home_recent_title),
                description = stringResource(id = R.string.home_recent_description)
            )
            Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.space_md)))
            latestTransaction?.let {
                HighlightTransactionCard(transaction = it)
                Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.space_md)))
            }
            if (recentTransactions.isEmpty()) {
                Text(
                    text = stringResource(id = R.string.home_recent_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = textUnitResource(id = R.dimen.text_body)
                )
            } else {
                recentTransactions.forEachIndexed { index, transaction ->
                    TransactionRow(transaction = transaction)
                    if (index != recentTransactions.lastIndex) {
                        Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.space_sm)))
                    }
                }
            }
        }
    }
}

@Composable
private fun HighlightTransactionCard(transaction: RecentTransactionUiModel) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(dimensionResource(id = R.dimen.card_corner_medium))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(dimensionResource(id = R.dimen.space_md)),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(id = R.string.home_latest_transaction_title),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = textUnitResource(id = R.dimen.text_micro)
                )
                Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.space_xs)))
                Text(
                    text = transaction.title,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold,
                    fontSize = textUnitResource(id = R.dimen.text_heading_small)
                )
                Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.space_xs)))
                Text(
                    text = "${transaction.sourceLabel} | ${transaction.dateLabel}",
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f),
                    fontSize = textUnitResource(id = R.dimen.text_caption)
                )
            }
            Text(
                text = "Rs ${formatIndianCurrency(transaction.amount)}",
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Bold,
                fontSize = textUnitResource(id = R.dimen.text_heading_small)
            )
        }
    }
}

@Composable
private fun TransactionRow(transaction: RecentTransactionUiModel) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = transaction.sourceLabel.take(1),
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.width(dimensionResource(id = R.dimen.space_sm)))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = transaction.title,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                fontSize = textUnitResource(id = R.dimen.text_body)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${transaction.subtitle} | ${transaction.dateLabel}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = textUnitResource(id = R.dimen.text_caption)
            )
        }
        Text(
            text = "Rs ${formatIndianCurrency(transaction.amount)}",
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            fontSize = textUnitResource(id = R.dimen.text_body)
        )
    }
}

@Composable
private fun CategoryBreakdownCard(categories: List<CategoryBreakdownUiModel>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(dimensionResource(id = R.dimen.card_corner_large)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(dimensionResource(id = R.dimen.space_lg))) {
            SectionHeading(
                eyebrow = stringResource(id = R.string.categories_breakdown_eyebrow),
                title = stringResource(id = R.string.categories_breakdown_title),
                description = stringResource(id = R.string.categories_breakdown_description)
            )
            Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.space_md)))
            categories.forEachIndexed { index, item ->
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = item.title,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium,
                            fontSize = textUnitResource(id = R.dimen.text_caption)
                        )
                        Text(
                            text = "Rs ${formatIndianCurrency(item.amount)}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = textUnitResource(id = R.dimen.text_caption)
                        )
                    }
                    Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.space_xs)))
                    LinearProgressIndicator(
                        progress = { item.ratio },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(dimensionResource(id = R.dimen.progress_height))
                            .clip(RoundedCornerShape(dimensionResource(id = R.dimen.progress_corner))),
                        color = if (index % 2 == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                        trackColor = MaterialTheme.colorScheme.primaryContainer
                    )
                }
                if (index != categories.lastIndex) {
                    Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.space_md)))
                }
            }
        }
    }
}

@Composable
private fun BudgetPlannerCard(
    budgets: List<BudgetUiModel>,
    selectedCategory: String,
    amount: String,
    onCategorySelected: (String) -> Unit,
    onAmountChanged: (String) -> Unit,
    onCreateBudget: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(dimensionResource(id = R.dimen.card_corner_large)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(dimensionResource(id = R.dimen.space_lg))) {
            SectionHeading(
                eyebrow = stringResource(id = R.string.categories_budget_eyebrow),
                title = stringResource(id = R.string.categories_budget_title),
                description = stringResource(id = R.string.categories_budget_description)
            )
            Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.space_md)))
            Column(verticalArrangement = Arrangement.spacedBy(dimensionResource(id = R.dimen.space_sm))) {
                listOf(
                    listOf("Food", "Shopping", "Bills"),
                    listOf("Transport", "Others")
                ).forEach { rowItems ->
                    Row(horizontalArrangement = Arrangement.spacedBy(dimensionResource(id = R.dimen.space_sm))) {
                        rowItems.forEach { category ->
                            TrackingOptionChip(
                                text = category,
                                selected = selectedCategory == category,
                                onClick = { onCategorySelected(category) }
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.space_md)))
            OutlinedTextField(
                value = amount,
                onValueChange = onAmountChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = stringResource(id = R.string.categories_budget_amount_label)) },
                prefix = { Text(text = "Rs") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.space_md)))
            Button(
                onClick = onCreateBudget,
                enabled = amount.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(id = R.string.categories_budget_create))
            }
            AnimatedVisibility(visible = budgets.isNotEmpty()) {
                Column(modifier = Modifier.padding(top = dimensionResource(id = R.dimen.space_lg))) {
                    budgets.forEachIndexed { index, budget ->
                        BudgetRow(budget = budget)
                        if (index != budgets.lastIndex) {
                            Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.space_md)))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BudgetRow(budget: BudgetUiModel) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = budget.category,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = textUnitResource(id = R.dimen.text_body)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Rs ${formatIndianCurrency(budget.spent)} spent of Rs ${formatIndianCurrency(budget.limit)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = textUnitResource(id = R.dimen.text_caption)
                )
            }
            Text(
                text = "Rs ${formatIndianCurrency(budget.remaining)} left",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                fontSize = textUnitResource(id = R.dimen.text_caption)
            )
        }
        Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.space_xs)))
        LinearProgressIndicator(
            progress = { budget.progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(dimensionResource(id = R.dimen.progress_height))
                .clip(RoundedCornerShape(dimensionResource(id = R.dimen.progress_corner))),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.primaryContainer
        )
    }
}

@Composable
private fun SourceCard(item: SourceSetupUiModel, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(dimensionResource(id = R.dimen.card_corner_large)),
        colors = CardDefaults.cardColors(
            containerColor = if (item.isConnected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(dimensionResource(id = R.dimen.space_lg))
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(dimensionResource(id = R.dimen.permission_icon_size))
                        .clip(CircleShape)
                        .background(
                            if (item.isConnected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.secondaryContainer
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = item.title.take(1),
                        color = if (item.isConnected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.width(dimensionResource(id = R.dimen.space_md)))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = textUnitResource(id = R.dimen.text_heading_small)
                    )
                    Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.space_xs)))
                    Text(
                        text = item.statusLabel,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = textUnitResource(id = R.dimen.text_micro)
                    )
                }
                ActionPill(
                    text = item.actionLabel,
                    enabled = item.isAvailable || item.isConnected,
                    onClick = onClick
                )
            }
            Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.space_sm)))
            Text(
                text = item.description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = textUnitResource(id = R.dimen.text_body)
            )
        }
    }
}

@Composable
private fun ReadingCard(
    modifier: Modifier = Modifier,
    progress: Float,
    status: String,
    hint: String,
    lastSyncSummary: String,
    canRefresh: Boolean,
    isRefreshing: Boolean,
    onRefreshReading: () -> Unit
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(dimensionResource(id = R.dimen.card_corner_large)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(dimensionResource(id = R.dimen.space_lg))
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(id = R.string.home_progress_title),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = textUnitResource(id = R.dimen.text_heading_small)
                    )
                    Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.space_xs)))
                    Text(
                        text = status,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = textUnitResource(id = R.dimen.text_body)
                    )
                }
                OutlinedButton(onClick = onRefreshReading, enabled = canRefresh && !isRefreshing) {
                    Text(text = stringResource(id = R.string.home_refresh))
                }
            }
            Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.space_md)))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(dimensionResource(id = R.dimen.progress_height))
                    .clip(RoundedCornerShape(dimensionResource(id = R.dimen.progress_corner))),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primaryContainer
            )
            Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.space_sm)))
            Text(
                text = hint,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = textUnitResource(id = R.dimen.text_caption)
            )
            AnimatedVisibility(visible = lastSyncSummary.isNotBlank()) {
                Text(
                    text = lastSyncSummary,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                    fontSize = textUnitResource(id = R.dimen.text_caption),
                    modifier = Modifier.padding(top = dimensionResource(id = R.dimen.space_sm))
                )
            }
            AnimatedVisibility(visible = !canRefresh) {
                Text(
                    text = stringResource(id = R.string.home_no_sources_description),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                    fontSize = textUnitResource(id = R.dimen.text_caption),
                    modifier = Modifier.padding(top = dimensionResource(id = R.dimen.space_sm))
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SetupBottomSheet(
    uiState: DashboardUiState,
    onDismiss: () -> Unit,
    onPrimaryClick: () -> Unit,
    onSecondaryClick: () -> Unit
) {
    val selectedItem = uiState.sourceItems.firstOrNull { it.type == uiState.selectedSourceType }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = dimensionResource(id = R.dimen.space_sm))
                    .width(dimensionResource(id = R.dimen.sheet_handle_width))
                    .height(dimensionResource(id = R.dimen.sheet_handle_height))
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = dimensionResource(id = R.dimen.space_lg))
                .padding(bottom = dimensionResource(id = R.dimen.space_lg))
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "PW",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = textUnitResource(id = R.dimen.text_heading)
                )
            }
            Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.space_md)))
            Text(
                text = if (uiState.setupSheetMode == SetupSheetMode.ALL) {
                    stringResource(id = R.string.home_setup_sheet_all_title)
                } else {
                    stringResource(id = R.string.home_setup_sheet_single_title)
                },
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                fontSize = textUnitResource(id = R.dimen.text_micro)
            )
            Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.space_xs)))
            Text(
                text = stringResource(id = R.string.home_setup_sheet_title),
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                fontSize = textUnitResource(id = R.dimen.text_heading)
            )
            Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.space_sm)))
            Text(
                text = setupHint(selectedItem),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = textUnitResource(id = R.dimen.text_body)
            )
            Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.space_md)))
            uiState.sourceItems.forEach { item ->
                SourceCard(item = item, onClick = {})
                Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.space_sm)))
            }
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = dimensionResource(id = R.dimen.space_sm)),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = stringResource(id = R.string.home_setup_sheet_privacy),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontSize = textUnitResource(id = R.dimen.text_micro),
                    modifier = Modifier.padding(dimensionResource(id = R.dimen.space_sm))
                )
            }
            Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.space_md)))
            Button(
                onClick = onPrimaryClick,
                enabled = uiState.setupSheetMode == SetupSheetMode.ALL || (selectedItem?.isAvailable == true && !selectedItem.isConnected),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(dimensionResource(id = R.dimen.button_height_large))
            ) {
                Text(text = primaryButtonText(selectedItem))
            }
            OutlinedButton(
                onClick = onSecondaryClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = dimensionResource(id = R.dimen.space_sm))
                    .height(dimensionResource(id = R.dimen.button_height))
            ) {
                Text(text = stringResource(id = R.string.home_setup_sheet_secondary))
            }
        }
    }
}

@Composable
private fun SectionHeading(eyebrow: String, title: String, description: String) {
    Text(
        text = eyebrow,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        fontSize = textUnitResource(id = R.dimen.text_micro)
    )
    Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.space_xs)))
    Text(
        text = title,
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.Bold,
        fontSize = textUnitResource(id = R.dimen.text_heading_small)
    )
    Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.space_xs)))
    Text(
        text = description,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = textUnitResource(id = R.dimen.text_body)
    )
}

@Composable
private fun MetricLabel(title: String, value: String) {
    Column {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = textUnitResource(id = R.dimen.text_micro)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
            fontSize = textUnitResource(id = R.dimen.text_caption)
        )
    }
}

@Composable
private fun ActionPill(text: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(dimensionResource(id = R.dimen.card_corner_medium)))
            .background(if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(
                horizontal = dimensionResource(id = R.dimen.space_sm),
                vertical = dimensionResource(id = R.dimen.space_xs)
            )
    ) {
        Text(
            text = text,
            color = if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
            fontSize = textUnitResource(id = R.dimen.text_micro)
        )
    }
}

@Composable
private fun TrackingOptionChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val background = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val content = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val border = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(dimensionResource(id = R.dimen.card_corner_medium)))
            .background(background)
            .border(
                width = dimensionResource(id = R.dimen.stroke_width),
                color = border,
                shape = RoundedCornerShape(dimensionResource(id = R.dimen.card_corner_medium))
            )
            .clickable(onClick = onClick)
            .padding(
                horizontal = dimensionResource(id = R.dimen.space_md),
                vertical = dimensionResource(id = R.dimen.space_sm)
            )
    ) {
        Text(
            text = text,
            color = content,
            fontWeight = FontWeight.SemiBold,
            fontSize = textUnitResource(id = R.dimen.text_caption)
        )
    }
}

@Composable
private fun PlaceholderTab(title: String, description: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(dimensionResource(id = R.dimen.space_screen_horizontal)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                fontSize = textUnitResource(id = R.dimen.text_title)
            )
            Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.space_sm)))
            Text(
                text = description,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                fontSize = textUnitResource(id = R.dimen.text_body)
            )
        }
    }
}

@Composable
private fun primaryButtonText(item: SourceSetupUiModel?): String {
    return when (item?.type) {
        SourceType.SMS -> stringResource(id = R.string.home_setup_sheet_primary_sms)
        SourceType.GMAIL -> stringResource(id = R.string.home_setup_sheet_primary_sms)
        SourceType.OUTLOOK -> stringResource(id = R.string.home_setup_sheet_primary_sms)
        null -> stringResource(id = R.string.home_setup_sheet_primary_all)
    }
}

@Composable
private fun setupHint(item: SourceSetupUiModel?): String {
    return when (item?.type) {
        SourceType.SMS -> "SMS starts live transaction tracking across bank, card and UPI alerts."
        SourceType.GMAIL -> stringResource(id = R.string.home_setup_sheet_description)
        SourceType.OUTLOOK -> stringResource(id = R.string.home_setup_sheet_description)

        null -> stringResource(id = R.string.home_setup_sheet_description)
    }
}

private fun formatIndianCurrency(amount: Int): String {
    val text = amount.toString()
    if (text.length <= 3) return text
    val lastThree = text.takeLast(3)
    val remaining = text.dropLast(3).reversed().chunked(2).joinToString(",").reversed()
    return "$remaining,$lastThree"
}
