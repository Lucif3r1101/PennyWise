package com.rishav.pennywise.core.ai

import com.rishav.pennywise.core.sms.SmsTransactionRecord
import com.rishav.pennywise.feature.dashboard.presentation.AiInsightUiModel
import com.rishav.pennywise.feature.dashboard.presentation.BudgetUiModel
import com.rishav.pennywise.feature.dashboard.presentation.CategoryBreakdownUiModel

class LocalAiOrchestrator(
    private val primaryAssistant: LocalAiAssistant = GeminiNanoAiAssistant(),
    private val fallbackAssistant: LocalAiAssistant = RuleBasedLocalAiAssistant()
) {

    fun supportStatus(): LocalAiSupport {
        return if (primaryAssistant.supportStatus().isAvailable) {
            primaryAssistant.supportStatus()
        } else {
            fallbackAssistant.supportStatus()
        }
    }

    fun generateInsights(
        transactions: List<SmsTransactionRecord>,
        categories: List<CategoryBreakdownUiModel>,
        budgets: List<BudgetUiModel>
    ): List<AiInsightUiModel> {
        val primarySupport = primaryAssistant.supportStatus()
        val primaryInsights = if (primarySupport.isAvailable) {
            primaryAssistant.generateInsights(transactions, categories, budgets)
        } else {
            emptyList()
        }

        val resolved = if (primaryInsights.isNotEmpty()) primaryInsights else {
            fallbackAssistant.generateInsights(transactions, categories, budgets)
        }

        val status = if (primarySupport.isAvailable) primarySupport else fallbackAssistant.supportStatus()
        return listOf(
            AiInsightUiModel(
                title = status.engineName,
                description = status.summary
            )
        ) + resolved
    }
}
