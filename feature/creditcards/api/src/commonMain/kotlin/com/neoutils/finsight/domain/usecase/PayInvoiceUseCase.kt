package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import com.neoutils.finsight.domain.error.InvoiceException
import com.neoutils.finsight.domain.model.Invoice
import kotlinx.datetime.LocalDate

/**
 * Marks a closed invoice paid on a date.
 *
 * ⚠️ **It records the status and nothing else.** No money leaves any account here, so a
 * caller that means "the user paid this bill" wants [PayInvoicePaymentUseCase], which
 * writes the payment and then comes through here. This one exists for the two moments
 * where there is nothing to write: closing an invoice that owes zero, and the step that
 * follows a payment already written.
 */
interface PayInvoiceUseCase {

    /**
     * The canonical form, and the one that carries the implementation.
     *
     * The invoice is resolved **when the operation runs**, so the payability and date
     * guards read the invoice as it stands; an identity that matches nothing is refused
     * with `InvoiceError.NotFound` and no status changes.
     */
    suspend operator fun invoke(
        invoiceId: Long,
        paidAt: LocalDate,
    ): Either<InvoiceException, Invoice>

    /**
     * The convenience for a caller that already holds the invoice. It extracts the
     * identity and delegates — not another rule, so not another implementation.
     */
    suspend operator fun invoke(
        invoice: Invoice,
        paidAt: LocalDate,
    ): Either<InvoiceException, Invoice> = invoke(invoice.id, paidAt)
}
