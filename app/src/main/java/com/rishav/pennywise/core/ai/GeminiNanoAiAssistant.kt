package com.rishav.pennywise.core.ai

import com.rishav.pennywise.core.sms.SmsTransactionRecord
import com.rishav.pennywise.feature.dashboard.presentation.AiInsightUiModel
import com.rishav.pennywise.feature.dashboard.presentation.BudgetUiModel
import com.rishav.pennywise.feature.dashboard.presentation.CategoryBreakdownUiModel

class GeminiNanoAiAssistant : LocalAiAssistant {

    override fun supportStatus(): LocalAiSupport {
        return LocalAiSupport(
            isAvailable = false,
            engineName = "Gemini Nano",
            summary = "Planned local AI backend. If we add it later, it will use a fully on-device runtime instead of server inference."
        )
    }

    override fun generateInsights(
        transactions: List<SmsTransactionRecord>,
        categories: List<CategoryBreakdownUiModel>,
        budgets: List<BudgetUiModel>
    ): List<AiInsightUiModel> {
        return emptyList()
    }
}
