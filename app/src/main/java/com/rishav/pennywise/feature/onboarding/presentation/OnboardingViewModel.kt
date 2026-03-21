package com.rishav.pennywise.feature.onboarding.presentation

import androidx.lifecycle.ViewModel
import com.rishav.pennywise.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class OnboardingViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(
        OnboardingUiState(
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
        )
    )
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()
}
