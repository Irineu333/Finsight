package com.neoutils.finsight.ui.screen.report.config

import com.neoutils.finsight.domain.model.PerspectiveTab
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

data class ReportConfig(
    val selectedTab: PerspectiveTab = PerspectiveTab.ACCOUNT,
    val selectedAccountIds: Set<Long> = emptySet(),
    val selectedCreditCardId: Long? = null,
    val selectedInvoiceIds: Set<Long> = emptySet(),
    val startDate: LocalDate,
    val endDate: LocalDate,
    val includeSpendingByCategory: Boolean = true,
    val includeIncomeByCategory: Boolean = true,
    val includeTransactionList: Boolean = true,
) {
    companion object {
        fun initial(): ReportConfig {
            val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
            val firstThisMonth = LocalDate(today.year, today.month, 1)
            val lastThisMonth = firstThisMonth
                .plus(1, DateTimeUnit.MONTH)
                .minus(1, DateTimeUnit.DAY)

            return ReportConfig(
                startDate = firstThisMonth,
                endDate = lastThisMonth,
            )
        }
    }

    val isValid: Boolean
        get() = when (selectedTab) {
            PerspectiveTab.ACCOUNT -> selectedAccountIds.isNotEmpty() && startDate <= endDate
            PerspectiveTab.CREDIT_CARD -> selectedCreditCardId != null && selectedInvoiceIds.isNotEmpty()
        }
}
