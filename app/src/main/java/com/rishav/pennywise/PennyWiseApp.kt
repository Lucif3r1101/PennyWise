package com.rishav.pennywise

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.rishav.pennywise.feature.dashboard.presentation.DashboardRoute
import com.rishav.pennywise.feature.onboarding.presentation.OnboardingRoute

private enum class RootDestination {
    ONBOARDING,
    DASHBOARD
}

@Composable
fun PennyWiseApp(modifier: Modifier = Modifier) {
    var destination by rememberSaveable { mutableStateOf(RootDestination.ONBOARDING) }

    when (destination) {
        RootDestination.ONBOARDING -> {
            OnboardingRoute(
                modifier = modifier,
                onGetStartedClick = { destination = RootDestination.DASHBOARD }
            )
        }

        RootDestination.DASHBOARD -> {
            DashboardRoute(modifier = modifier)
        }
    }
}
