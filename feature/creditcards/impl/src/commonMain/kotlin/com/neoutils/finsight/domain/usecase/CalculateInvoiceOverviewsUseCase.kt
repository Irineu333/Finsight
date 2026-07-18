package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.repository.IEntryRepository
import kotlinx.datetime.YearMonth

/**
 * Invoice overviews derived from the ledger (task 4.11): each invoice's
 * expense/advance-payment/adjustment come from its LIABILITY-leg entries
 * ([IEntryRepository.invoiceFlows]) and the owed total from [IEntryRepository.invoiceOwed],
 * replacing the leg-based sums by `Transaction.Type`/`Target`.
 */
class CalculateInvoiceOverviewsUseCase(
    private val entryRepository: IEntryRepository,
) {

    suspend operator fun invoke(
        invoices: List<Invoice>,
        forYearMonth: YearMonth,
    ): InvoiceOverviewStats {
        val invoiceOverviews = invoices
            .filter { it.closingMonth == forYearMonth }
            .map { invoice ->
                val flows = entryRepository.invoiceFlows(invoice.id)
                InvoiceOverviewResult(
                    invoiceId = invoice.id,
                    creditCardName = invoice.creditCard.name,
                    invoiceStatus = invoice.status,
                    expense = flows.expense,
                    advancePayment = flows.advancePayment,
                    adjustment = flows.adjustment,
                    total = entryRepository.invoiceOwed(invoice.id),
                )
            }

        val creditCardOverview = CreditCardOverviewResult(
            expense = invoiceOverviews.sumOf { it.expense },
            advancePayment = invoiceOverviews.sumOf { it.advancePayment },
            adjustment = invoiceOverviews.sumOf { it.adjustment },
            total = invoiceOverviews.sumOf { it.total }
        )

        return InvoiceOverviewStats(
            invoiceOverviews = invoiceOverviews,
            creditCardOverview = creditCardOverview
        )
    }

    data class InvoiceOverviewStats(
        val invoiceOverviews: List<InvoiceOverviewResult>,
        val creditCardOverview: CreditCardOverviewResult
    )

    data class InvoiceOverviewResult(
        val invoiceId: Long,
        val creditCardName: String,
        val invoiceStatus: Invoice.Status,
        val expense: Double,
        val advancePayment: Double,
        val adjustment: Double,
        val total: Double
    )

    data class CreditCardOverviewResult(
        val expense: Double,
        val advancePayment: Double,
        val adjustment: Double,
        val total: Double
    )
}
