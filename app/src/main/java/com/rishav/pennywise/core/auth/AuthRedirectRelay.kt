package com.rishav.pennywise.core.auth

import android.content.Intent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object AuthRedirectRelay {
    private val _events = MutableSharedFlow<Intent>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    fun emit(intent: Intent) {
        _events.tryEmit(intent)
    }
}
