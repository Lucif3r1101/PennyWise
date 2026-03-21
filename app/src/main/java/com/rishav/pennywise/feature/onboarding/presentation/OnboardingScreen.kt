package com.rishav.pennywise.feature.onboarding.presentation

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rishav.pennywise.R
import com.rishav.pennywise.core.ui.textUnitResource
import com.rishav.pennywise.ui.theme.PennyWiseTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun OnboardingRoute(
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    OnboardingScreen(
        uiState = uiState,
        modifier = modifier,
        onGetStartedClick = {}
    )
}

@Composable
fun OnboardingScreen(
    uiState: OnboardingUiState,
    onGetStartedClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pagerState = rememberPagerState(pageCount = { uiState.pages.size })
    val coroutineScope = rememberCoroutineScope()
    val settledPage = pagerState.settledPage
    val isLastPage = settledPage == uiState.pages.lastIndex

    LaunchedEffect(uiState.pages.size, settledPage) {
        if (uiState.pages.isEmpty() || isLastPage) return@LaunchedEffect
        delay(3500)
        if (!pagerState.isScrollInProgress) {
            pagerState.animateScrollToPage(settledPage + 1)
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = dimensionResource(id = R.dimen.space_screen_horizontal),
                    vertical = dimensionResource(id = R.dimen.space_screen_vertical)
                ),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(dimensionResource(id = R.dimen.logo_size))
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(id = R.string.onboarding_logo_mark),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = textUnitResource(id = R.dimen.text_logo)
                    )
                }
                Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.space_sm)))
                Text(
                    text = stringResource(id = R.string.app_name),
                    fontSize = textUnitResource(id = R.dimen.text_title),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.space_xs)))
                Text(
                    text = stringResource(id = R.string.onboarding_tagline),
                    fontSize = textUnitResource(id = R.dimen.text_body),
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f)
                )
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(dimensionResource(id = R.dimen.card_corner_large)),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(
                    defaultElevation = dimensionResource(id = R.dimen.space_xs)
                )
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxWidth()
                ) { page ->
                    val onboardingPage = uiState.pages[page]

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(dimensionResource(id = R.dimen.space_lg)),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(dimensionResource(id = R.dimen.card_corner_medium)))
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .padding(
                                    horizontal = dimensionResource(id = R.dimen.space_lg),
                                    vertical = dimensionResource(id = R.dimen.space_sm)
                                )
                        ) {
                            Text(
                                text = stringResource(id = onboardingPage.badgeRes),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = textUnitResource(id = R.dimen.text_badge)
                            )
                        }

                        Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.space_xl)))

                        Box(
                            modifier = Modifier
                                .size(dimensionResource(id = R.dimen.hero_size))
                                .clip(RoundedCornerShape(dimensionResource(id = R.dimen.hero_corner)))
                                .background(MaterialTheme.colorScheme.secondaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${page + 1}/${uiState.pages.size}",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                fontSize = textUnitResource(id = R.dimen.text_progress)
                            )
                        }

                        Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.space_xl)))

                        Text(
                            text = stringResource(id = onboardingPage.titleRes),
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = textUnitResource(id = R.dimen.text_heading)
                        )

                        Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.space_sm)))

                        Text(
                            text = stringResource(id = onboardingPage.descriptionRes),
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = textUnitResource(id = R.dimen.text_body)
                        )
                    }
                }
            }

            Column {
                Text(
                    text = stringResource(id = R.string.onboarding_permission_note),
                    fontSize = textUnitResource(id = R.dimen.text_caption),
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.space_md)))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    uiState.pages.forEachIndexed { index, _ ->
                        val selected = index == settledPage
                        Box(
                            modifier = Modifier
                                .padding(horizontal = dimensionResource(id = R.dimen.indicator_spacing))
                                .clip(CircleShape)
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant
                                )
                                .clickable {
                                    if (index != settledPage) {
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(index)
                                        }
                                    }
                                }
                                .height(dimensionResource(id = R.dimen.indicator_height))
                                .width(
                                    if (selected) {
                                        dimensionResource(id = R.dimen.indicator_width_selected)
                                    } else {
                                        dimensionResource(id = R.dimen.indicator_width_unselected)
                                    }
                                )
                        )
                    }
                }

                AnimatedVisibility(visible = isLastPage) {
                    Button(
                        onClick = onGetStartedClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = dimensionResource(id = R.dimen.space_md))
                            .height(dimensionResource(id = R.dimen.button_height))
                    ) {
                        Text(text = stringResource(id = R.string.onboarding_get_started))
                    }
                }
            }
        }
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
