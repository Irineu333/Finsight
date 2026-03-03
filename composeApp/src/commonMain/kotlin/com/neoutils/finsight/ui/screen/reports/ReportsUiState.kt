package com.neoutils.finsight.ui.screen.reports

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Invoice
import kotlinx.datetime.LocalDate

data class ReportsUiState(
    val reportType: ReportType = ReportType.ACCOUNT_BALANCE,
    val accounts: List<Account> = emptyList(),
    val selectedAccount: Account? = null,
    val creditCards: List<CreditCard> = emptyList(),
    val selectedCreditCard: CreditCard? = null,
    val invoices: List<Invoice> = emptyList(),
    val selectedInvoice: Invoice? = null,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val reportRequest: ReportRequest? = null,
) {
    val canGenerate: Boolean
        get() = reportRequest != null
}

enum class ReportType {
    ACCOUNT_BALANCE,
    INVOICE,
    TRANSACTIONS,
}

enum class ReportFormat {
    PDF,
    CSV,
}

sealed interface ReportRequest {
    val format: ReportFormat

    data class AccountBalance(
        val account: Account,
        val startDate: LocalDate,
        val endDate: LocalDate,
        override val format: ReportFormat = ReportFormat.PDF,
    ) : ReportRequest

    data class InvoiceStatement(
        val creditCard: CreditCard,
        val invoice: Invoice,
        override val format: ReportFormat = ReportFormat.PDF,
    ) : ReportRequest

    data class TransactionsByPeriod(
        val startDate: LocalDate,
        val endDate: LocalDate,
        override val format: ReportFormat = ReportFormat.CSV,
    ) : ReportRequest
}
