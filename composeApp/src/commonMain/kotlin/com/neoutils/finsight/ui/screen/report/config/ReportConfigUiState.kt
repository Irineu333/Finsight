package com.neoutils.finsight.ui.screen.report.config

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.CreditCard
import kotlinx.datetime.LocalDate

data class ReportConfigUiState(
    val accounts: List<Account> = emptyList(),
    val creditCards: List<CreditCard> = emptyList(),
    val selectedTab: PerspectiveTab = PerspectiveTab.ACCOUNT,
    val selectedAccountIds: Set<Long> = emptySet(),
    val selectedCreditCardId: Long? = null,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val includeSpendingByCategory: Boolean = true,
    val includeTransactionList: Boolean = true,
) {
    val isValid: Boolean
        get() = when (selectedTab) {
            PerspectiveTab.ACCOUNT -> selectedAccountIds.isNotEmpty() && startDate != null && endDate != null && startDate <= endDate
            PerspectiveTab.CREDIT_CARD -> selectedCreditCardId != null && startDate != null && endDate != null && startDate <= endDate
        }
}

enum class PerspectiveTab {
    ACCOUNT,
    CREDIT_CARD,
}
