package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.MoneyByCurrency
import com.neoutils.finsight.domain.repository.IEntryRepository
import kotlinx.datetime.YearMonth

/**
 * Invoice overviews derived from the ledger: each invoice's
 * expense/advance-payment/adjustment come from the entries carrying its dimension
 * ([IEntryRepository.dimensionFlowsByCurrency]) and the owed total from
 * [IEntryRepository.dimensionOwedByCurrency].
 *
 * The ledger answers per currency; an invoice holds one, by the card facade's guarantee
 * (see [CalculateInvoiceUseCase]), and that is where each map is reduced. The card-wide
 * totals below then add invoices of *possibly different* cards — but every card here
 * belongs to one screen and, until the summary is consolidated, they are added as the
 * plain numbers they already were.
 *
 * **Public contract.** Invoices and a month in, the nested result types below out —
 * every one of them plain data, with the identifier beside the name. Nothing can fail
 * and nothing is presented: no error type, no `UiText`, no string resource. A concrete
 * class rather than an interface, because it depends only on `:core:ledger`.
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
                val flows = dimensionId?.let { entryRepository.dimensionFlowsByCurrency(it) }
                InvoiceOverviewResult(
                    invoiceId = invoice.id,
                    creditCardName = invoice.creditCard.name,
                    invoiceStatus = invoice.status,
                    expense = flows?.expense.only(),
                    advancePayment = flows?.advancePayment.only(),
                    adjustment = flows?.adjustment.only(),
                    total = dimensionId
                        ?.let { entryRepository.dimensionOwedByCurrency(it).singleOrNull()?.value }
                        ?: 0.0,
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

    /** The one term of an invoice's figure — the facade's guarantee, applied. */
    private fun MoneyByCurrency?.only(): Double = this?.singleOrNull()?.value ?: 0.0

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
