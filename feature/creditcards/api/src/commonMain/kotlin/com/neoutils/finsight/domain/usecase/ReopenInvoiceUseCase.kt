package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import com.neoutils.finsight.domain.error.InvoiceException
import com.neoutils.finsight.domain.model.Invoice

/**
 * Puts a closed invoice back on the air, and demotes the successor that opened in its
 * place back to future.
 *
 * Only the latest closed invoice reopens: the successor has to be the card's current
 * `OPEN` one, or reopening would leave two open invoices on the same card. `isReopenable`
 * (`core/model`) is the same rule the screens read to not offer the button, so a screen
 * and this operation never disagree about what is reopenable.
 */
interface ReopenInvoiceUseCase {

    /**
     * The invoice is resolved **when the operation runs**, so the successor is the one
     * standing at that moment; an identity that matches nothing is refused with
     * `InvoiceError.NotFound` and nothing is reopened.
     */
    suspend operator fun invoke(invoiceId: Long): Either<InvoiceException, Invoice>

    /**
     * The convenience for a caller that already holds the invoice. It extracts the
     * identity and delegates — not another rule, so not another implementation.
     */
    suspend operator fun invoke(invoice: Invoice): Either<InvoiceException, Invoice> =
        invoke(invoice.id)
}
