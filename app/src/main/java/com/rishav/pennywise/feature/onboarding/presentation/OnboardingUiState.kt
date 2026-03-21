package com.rishav.pennywise.feature.onboarding.presentation

import androidx.annotation.StringRes

data class OnboardingPageUiModel(
    @param:StringRes val badgeRes: Int,
    @param:StringRes val titleRes: Int,
    @param:StringRes val descriptionRes: Int
)

data class OnboardingUiState(
    val pages: List<OnboardingPageUiModel> = emptyList()
)
