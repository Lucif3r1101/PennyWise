package com.rishav.pennywise.feature.dashboard.presentation

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.core.content.ContextCompat
import com.rishav.pennywise.core.auth.AuthRedirectRelay
import com.rishav.pennywise.core.auth.EmailAuthManager
import com.rishav.pennywise.core.sms.SmsTransactionReader
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rishav.pennywise.R
import com.rishav.pennywise.core.ui.textUnitResource

@Composable
fun DashboardRoute(
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = viewModel()
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val smsReader = SmsTransactionReader()
    val emailAuthManager = remember { EmailAuthManager() }

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
                viewModel.connectConfiguredEmailSources()
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

    LaunchedEffect(Unit) {
        AuthRedirectRelay.events.collect { intent ->
            val provider = intent.getStringExtra(EmailAuthManager.EXTRA_PROVIDER)?.let(SourceType::valueOf)
            emailAuthManager.completeAuthorization(context, intent)
                .onSuccess(viewModel::onEmailAuthCompleted)
                .onFailure {
                    if (provider != null) {
                        viewModel.onEmailAuthFailed(provider, it.message ?: "Could not complete sign-in.")
                    }
                }
        }
    }

    DashboardScreen(
        uiState = uiState,
        modifier = modifier,
        onTabSelected = viewModel::onTabSelected,
        onTrackingStartOptionSelected = viewModel::onTrackingStartOptionSelected,
        onAllowAllClick = viewModel::onAllowAllClick,
        onSourceSelected = viewModel::onSourceSelected,
        onDismissSetupSheet = viewModel::onDismissSetupSheet,
        onSetupPrimaryClick = {
            when {
                uiState.setupSheetMode == SetupSheetMode.ALL -> {
                    if (!uiState.smsPermissionGranted) {
                        smsPermissionLauncher.launch(Manifest.permission.READ_SMS)
                    } else {
                        viewModel.connectConfiguredEmailSources()
                    }
                }

                uiState.selectedSourceType == SourceType.SMS -> {
                    if (!uiState.smsPermissionGranted) {
                        smsPermissionLauncher.launch(Manifest.permission.READ_SMS)
                    }
                }

                uiState.selectedSourceType == SourceType.GMAIL || uiState.selectedSourceType == SourceType.OUTLOOK -> {
                    val source = uiState.selectedSourceType
                    if (source != null) {
                        if (activity != null) {
                            viewModel.onEmailAuthStarted(source)
                            emailAuthManager.startAuthorization(activity, source)
                        } else {
                            viewModel.onEmailAuthFailed(source, "Email sign-in needs an activity context.")
                        }
                    }
                }
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
        }
    )
}

@Composable
fun DashboardScreen(
    uiState: DashboardUiState,
    onTabSelected: (DashboardTab) -> Unit,
    onTrackingStartOptionSelected: (TrackingStartOption) -> Unit,
    onAllowAllClick: () -> Unit,
    onSourceSelected: (SourceType) -> Unit,
    onDismissSetupSheet: () -> Unit,
    onSetupPrimaryClick: () -> Unit,
    onGiveLaterClick: () -> Unit,
    onRefreshReading: () -> Unit,
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
                    onAllowAllClick = onAllowAllClick,
                    onSourceSelected = onSourceSelected,
                    onRefreshReading = onRefreshReading
                )

                DashboardTab.AI_INSIGHTS -> PlaceholderTab(
                    title = stringResource(id = R.string.ai_insights_title),
                    description = stringResource(id = R.string.ai_insights_placeholder)
                )

                DashboardTab.CATEGORIES -> PlaceholderTab(
                    title = stringResource(id = R.string.categories_title),
                    description = stringResource(id = R.string.categories_placeholder)
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
    onAllowAllClick: () -> Unit,
    onSourceSelected: (SourceType) -> Unit,
    onRefreshReading: () -> Unit
) {
    val progress by animateFloatAsState(targetValue = uiState.readingProgress, label = "reading_progress")

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = dimensionResource(id = R.dimen.space_screen_horizontal)),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(id = R.dimen.space_lg))
    ) {
        item {
            Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.space_sm)))
            Text(
                text = stringResource(id = R.string.home_welcome_title),
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                fontSize = textUnitResource(id = R.dimen.text_title)
            )
            Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.space_xs)))
            Text(
                text = stringResource(id = R.string.home_welcome_subtitle),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f),
                fontSize = textUnitResource(id = R.dimen.text_body)
            )
        }

        item {
            HeroCard(
                connectedCount = uiState.connectedCount,
                availableCount = uiState.availableCount,
                progress = progress,
                canRefresh = uiState.canRefresh,
                isRefreshing = uiState.isRefreshing,
                onAllowAllClick = onAllowAllClick,
                onRefreshReading = onRefreshReading
            )
        }

        item {
            SectionHeading(
                eyebrow = stringResource(id = R.string.home_permission_title),
                title = stringResource(id = R.string.home_setup_title),
                description = stringResource(id = R.string.home_permission_description)
            )
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(dimensionResource(id = R.dimen.space_sm))) {
                uiState.sourceItems.forEach { item ->
                    SourceCard(item = item, onClick = { onSourceSelected(item.type) })
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(dimensionResource(id = R.dimen.card_corner_large)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(dimensionResource(id = R.dimen.space_lg))
                ) {
                    SectionHeading(
                        eyebrow = stringResource(id = R.string.home_range_title),
                        title = stringResource(id = R.string.home_range_title),
                        description = stringResource(id = R.string.home_range_description)
                    )
                    Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.space_md)))
                    Row(horizontalArrangement = Arrangement.spacedBy(dimensionResource(id = R.dimen.space_sm))) {
                        TrackingOptionChip(
                            text = stringResource(id = R.string.home_range_now),
                            selected = uiState.trackingStartOption == TrackingStartOption.FROM_NOW,
                            onClick = { onTrackingStartOptionSelected(TrackingStartOption.FROM_NOW) }
                        )
                        TrackingOptionChip(
                            text = stringResource(id = R.string.home_range_year),
                            selected = uiState.trackingStartOption == TrackingStartOption.FROM_THIS_YEAR,
                            onClick = { onTrackingStartOptionSelected(TrackingStartOption.FROM_THIS_YEAR) }
                        )
                    }
                }
            }
        }

        item {
            ReadingCard(
                progress = progress,
                status = uiState.readingStatus,
                hint = uiState.readingHint,
                lastSyncSummary = uiState.lastSyncSummary,
                canRefresh = uiState.canRefresh,
                isRefreshing = uiState.isRefreshing,
                onRefreshReading = onRefreshReading
            )
        }

        item {
            Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.space_lg)))
        }
    }
}

@Composable
private fun HeroCard(
    connectedCount: Int,
    availableCount: Int,
    progress: Float,
    canRefresh: Boolean,
    isRefreshing: Boolean,
    onAllowAllClick: () -> Unit,
    onRefreshReading: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(dimensionResource(id = R.dimen.card_corner_large)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
                .padding(dimensionResource(id = R.dimen.space_lg))
        ) {
            Column {
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
                    fontSize = textUnitResource(id = R.dimen.text_heading)
                )
                Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.space_sm)))
                Text(
                    text = stringResource(id = R.string.home_setup_description),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = textUnitResource(id = R.dimen.text_body)
                )
                Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.space_lg)))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(dimensionResource(id = R.dimen.hero_orb_size))
                            .clip(RoundedCornerShape(dimensionResource(id = R.dimen.hero_corner)))
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$connectedCount/$availableCount",
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = textUnitResource(id = R.dimen.text_heading_small)
                        )
                    }
                    Spacer(modifier = Modifier.width(dimensionResource(id = R.dimen.space_md)))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(id = R.string.home_reading_sources),
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = textUnitResource(id = R.dimen.text_body)
                        )
                        Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.space_xs)))
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(dimensionResource(id = R.dimen.progress_height))
                                .clip(RoundedCornerShape(dimensionResource(id = R.dimen.progress_corner))),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surface
                        )
                        Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.space_xs)))
                        Text(
                            text = "${(progress * 100).toInt()}% ${stringResource(id = R.string.home_progress_complete)}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = textUnitResource(id = R.dimen.text_caption)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.space_lg)))
                Row(horizontalArrangement = Arrangement.spacedBy(dimensionResource(id = R.dimen.space_sm))) {
                    Button(
                        onClick = onAllowAllClick,
                        modifier = Modifier
                            .weight(1f)
                            .height(dimensionResource(id = R.dimen.button_height_large))
                    ) {
                        Text(text = stringResource(id = R.string.home_allow_all))
                    }
                    OutlinedButton(
                        onClick = onRefreshReading,
                        enabled = canRefresh && !isRefreshing,
                        modifier = Modifier
                            .weight(1f)
                            .height(dimensionResource(id = R.dimen.button_height_large))
                    ) {
                        Text(text = stringResource(id = R.string.home_refresh))
                    }
                }
            }
        }
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
                ActionPill(text = item.actionLabel, enabled = item.isAvailable || item.isConnected)
            }
            Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.space_md)))
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
    progress: Float,
    status: String,
    hint: String,
    lastSyncSummary: String,
    canRefresh: Boolean,
    isRefreshing: Boolean,
    onRefreshReading: () -> Unit
) {
    Card(
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
private fun ActionPill(text: String, enabled: Boolean) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(dimensionResource(id = R.dimen.card_corner_medium)))
            .background(if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
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
        SourceType.GMAIL -> stringResource(id = R.string.home_setup_sheet_primary_gmail)
        SourceType.OUTLOOK -> stringResource(id = R.string.home_setup_sheet_primary_outlook)
        null -> stringResource(id = R.string.home_setup_sheet_primary_all)
    }
}

@Composable
private fun setupHint(item: SourceSetupUiModel?): String {
    return when (item?.type) {
        SourceType.SMS -> "SMS starts live transaction tracking across bank, card and UPI alerts."
        SourceType.GMAIL -> if (item.isAvailable) {
            "Gmail local config is present, so the app is ready for a real OAuth connection flow next."
        } else {
            "Add Gmail client values in local.properties before testing the real connection flow."
        }

        SourceType.OUTLOOK -> if (item.isAvailable) {
            "Outlook local config is present, so Microsoft sign-in can be wired next."
        } else {
            "Add Outlook client values in local.properties before testing the real connection flow."
        }

        null -> stringResource(id = R.string.home_setup_sheet_description)
    }
}
