package com.rishav.pennywise.core.ai

import com.rishav.pennywise.core.sms.SmsTransactionRecord
import com.rishav.pennywise.feature.dashboard.presentation.AiInsightUiModel
import com.rishav.pennywise.feature.dashboard.presentation.BudgetUiModel
import com.rishav.pennywise.feature.dashboard.presentation.CategoryBreakdownUiModel

data class LocalAiSupport(
    val isAvailable: Boolean,
    val engineName: String,
    val summary: String
)

interface LocalAiAssistant {
    fun supportStatus(): LocalAiSupport

    fun generateInsights(
        transactions: List<SmsTransactionRecord>,
        categories: List<CategoryBreakdownUiModel>,
        budgets: List<BudgetUiModel>
    ): List<AiInsightUiModel>
}
