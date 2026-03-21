package com.rishav.pennywise

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.rishav.pennywise.core.auth.AuthRedirectRelay
import com.rishav.pennywise.core.auth.EmailAuthManager
import com.rishav.pennywise.ui.theme.PennyWiseTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleAuthIntent(intent)
        enableEdgeToEdge()
        setContent {
            PennyWiseTheme {
                PennyWiseApp(modifier = Modifier.fillMaxSize())
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAuthIntent(intent)
    }

    private fun handleAuthIntent(intent: Intent?) {
        val safeIntent = intent ?: return
        if (EmailAuthManager().isAuthRedirect(safeIntent)) {
            AuthRedirectRelay.emit(safeIntent)
        }
    }
}
