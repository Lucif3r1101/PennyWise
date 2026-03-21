package com.rishav.pennywise.feature.onboarding.presentation

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rishav.pennywise.R
import com.rishav.pennywise.core.ui.textUnitResource
import com.rishav.pennywise.ui.theme.PennyWiseTheme
import kotlinx.coroutines.delay

@Composable
fun OnboardingRoute(
    modifier: Modifier = Modifier,
    onGetStartedClick: () -> Unit,
    viewModel: OnboardingViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    OnboardingScreen(
        uiState = uiState,
        modifier = modifier,
        onGetStartedClick = onGetStartedClick
    )
}

@Composable
fun OnboardingScreen(
    uiState: OnboardingUiState,
    onGetStartedClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var currentPage by remember { mutableIntStateOf(0) }
    val isLastPage = currentPage == uiState.pages.lastIndex

    LaunchedEffect(currentPage, uiState.pages.size) {
        if (uiState.pages.isEmpty() || isLastPage) return@LaunchedEffect
        delay(4200)
        currentPage = (currentPage + 1).coerceAtMost(uiState.pages.lastIndex)
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
        ) {
            Text(
                text = stringResource(id = R.string.onboarding_skip),
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(dimensionResource(id = R.dimen.space_md))
                    .clickable(onClick = onGetStartedClick)
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = dimensionResource(id = R.dimen.space_screen_horizontal)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Crossfade(
                    targetState = currentPage,
                    animationSpec = tween(durationMillis = 350),
                    label = "onboarding_page"
                ) { page ->
                    val pageModel = uiState.pages[page]
                    val chips = onboardingChipTexts(page)

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(120.dp)
                                .clip(RoundedCornerShape(32.dp))
                                .background(
                                    brush = Brush.linearGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.primary,
                                            MaterialTheme.colorScheme.secondary
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(id = R.string.onboarding_logo_mark),
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = textUnitResource(id = R.dimen.text_title)
                            )
                        }

                        Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.space_xl)))

                        Text(
                            text = stringResource(id = pageModel.badgeRes),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = textUnitResource(id = R.dimen.text_caption)
                        )

                        Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.space_sm)))

                        Text(
                            text = stringResource(id = pageModel.titleRes),
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.Bold,
                            fontSize = textUnitResource(id = R.dimen.text_title)
                        )

                        Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.space_sm)))

                        Text(
                            text = stringResource(id = pageModel.descriptionRes),
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f),
                            fontSize = textUnitResource(id = R.dimen.text_body)
                        )

                        Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.space_xl)))

                        chips.chunked(2).forEach { rowChips ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(dimensionResource(id = R.dimen.space_sm))
                            ) {
                                rowChips.forEach { chip ->
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(dimensionResource(id = R.dimen.card_corner_medium)))
                                            .background(MaterialTheme.colorScheme.surface)
                                            .padding(
                                                horizontal = dimensionResource(id = R.dimen.space_sm),
                                                vertical = dimensionResource(id = R.dimen.space_sm)
                                            )
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(dimensionResource(id = R.dimen.space_xs))
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.primary)
                                            )
                                            Text(
                                                text = chip,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                fontSize = textUnitResource(id = R.dimen.text_caption),
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                                if (rowChips.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                            Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.space_sm)))
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = dimensionResource(id = R.dimen.space_screen_horizontal))
                    .padding(bottom = dimensionResource(id = R.dimen.space_screen_vertical)),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(dimensionResource(id = R.dimen.space_xs))
                ) {
                    uiState.pages.forEachIndexed { index, _ ->
                        val width by animateFloatAsState(
                            targetValue = if (index == currentPage) 32f else 8f,
                            animationSpec = tween(durationMillis = 250),
                            label = "indicator_width"
                        )
                        Box(
                            modifier = Modifier
                                .width(width.dp)
                                .height(dimensionResource(id = R.dimen.indicator_height))
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (index == currentPage) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant
                                )
                                .clickable { currentPage = index }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.space_lg)))

                Button(
                    onClick = {
                        if (isLastPage) {
                            onGetStartedClick()
                        } else {
                            currentPage = (currentPage + 1).coerceAtMost(uiState.pages.lastIndex)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(dimensionResource(id = R.dimen.button_height)),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(
                        text = if (isLastPage) {
                            stringResource(id = R.string.onboarding_get_started)
                        } else {
                            stringResource(id = R.string.onboarding_continue)
                        },
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun onboardingChipTexts(page: Int): List<String> {
    return when (page) {
        0 -> listOf(
            stringResource(id = R.string.onboarding_tracking_chip_one),
            stringResource(id = R.string.onboarding_tracking_chip_two)
        )
        1 -> listOf(
            stringResource(id = R.string.onboarding_insight_chip_one),
            stringResource(id = R.string.onboarding_insight_chip_two),
            stringResource(id = R.string.onboarding_insight_chip_three),
            stringResource(id = R.string.onboarding_tracking_chip_three)
        )
        else -> listOf(
            stringResource(id = R.string.onboarding_category_chip_one),
            stringResource(id = R.string.onboarding_category_chip_two),
            stringResource(id = R.string.onboarding_category_chip_three),
            stringResource(id = R.string.onboarding_category_chip_four)
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun OnboardingScreenPreview() {
    PennyWiseTheme {
        OnboardingScreen(
            uiState = OnboardingUiState(
                pages = listOf(
                    OnboardingPageUiModel(
                        badgeRes = R.string.onboarding_page_badge_tracking,
                        titleRes = R.string.onboarding_page_title_tracking,
                        descriptionRes = R.string.onboarding_page_description_tracking
                    ),
                    OnboardingPageUiModel(
                        badgeRes = R.string.onboarding_page_badge_insights,
                        titleRes = R.string.onboarding_page_title_insights,
                        descriptionRes = R.string.onboarding_page_description_insights
                    ),
                    OnboardingPageUiModel(
                        badgeRes = R.string.onboarding_page_badge_categories,
                        titleRes = R.string.onboarding_page_title_categories,
                        descriptionRes = R.string.onboarding_page_description_categories
                    )
                )
            ),
            onGetStartedClick = {}
        )
    }
}
