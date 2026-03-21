package com.rishav.pennywise

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import com.rishav.pennywise.feature.onboarding.presentation.OnboardingRoute
import com.rishav.pennywise.ui.theme.PennyWiseTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PennyWiseTheme {
                OnboardingRoute(modifier = Modifier.fillMaxSize())
            }
        }
    }
}
