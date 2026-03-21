package com.rishav.pennywise.core.ai

import com.rishav.pennywise.core.sms.SmsTransactionRecord
import com.rishav.pennywise.core.transactions.TransactionKind
import com.rishav.pennywise.feature.dashboard.presentation.AiInsightUiModel
import com.rishav.pennywise.feature.dashboard.presentation.BudgetUiModel
import com.rishav.pennywise.feature.dashboard.presentation.CategoryBreakdownUiModel
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

class RuleBasedLocalAiAssistant : LocalAiAssistant {

    private val zoneId = ZoneId.systemDefault()

    override fun supportStatus(): LocalAiSupport {
        return LocalAiSupport(
            isAvailable = true,
            engineName = "On-device rules",
            summary = "Local-only heuristic insight engine is active now. Your SMS analysis stays on this device."
        )
    }

    override fun generateInsights(
        transactions: List<SmsTransactionRecord>,
        categories: List<CategoryBreakdownUiModel>,
        budgets: List<BudgetUiModel>
    ): List<AiInsightUiModel> {
        if (transactions.isEmpty()) {
            val support = supportStatus()
            return listOf(
                AiInsightUiModel(
                    title = support.engineName,
                    description = support.summary
                )
            )
        }

        val insights = mutableListOf<AiInsightUiModel>()
        val expenseTransactions = transactions.filter {
            it.kind == TransactionKind.EXPENSE || it.kind == TransactionKind.REFUND
        }
        val today = Instant.now().atZone(zoneId).toLocalDate()
        val recentWindowStart = today.minusDays(6)
        val previousWindowStart = recentWindowStart.minusDays(7)

        val recentSpend = expenseTransactions
            .filter { !Instant.ofEpochMilli(it.timestampMillis).atZone(zoneId).toLocalDate().isBefore(recentWindowStart) }
            .sumOf { it.amount }
        val previousSpend = expenseTransactions
            .filter {
                val date = Instant.ofEpochMilli(it.timestampMillis).atZone(zoneId).toLocalDate()
                !date.isBefore(previousWindowStart) && date.isBefore(recentWindowStart)
            }
            .sumOf { it.amount }

        categories.maxByOrNull { it.amount }?.let { top ->
            insights += AiInsightUiModel(
                title = "${top.title} is leading spend",
                description = "This category is currently contributing the most to your tracked outgoing transactions."
            )
        }

        budgets.firstOrNull { it.progress >= 0.85f }?.let { risk ->
            insights += AiInsightUiModel(
                title = "${risk.category} budget needs attention",
                description = "You have already used ${(risk.progress * 100).toInt()}% of this category budget."
            )
        }

        if (recentSpend > 0 && previousSpend > 0 && recentSpend >= (previousSpend * 1.35f).toInt()) {
            val growth = ((recentSpend - previousSpend).toFloat() / previousSpend.toFloat() * 100f).toInt()
            insights += AiInsightUiModel(
                title = "Spending picked up this week",
                description = "Your last 7 days are about $growth% higher than the 7 days before that."
            )
        }

        expenseTransactions
            .groupBy { it.merchant ?: it.category }
            .filterKeys { !it.isNullOrBlank() }
            .filterValues { items -> items.size >= 2 }
            .maxByOrNull { entry -> entry.value.sumOf { it.amount } }
            ?.let { merchant ->
                insights += AiInsightUiModel(
                    title = "Recurring spend spotted at ${merchant.key}",
                    description = "${merchant.value.size} transactions were grouped here, so this may be becoming a repeat spend pattern."
                )
            }

        expenseTransactions
            .maxByOrNull { it.amount }
            ?.takeIf { it.amount >= 1000 }
            ?.let { largest ->
                val largestDate = Instant.ofEpochMilli(largest.timestampMillis).atZone(zoneId).toLocalDate()
                val daysAgo = ChronoUnit.DAYS.between(largestDate, today).toInt().coerceAtLeast(0)
                val merchantLabel = largest.merchant ?: largest.category
                val recencyLabel = if (daysAgo == 0) {
                    "today"
                } else {
                    "in the last $daysAgo day(s)"
                }
                insights += AiInsightUiModel(
                    title = "Largest recent transaction: Rs ${largest.amount}",
                    description = "$merchantLabel was your largest tracked transaction $recencyLabel."
                )
            }

        return insights.distinctBy { it.title }.take(4)
    }
}
