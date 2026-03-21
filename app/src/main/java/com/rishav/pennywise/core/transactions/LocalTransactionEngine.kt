package com.rishav.pennywise.core.transactions

enum class TransactionKind {
    EXPENSE,
    INCOME,
    REFUND,
    BILL_DUE,
    FAILED
}

data class ParsedTransaction(
    val amount: Int,
    val timestampMillis: Long,
    val category: String,
    val merchant: String?,
    val kind: TransactionKind,
    val confidence: Float,
    val referenceId: String?
)

object LocalTransactionEngine {

    private val expenseRegex = Regex(
        pattern = "\\b(debited|spent|purchase|txn|transaction|upi|withdrawn|paid|payment|sent|charged|order|receipt|invoice|transferred|dr\\b|autopay)\\b",
        option = RegexOption.IGNORE_CASE
    )
    private val incomeRegex = Regex(
        pattern = "\\b(credited|received|salary|cashback|refund initiated|refunded|deposit|reversal|cr\\b|interest|bonus)\\b",
        option = RegexOption.IGNORE_CASE
    )
    private val billDueRegex = Regex(
        pattern = "\\b(due|minimum due|statement|bill due|outstanding)\\b",
        option = RegexOption.IGNORE_CASE
    )
    private val failureRegex = Regex(
        pattern = "\\b(failed|declined|unsuccessful|reversed|could not be processed)\\b",
        option = RegexOption.IGNORE_CASE
    )
    private val amountPatterns = listOf(
        Regex("(?:Rs\\.?|INR|₹)\\s*([0-9,]+(?:\\.\\d{1,2})?)", RegexOption.IGNORE_CASE),
        Regex("amt\\s*(?:is|of)?\\s*(?:Rs\\.?|INR|₹)?\\s*([0-9,]+(?:\\.\\d{1,2})?)", RegexOption.IGNORE_CASE),
        Regex("([0-9,]+(?:\\.\\d{1,2})?)\\s*(?:Rs\\.?|INR|₹)", RegexOption.IGNORE_CASE)
    )
    private val refRegex = Regex(
        pattern = "\\b(?:utr|ref|reference|txn id|transaction id|upi ref|rrn)[:#\\s-]*([A-Z0-9]{6,})\\b",
        option = RegexOption.IGNORE_CASE
    )
    private val merchantPatterns = listOf(
        Regex("\\b(?:to|at|on|via|merchant|paid to|spent at|purchase at)\\s+([A-Za-z0-9&._@/-]{3,48})", RegexOption.IGNORE_CASE),
        Regex("\\b(?:from)\\s+([A-Za-z0-9&._@/-]{3,48})", RegexOption.IGNORE_CASE),
        Regex("\\b(?:vpa|upi)[:/\\s-]*([A-Za-z0-9&._@/-]{3,48})", RegexOption.IGNORE_CASE)
    )
    private val promotionalRegex = Regex(
        pattern = "\\b(offer|sale|discount|voucher|coupon|shop now|apply now|limited period|reward points|cash on delivery)\\b",
        option = RegexOption.IGNORE_CASE
    )
    private val transactionAnchorRegex = Regex(
        pattern = "\\b(debited|credited|spent|paid|received|withdrawn|sent|upi|txn|transaction|payment|purchase|refund|cashback|bill due|statement)\\b",
        option = RegexOption.IGNORE_CASE
    )
    private val accountInfoOnlyRegex = Regex(
        pattern = "\\b(balance|available bal|avail bal|avl bal|a/c balance|account summary)\\b",
        option = RegexOption.IGNORE_CASE
    )

    fun parse(content: String, timestampMillis: Long, sender: String? = null): ParsedTransaction? {
        val normalized = content.replace("\n", " ").replace(Regex("\\s+"), " ").trim()
        if (normalized.isBlank()) return null
        if (looksLikeOtp(normalized)) return null
        if (promotionalRegex.containsMatchIn(normalized) && !transactionAnchorRegex.containsMatchIn(normalized)) return null

        val amount = extractAmount(normalized) ?: return null
        if (accountInfoOnlyRegex.containsMatchIn(normalized) && !expenseRegex.containsMatchIn(normalized) && !incomeRegex.containsMatchIn(normalized)) {
            return null
        }

        val kind = when {
            failureRegex.containsMatchIn(normalized) -> TransactionKind.FAILED
            incomeRegex.containsMatchIn(normalized) && normalized.contains("refund", ignoreCase = true) -> TransactionKind.REFUND
            incomeRegex.containsMatchIn(normalized) -> TransactionKind.INCOME
            billDueRegex.containsMatchIn(normalized) && !expenseRegex.containsMatchIn(normalized) -> TransactionKind.BILL_DUE
            expenseRegex.containsMatchIn(normalized) -> TransactionKind.EXPENSE
            else -> return null
        }

        val merchant = extractMerchant(normalized, sender)
        val category = detectCategory(normalized, merchant)
        val referenceId = refRegex.find(normalized)?.groupValues?.getOrNull(1)
        val confidence = computeConfidence(normalized, merchant, referenceId, kind, sender)

        return ParsedTransaction(
            amount = amount,
            timestampMillis = timestampMillis,
            category = category,
            merchant = merchant,
            kind = kind,
            confidence = confidence.coerceIn(0.15f, 0.98f),
            referenceId = referenceId
        )
    }

    private fun extractAmount(content: String): Int? {
        return amountPatterns.asSequence()
            .mapNotNull { pattern ->
                pattern.find(content)
                    ?.groupValues
                    ?.drop(1)
                    ?.firstOrNull { it.isNotBlank() }
                    ?.replace(",", "")
                    ?.substringBefore(".")
                    ?.toIntOrNull()
            }
            .firstOrNull { it > 0 }
    }

    private fun looksLikeOtp(content: String): Boolean {
        return content.contains("otp", ignoreCase = true) &&
            !content.contains("debited", ignoreCase = true) &&
            !content.contains("credited", ignoreCase = true) &&
            !content.contains("paid", ignoreCase = true)
    }

    private fun computeConfidence(
        content: String,
        merchant: String?,
        referenceId: String?,
        kind: TransactionKind,
        sender: String?
    ): Float {
        var score = 0.32f
        if (amountPatterns.any { it.containsMatchIn(content) }) score += 0.2f
        if (referenceId != null) score += 0.18f
        if (!merchant.isNullOrBlank()) score += 0.14f
        if (kind == TransactionKind.EXPENSE || kind == TransactionKind.INCOME || kind == TransactionKind.REFUND) score += 0.12f
        if (content.contains("upi", ignoreCase = true) || content.contains("card", ignoreCase = true) || content.contains("bank", ignoreCase = true)) {
            score += 0.08f
        }
        if (!sender.isNullOrBlank() && knownBankSenders.any { sender.contains(it, ignoreCase = true) }) {
            score += 0.08f
        }
        if (content.contains("avl bal", ignoreCase = true) || content.contains("a/c", ignoreCase = true)) {
            score += 0.04f
        }
        if (promotionalRegex.containsMatchIn(content) && !transactionAnchorRegex.containsMatchIn(content)) {
            score -= 0.2f
        }
        return score
    }

    private fun extractMerchant(content: String, sender: String?): String? {
        knownMerchants.firstOrNull { content.contains(it, ignoreCase = true) }?.let { return it }
        knownMerchants.firstOrNull { merchant ->
            !sender.isNullOrBlank() && sender.contains(merchant, ignoreCase = true)
        }?.let { return it }

        for (pattern in merchantPatterns) {
            val candidate = pattern.find(content)?.groupValues?.getOrNull(1)
                ?.substringBefore("/")
                ?.substringBefore("@")
                ?.substringBefore(" on ")
                ?.substringBefore(" using ")
                ?.substringBefore(" via ")
                ?.substringBefore(" by ")
                ?.substringBefore(" txn")
                ?.trim(' ', '.', ',', '-', ':')
                ?.takeIf { it.length >= 3 }
            if (!candidate.isNullOrBlank() && !candidate.startsWith("a/c", ignoreCase = true)) {
                val cleaned = cleanMerchant(candidate)
                if (!cleaned.isNullOrBlank()) {
                    return cleaned
                }
            }
        }
        return null
    }

    private fun cleanMerchant(candidate: String): String? {
        val cleaned = candidate
            .replace(Regex("\\b(?:upi|vpa|id|no|number|account|bank|ref|utr|txn|transaction|payment|paid)\\b", RegexOption.IGNORE_CASE), "")
            .replace(Regex("[^A-Za-z0-9&. -]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (cleaned.length < 3) return null
        if (cleaned.all { it.isDigit() }) return null
        if (knownNoiseWords.any { cleaned.equals(it, ignoreCase = true) }) return null
        return knownMerchants.firstOrNull { cleaned.contains(it, ignoreCase = true) } ?: cleaned
    }

    private fun detectCategory(content: String, merchant: String?): String {
        val text = buildString {
            append(content.lowercase())
            if (!merchant.isNullOrBlank()) append(" ").append(merchant.lowercase())
        }
        return when {
            listOf("swiggy", "zomato", "restaurant", "food", "cafe", "pizza", "burger", "starbucks", "domino", "mcdonald").any(text::contains) -> "Food"
            listOf("uber", "ola", "metro", "irctc", "fuel", "petrol", "diesel", "transport", "rapido", "bus", "cab").any(text::contains) -> "Transport"
            listOf("amazon", "flipkart", "myntra", "shopping", "store", "mart", "ajio", "meesho", "nykaa", "shop").any(text::contains) -> "Shopping"
            listOf("electricity", "water", "gas", "broadband", "recharge", "bill", "invoice", "airtel", "jio", "bsnl", "postpaid", "prepaid").any(text::contains) -> "Bills"
            listOf("medical", "pharmacy", "hospital", "apollo", "medplus", "doctor").any(text::contains) -> "Health"
            listOf("salary", "cashback", "refund", "credited", "interest", "bonus").any(text::contains) -> "Income"
            listOf("atm", "cash withdrawal", "withdrawn").any(text::contains) -> "Cash"
            else -> "Others"
        }
    }

    private val knownMerchants = listOf(
        "Amazon", "Flipkart", "Myntra", "Swiggy", "Zomato", "Uber", "Ola", "Paytm", "PhonePe", "Google Pay",
        "Airtel", "Jio", "IRCTC", "BigBasket", "Blinkit", "Zepto", "Starbucks", "DMart", "Reliance Fresh",
        "Nykaa", "Ajio", "Meesho", "Rapido", "BookMyShow", "Apollo", "MedPlus", "Dominos", "McDonalds"
    )
    private val knownBankSenders = listOf(
        "HDFCBK", "ICICIB", "SBIINB", "AXISBK", "KOTAKB", "IDFCFB", "YESBNK", "PNBSMS",
        "CANBNK", "UNIONB", "PAYTMB", "PHONEPE", "GPAY", "UPI"
    )
    private val knownNoiseWords = listOf(
        "upi", "payment", "txn", "transaction", "account", "bank", "vpa", "info", "merchant"
    )
}
