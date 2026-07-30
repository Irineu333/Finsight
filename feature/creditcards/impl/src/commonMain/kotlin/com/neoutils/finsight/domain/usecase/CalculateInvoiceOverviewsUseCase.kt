package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.ASSUMED_SINGLE_CURRENCY
import com.neoutils.finsight.domain.model.CurrencyBalance
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.sum
import com.neoutils.finsight.domain.repository.DimensionFlows
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
        // Each invoice is single-currency by this facade's guarantee, so its own figures
        // reduce to that currency. The card overview, on the other hand, spans every invoice
        // of the month and therefore every card — so it is summed **per currency** and only
        // then reduced. Summing the already-reduced invoice figures would be the one thing
        // the ledger refuses to do: adding two currencies into one number.
        val perInvoice = invoices
            .filter { it.closingMonth == forYearMonth }
            .map { invoice ->
                val dimensionId = invoice.dimensionId
                InvoiceFigures(
                    invoice = invoice,
                    flows = dimensionId?.let { entryRepository.dimensionFlows(it) } ?: DimensionFlows(),
                    owed = dimensionId?.let { entryRepository.dimensionOwed(it) } ?: CurrencyBalance.zero,
                )
            }

        val invoiceOverviews = perInvoice.map { figures ->
            InvoiceOverviewResult(
                invoiceId = figures.invoice.id,
                creditCardName = figures.invoice.creditCard.name,
                invoiceStatus = figures.invoice.status,
                expense = figures.flows.expense.soleAmount,
                advancePayment = figures.flows.advancePayment.soleAmount,
                adjustment = figures.flows.adjustment.soleAmount,
                total = figures.owed.soleAmount,
            )
        }

        val creditCardOverview = CreditCardOverviewResult(
            expense = perInvoice.map { it.flows.expense }.sum()[ASSUMED_SINGLE_CURRENCY],
            advancePayment = perInvoice.map { it.flows.advancePayment }.sum()[ASSUMED_SINGLE_CURRENCY],
            adjustment = perInvoice.map { it.flows.adjustment }.sum()[ASSUMED_SINGLE_CURRENCY],
            total = perInvoice.map { it.owed }.sum()[ASSUMED_SINGLE_CURRENCY],
        )

        return InvoiceOverviewStats(
            invoiceOverviews = invoiceOverviews,
            creditCardOverview = creditCardOverview
        )
    }

    /** One invoice's ledger figures before reduction — per currency, as the ledger gave them. */
    private data class InvoiceFigures(
        val invoice: Invoice,
        val flows: DimensionFlows,
        val owed: CurrencyBalance,
    )

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
