package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import com.neoutils.finsight.domain.model.Invoice
import kotlinx.datetime.LocalDate

/**
 * Corrects what an invoice owes on a date, by writing the difference.
 *
 * The adjustment is an **event of its date**, not a target the ledger keeps
 * re-reaching: the operation finds the adjustment already written on that date — the
 * transaction carrying this invoice's dimension against an `EQUITY` (reconciliation)
 * counter-leg — and rewrites it from its own ledger leg, so a re-adjustment can never
 * accumulate onto a stale value (design D17). An adjustment that lands back on the
 * original amount is removed rather than kept as a transaction worth zero.
 *
 * Adjusting to the amount the invoice already owes is refused with
 * `InvoiceNotAdjustedException`: there is nothing to record.
 */
interface AdjustInvoiceUseCase {

    /**
     * The canonical form, and the one that carries the implementation.
     *
     * The invoice is resolved **when the operation runs**, so the difference is
     * measured against what it owes at that moment; an identity that matches nothing is
     * refused with `InvoiceError.NotFound` and nothing is written.
     */
    suspend operator fun invoke(
        invoiceId: Long,
        target: Double,
        adjustmentDate: LocalDate,
    ): Either<Throwable, Unit>

    /**
     * The convenience for a caller that already holds the invoice. It extracts the
     * identity and delegates — not another rule, so not another implementation.
     */
    suspend operator fun invoke(
        invoice: Invoice,
        target: Double,
        adjustmentDate: LocalDate,
    ): Either<Throwable, Unit> = invoke(invoice.id, target, adjustmentDate)
}
