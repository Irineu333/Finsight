package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import com.neoutils.finsight.domain.error.InvoiceException
import com.neoutils.finsight.domain.model.Invoice

/**
 * Removes an invoice that was declared but never lived, along with whatever was
 * already booked into it.
 *
 * Only a future or retroactive invoice can go: `Invoice.Status.isDeletable` is the rule,
 * and it is the same one the screens read to not offer the action. An open or closed
 * cycle is history, and history is archived rather than removed.
 */
interface DeleteFutureInvoiceUseCase {

    /**
     * The invoice is resolved **when the operation runs**, so the deletability guard
     * reads the status it has at that moment; an identity that matches nothing is
     * refused with `InvoiceError.NotFound` and nothing is removed.
     *
     * The refusals happen before anything is announced or destroyed, which is what keeps
     * a deletion the domain does not allow from producing a copy of an archive nothing
     * was going to be removed from.
     *
     * @param withoutCopy the person was told no copy could be kept and said to go on.
     *
     * A copy that was owed and could not be taken refuses by throwing
     * `PreventiveCaptureException`, before the first row goes: the invoice and all its
     * transactions are still there, and only the person may say to come back
     * [withoutCopy].
     */
    suspend operator fun invoke(
        invoiceId: Long,
        withoutCopy: Boolean = false,
    ): Either<InvoiceException, Unit>

    /**
     * The convenience for a caller that already holds the invoice. It extracts the
     * identity and delegates — not another rule, so not another implementation.
     */
    suspend operator fun invoke(
        invoice: Invoice,
        withoutCopy: Boolean = false,
    ): Either<InvoiceException, Unit> = invoke(
        invoiceId = invoice.id,
        withoutCopy = withoutCopy,
    )
}
