package com.rishav.pennywise.core.ui

import android.util.TypedValue
import androidx.annotation.DimenRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

@Composable
fun textUnitResource(@DimenRes id: Int): TextUnit {
    val context = LocalContext.current
    val density = LocalDensity.current
    return remember(id, context, density) {
        val value = TypedValue()
        context.resources.getValue(id, value, true)
        val pixels = when (value.type) {
            TypedValue.TYPE_DIMENSION -> {
                TypedValue.complexToDimension(value.data, context.resources.displayMetrics)
            }
            else -> value.float
        }
        (pixels / density.density / density.fontScale).sp
    }
}
