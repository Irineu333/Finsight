package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import com.neoutils.finsight.domain.error.InvoiceException
import com.neoutils.finsight.domain.model.Invoice
import kotlinx.datetime.LocalDate

/**
 * Closes a cycle: the invoice stops taking spending, and the card's next one opens.
 *
 * Closing settles nothing. Only an invoice that owes zero is marked paid by closing —
 * the ledger knows nothing about status, so an invoice with a balance keeps its
 * `LIABILITY` legs standing until a payment is actually written, and is paid
 * explicitly through [PayInvoicePaymentUseCase].
 *
 * A retroactive invoice belongs to a past cycle, so closing it opens no successor: the
 * current one is already open, and a second `OPEN` invoice on one card is an invariant
 * the whole invoice lookup assumes.
 */
interface CloseInvoiceUseCase {

    /**
     * The invoice is resolved **when the operation runs**; an identity that matches
     * nothing is refused with `InvoiceError.NotFound` and nothing is closed.
     *
     * @param closedAt must fall in the invoice's closing month — a cycle cannot be
     * closed on a date outside itself.
     */
    suspend operator fun invoke(
        invoiceId: Long,
        closedAt: LocalDate,
    ): Either<InvoiceException, Invoice>

    /**
     * The convenience for a caller that already holds the invoice. It extracts the
     * identity and delegates — not another rule, so not another implementation.
     */
    suspend operator fun invoke(
        invoice: Invoice,
        closedAt: LocalDate,
    ): Either<InvoiceException, Invoice> = invoke(invoice.id, closedAt)
}
