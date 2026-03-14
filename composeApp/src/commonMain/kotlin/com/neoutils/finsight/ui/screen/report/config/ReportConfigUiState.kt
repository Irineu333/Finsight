package com.neoutils.finsight.ui.screen.report.config

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Invoice
import kotlinx.datetime.DateTimeUnit
import kotlinx.serialization.Serializable
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

data class ReportConfigUiState(
    val accounts: List<Account> = emptyList(),
    val creditCards: List<CreditCard> = emptyList(),
    val invoices: List<Invoice> = emptyList(),
    val selectedTab: PerspectiveTab = PerspectiveTab.ACCOUNT,
    val selectedAccountIds: Set<Long> = emptySet(),
    val selectedCreditCardId: Long? = null,
    val selectedInvoiceId: Long? = null,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val includeSpendingByCategory: Boolean = true,
    val includeTransactionList: Boolean = true,
) {
    companion object {
        fun initial(): ReportConfigUiState {
            val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
            val firstThisMonth = LocalDate(today.year, today.month, 1)
            val lastThisMonth = firstThisMonth
                .plus(1, DateTimeUnit.MONTH)
                .minus(1, DateTimeUnit.DAY)

            return ReportConfigUiState(
                startDate = firstThisMonth,
                endDate = lastThisMonth,
            )
        }
    }

    val isValid: Boolean
        get() = when (selectedTab) {
            PerspectiveTab.ACCOUNT -> selectedAccountIds.isNotEmpty() && startDate <= endDate
            PerspectiveTab.CREDIT_CARD -> selectedCreditCardId != null && selectedInvoiceId != null
        }
}

@Serializable
enum class PerspectiveTab {
    ACCOUNT,
    CREDIT_CARD,
}
