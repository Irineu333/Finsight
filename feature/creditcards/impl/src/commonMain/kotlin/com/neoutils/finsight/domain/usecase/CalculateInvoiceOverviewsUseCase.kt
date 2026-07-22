package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.repository.IEntryRepository
import kotlinx.datetime.YearMonth

/**
 * Invoice overviews derived from the ledger (task 4.11): each invoice's
 * expense/advance-payment/adjustment come from the entries carrying its dimension
 * ([IEntryRepository.dimensionFlows]) and the owed total from
 * [IEntryRepository.dimensionOwed].
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
                val dimensionId = invoice.dimensionId
                val flows = dimensionId?.let { entryRepository.dimensionFlows(it) }
                InvoiceOverviewResult(
                    invoiceId = invoice.id,
                    creditCardName = invoice.creditCard.name,
                    invoiceStatus = invoice.status,
                    expense = flows?.expense ?: 0.0,
                    advancePayment = flows?.advancePayment ?: 0.0,
                    adjustment = flows?.adjustment ?: 0.0,
                    total = dimensionId?.let { entryRepository.dimensionOwed(it) } ?: 0.0,
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
